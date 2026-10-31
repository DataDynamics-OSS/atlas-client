package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.AtlasClient;
import picocli.CommandLine.Mixin;

/**
 * REST로 Atlas 서버와 통신하는 명령의 공통 부모 클래스입니다.
 * <p>
 * {@link ConnectionOptions}를 mixin으로 포함하므로, 이 클래스를 상속한 명령은 모두 {@code --url}, {@code --user}
 * 같은 접속 옵션을 받습니다.
 */
abstract class ClientCommand extends CliCommand {

    /** 접속 옵션(URL, 인증 정보, 타임아웃 등)입니다. picocli가 명령줄 옵션을 채워 넣습니다. */
    @Mixin
    ConnectionOptions connection;

    /** 처음 필요할 때 만들어 재사용하는 클라이언트입니다. */
    private AtlasClient client;

    /**
     * Atlas 클라이언트를 돌려줍니다. 처음 호출할 때 접속 옵션과 환경 변수로 만들고 이후에는 같은 객체를 씁니다.
     * <p>
     * 지연 생성하므로 클라이언트가 필요 없는 경로(예: 인자 검증에서 실패하는 경우)에서는 설정 파일을 읽거나
     * 접속 정보를 검사하지 않습니다.
     *
     * @return 이 명령 실행 동안 공유하는 {@link AtlasClient}
     * @throws IllegalArgumentException URL이 설정되지 않았거나 설정이 잘못된 경우
     */
    protected AtlasClient client() {
        // 한 번만 만들어 두고 재사용합니다.
        if (client == null) {
            client = connection.newClient(root().env());
        }
        return client;
    }
}
