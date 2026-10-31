package io.datadynamics.atlas.client.cli;

import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 테스트용으로 CLI를 같은 프로세스 안에서 실행합니다. 환경 변수와 표준 입력을 원하는 값으로 지정할 수 있습니다.
 * <p>
 * 실제 프로세스의 환경 변수나 {@code System.exit}을 쓰지 않으므로 테스트끼리 서로 영향을 주지 않습니다.
 */
final class CliRunner {

    /**
     * 실행 결과입니다.
     *
     * @param exitCode 명령의 종료 코드
     * @param out      표준 출력으로 나간 내용
     * @param err      표준 오류로 나간 내용
     */
    record Result(int exitCode, String out, String err) {
    }

    /** CLI에 넘길 환경 변수입니다. 실제 환경 변수는 쓰지 않습니다. */
    private final Map<String, String> env = new HashMap<>();
    /** CLI의 표준 입력으로 줄 내용입니다. 확인 질문에 대한 답을 넣을 때 씁니다. */
    private String stdin = "";

    /**
     * {@code ATLAS_URL}을 지정한 러너를 만듭니다.
     *
     * @param atlasUrl 접속할 Atlas URL(보통 가짜 서버 주소). 빈 문자열이면 URL이 없는 것으로 취급됩니다
     */
    CliRunner(String atlasUrl) {
        env.put("ATLAS_URL", atlasUrl);
        // 개발자 PC의 ~/.atlas/client.properties를 절대 읽지 않도록 존재하지 않는 경로를 지정합니다.
        env.put("ATLAS_CLIENT_CONFIG", "/nonexistent/atlas-client.properties");
    }

    /** 환경 변수를 하나 추가하거나 바꿉니다. 메서드 체이닝을 위해 자신을 돌려줍니다. */
    CliRunner env(String name, String value) {
        env.put(name, value);
        return this;
    }

    /** 다음 실행에서 표준 입력으로 줄 내용을 지정합니다. 메서드 체이닝을 위해 자신을 돌려줍니다. */
    CliRunner stdin(String stdin) {
        this.stdin = stdin;
        return this;
    }

    /**
     * 주어진 인자로 CLI를 실행하고 종료 코드와 출력을 모아 돌려줍니다.
     * {@link AtlasCli#commandLine(AtlasCli)}을 써서 실제 실행과 같은 예외 처리를 거칩니다.
     *
     * @param args 명령줄 인자
     * @return 실행 결과
     */
    Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = AtlasCli.commandLine(new AtlasCli(env,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8))));
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        int exitCode = commandLine.execute(args);
        return new Result(exitCode, out.toString(), err.toString());
    }
}
