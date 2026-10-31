package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.api.AdminApi.AtlasVersion;
import picocli.CommandLine.Command;

/**
 * {@code atlas status} 명령입니다. Atlas 버전과 서버 상태(ACTIVE, PASSIVE 등)를 출력합니다.
 * <p>
 * 서버 상태가 ACTIVE이면 0, 그 밖의 상태(PASSIVE 등)이면 {@link AtlasCli#EXIT_FAILURE}로
 * 종료하므로, 스크립트에서 서버가 요청을 처리할 수 있는지 확인하는 용도로 쓸 수 있습니다.
 */
@Command(name = "status", description = "Shows the Atlas version and server status (ACTIVE, PASSIVE, ...).")
final class StatusCommand extends ClientCommand {

    /**
     * 버전과 상태를 조회해 {@code Atlas <버전> (<상태>)} 형식으로 출력합니다.
     *
     * @return 상태가 {@code ACTIVE}이면 0, 아니면 {@link AtlasCli#EXIT_FAILURE}
     */
    @Override
    public Integer call() {
        AtlasVersion version = client().admin().version();
        String status = client().admin().status();
        out().printf("Atlas %s (%s)%n", version.getVersion(), status);
        return "ACTIVE".equals(status) ? 0 : AtlasCli.EXIT_FAILURE;
    }
}
