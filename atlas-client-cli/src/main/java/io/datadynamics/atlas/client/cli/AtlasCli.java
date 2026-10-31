package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@code atlas} 명령줄 도구의 진입점이자 최상위 명령입니다.
 * <p>
 * 사용 예:
 * <pre>
 * atlas types plan ./types --url http://atlas:21000 -u admin
 * atlas types apply ./types --yes
 * atlas entities import tables.csv --type hive_table
 * atlas lineage impact hive_table raw.sales@prod
 * atlas check --duplicates hive_table
 * atlas events tail --bootstrap-servers kafka:9092
 * </pre>
 * 접속 설정은 명령줄 옵션을 먼저 보고, 없으면 {@code ATLAS_*} 환경 변수, 그래도 없으면
 * {@code ~/.atlas/client.properties} 파일에서 가져옵니다({@link ConnectionOptions} 참고).
 * <p>
 * 하위 명령 없이 실행하면 사용법 오류({@link #EXIT_USAGE})로 끝납니다.
 */
@Command(name = "atlas", mixinStandardHelpOptions = true, versionProvider = AtlasCli.VersionProvider.class,
        description = "Command line tools for Apache Atlas.",
        subcommands = {StatusCommand.class, TypesCommand.class, EntitiesCommand.class, LineageCommand.class,
                CheckCommand.class, EventsCommand.class, HelpCommand.class})
public final class AtlasCli implements Runnable {

    /**
     * 명령은 실행되었지만 문제가 발견된 경우의 종료 코드입니다.
     * plan 오류, 일관성 검사에서 찾은 문제, 확인 질문에 대한 거절 등이 해당합니다.
     * 실행 중 예외가 발생한 경우에도 이 코드로 끝납니다.
     */
    public static final int EXIT_FAILURE = 1;
    /** 명령줄이 잘못된 경우(알 수 없는 옵션, 빠진 인자 등)의 종료 코드입니다. picocli의 기본값과 같습니다. */
    public static final int EXIT_USAGE = 2;

    /** 접속 설정과 디버그 플래그를 읽을 환경 변수입니다. 테스트에서는 원하는 값으로 바꿔 넣습니다. */
    private final Map<String, String> env;
    /** 확인 질문의 답을 읽을 표준 입력입니다. */
    private final BufferedReader in;

    /** picocli가 주입하는 이 명령의 명세입니다. */
    @Spec
    CommandSpec spec;

    /** 실제 프로세스의 환경 변수({@link System#getenv()})와 표준 입력({@link System#in})을 쓰는 인스턴스를 만듭니다. */
    public AtlasCli() {
        this(System.getenv(), System.in);
    }

    /**
     * 환경 변수와 입력 스트림을 직접 지정해 만듭니다. 테스트에서 실제 환경과 분리하기 위해 씁니다.
     * 입력은 UTF-8로 읽습니다.
     */
    AtlasCli(Map<String, String> env, InputStream in) {
        this.env = env;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * 명령줄 도구를 실행하고, 명령이 돌려준 종료 코드로 JVM을 끝냅니다.
     *
     * @param args 명령줄 인자(예: {@code types plan ./types --url http://atlas:21000})
     */
    public static void main(String[] args) {
        System.exit(commandLine(new AtlasCli()).execute(args));
    }

    /**
     * {@link #main(String[])}가 실행하는 것과 같은 설정의 picocli {@link CommandLine}을 만듭니다.
     * <p>
     * 다른 애플리케이션에 도구를 내장할 때 씁니다. 출력은 {@code setOut}/{@code setErr}로 바꿔 끼울 수 있습니다.
     * 열거형 옵션 값은 대소문자를 구분하지 않습니다.
     * <p>
     * 실행 중 예외가 나면 스택 트레이스 대신 {@code Error: <메시지>} 한 줄만 표준 오류에 출력하고
     * {@link #EXIT_FAILURE}를 돌려줍니다. 환경 변수 {@code ATLAS_CLI_DEBUG}가 설정되어 있으면(값은 상관없습니다)
     * 스택 트레이스도 함께 출력합니다.
     *
     * @param cli 실행할 루트 명령 객체
     * @return 설정이 끝난 {@link CommandLine}. {@code execute(args)}로 실행합니다
     */
    public static CommandLine commandLine(AtlasCli cli) {
        CommandLine commandLine = new CommandLine(cli);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setExecutionExceptionHandler((error, cl, parseResult) -> {
            // 사용자가 고칠 수 있는 예상된 오류(Atlas 오류 응답, 잘못된 인자, 상태 오류, 파일 입출력 오류)는
            // 메시지만 보여 줍니다. 그 밖의 예외는 원인을 알 수 있도록 예외 클래스 이름까지 출력합니다.
            PrintWriter err = cl.getErr();
            if (error instanceof AtlasClientException || error instanceof IllegalArgumentException
                    || error instanceof IllegalStateException || error instanceof UncheckedIOException) {
                err.println("Error: " + error.getMessage());
            } else {
                err.println("Error: " + error);
            }
            if (cli.env.containsKey("ATLAS_CLI_DEBUG")) {
                error.printStackTrace(err);
            }
            err.flush();
            return EXIT_FAILURE;
        });
        return commandLine;
    }

    /**
     * 하위 명령 없이 {@code atlas}만 실행한 경우 호출됩니다. "Missing command" 사용법 오류를 던져
     * picocli가 사용법을 출력하고 {@link #EXIT_USAGE}로 끝나게 합니다.
     */
    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing command");
    }

    /** 하위 명령이 접속 설정과 디버그 플래그를 읽을 때 쓰는 환경 변수입니다. */
    Map<String, String> env() {
        return env;
    }

    /**
     * 표준 입력에서 한 줄을 읽습니다.
     *
     * @return 읽은 줄(줄바꿈 문자 제외), 입력이 끝났으면 {@code null}
     * @throws UncheckedIOException 읽기에 실패한 경우
     */
    String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code --version}에 출력할 버전 문자열을 제공합니다.
     * <p>
     * 버전은 jar 매니페스트의 {@code Implementation-Version}에서 읽습니다. IDE에서 클래스 파일로 바로 실행하는
     * 등 매니페스트가 없으면 {@code (development)}로 표시합니다.
     */
    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = AtlasCli.class.getPackage().getImplementationVersion();
            return new String[]{"atlas-client-cli " + (version == null ? "(development)" : version)};
        }
    }
}
