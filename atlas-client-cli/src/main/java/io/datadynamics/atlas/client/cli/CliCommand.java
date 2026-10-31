package io.datadynamics.atlas.client.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * 실제 동작을 수행하는 말단(leaf) 명령의 공통 부모 클래스입니다.
 * <p>
 * 출력({@link #out()}, {@link #err()}), 표준 입력을 통한 확인 질문({@link #confirm(String)}),
 * 그리고 루트 명령({@link AtlasCli})이 가진 환경 변수 등에 접근하는 방법을 제공합니다.
 * {@link Callable#call()}이 돌려주는 정수는 프로세스 종료 코드로 쓰입니다.
 */
abstract class CliCommand implements Callable<Integer> {

    /** picocli가 실행 시점에 주입하는 현재 명령의 명세입니다. 출력 스트림과 루트 명령을 찾는 데 씁니다. */
    @Spec
    CommandSpec spec;

    /**
     * 최상위 {@code atlas} 명령 객체를 돌려줍니다.
     *
     * @return 루트 명령인 {@link AtlasCli} 인스턴스(환경 변수와 표준 입력을 가지고 있습니다)
     */
    protected AtlasCli root() {
        return (AtlasCli) spec.root().userObject();
    }

    /**
     * 일반 출력에 쓸 writer를 돌려줍니다.
     * <p>
     * {@code System.out}을 직접 쓰지 않고 picocli의 writer를 쓰므로, 도구를 내장해 쓰는 쪽이나 테스트에서
     * {@code CommandLine#setOut}으로 출력을 가로챌 수 있습니다.
     *
     * @return 표준 출력(또는 바꿔 끼운 출력)에 연결된 writer
     */
    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    /**
     * 오류·경고 출력에 쓸 writer를 돌려줍니다. {@code CommandLine#setErr}로 바꿔 끼울 수 있습니다.
     *
     * @return 표준 오류(또는 바꿔 끼운 출력)에 연결된 writer
     */
    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    /**
     * 표준 입력으로 사용자에게 확인을 받습니다. 정확히 {@code yes}라고 입력한 경우에만 승인으로 봅니다.
     * <p>
     * 앞뒤 공백은 무시하지만 {@code y}, {@code YES} 같은 다른 답은 모두 거절로 처리합니다.
     * 입력이 끝나 읽을 줄이 없는 경우(예: 파이프로 빈 입력을 준 경우)에도 거절입니다.
     *
     * @param question 질문 문장. 뒤에 {@code " Only 'yes' will be accepted: "}가 붙어 출력됩니다
     * @return 사용자가 {@code yes}를 입력했으면 {@code true}
     */
    protected boolean confirm(String question) {
        PrintWriter out = out();
        out.print(question + " Only 'yes' will be accepted: ");
        out.flush();
        String answer = root().readLine();
        out.println();
        return answer != null && answer.trim().equals("yes");
    }
}
