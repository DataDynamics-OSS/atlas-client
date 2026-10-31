package io.datadynamics.atlas.client.http;

import io.datadynamics.atlas.client.exception.AtlasConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 요청을 보낼 Atlas 서버를 고릅니다.
 * <p>
 * URL이 하나뿐이면 항상 그 서버를 씁니다. 여러 개(Atlas HA 구성)이면 각 서버의
 * {@code /api/atlas/admin/status}를 차례로 확인해 {@code ACTIVE}라고 답한 서버를 고르고 기억해 둡니다.
 * 그 서버로 보낸 요청이 실패하면 기억을 지우고, 다음 요청 때 다시 확인합니다.
 * <p>
 * 스레드 안전합니다. 기억해 둔 서버는 {@code volatile} 필드에 두고, 다시 찾는 작업은 동기화해서 여러 스레드가
 * 동시에 상태 확인 요청을 보내지 않게 합니다.
 */
final class ServerSelector {

    // HA 상태 확인 응답에서 요청을 받을 수 있는 서버를 나타내는 값입니다.
    static final String ACTIVE = "ACTIVE";

    private static final Logger LOG = LoggerFactory.getLogger(ServerSelector.class);

    private final List<URI> servers;
    private final Function<URI, String> statusProbe;
    // 현재 ACTIVE로 알고 있는 서버입니다. null이면 다음 current() 호출에서 다시 찾습니다.
    private volatile URI active;

    /**
     * 선택기를 만듭니다.
     *
     * @param servers     후보 서버 URL 목록(복사해서 보관합니다)
     * @param statusProbe 서버의 상태(예: {@code ACTIVE}, {@code PASSIVE})를 돌려주며, 연결할 수 없으면 예외를 던집니다
     */
    ServerSelector(List<URI> servers, Function<URI, String> statusProbe) {
        this.servers = List.copyOf(servers);
        this.statusProbe = statusProbe;
    }

    // 서버가 둘 이상이면 HA 구성으로 봅니다.
    boolean isHighAvailability() {
        return servers.size() > 1;
    }

    /**
     * 지금 요청을 보낼 서버를 돌려줍니다. HA가 아니면 유일한 서버를, HA이면 기억해 둔 ACTIVE 서버를 돌려주고,
     * 기억해 둔 서버가 없으면 모든 서버를 확인해 찾습니다.
     * <p>
     * 이중 확인 잠금(double-checked locking)을 씁니다. 대부분의 호출은 잠금 없이 volatile 필드만 읽고,
     * 서버를 다시 찾아야 할 때만 잠금을 잡습니다.
     *
     * @throws AtlasConnectionException HA 구성에서 ACTIVE 서버를 찾지 못했을 때
     */
    URI current() {
        if (!isHighAvailability()) {
            return servers.get(0);
        }
        URI cached = active;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (active == null) {
                active = detectActive();
            }
            return active;
        }
    }

    /**
     * 방금 실패한 서버가 기억해 둔 ACTIVE 서버이면 그 기억을 지웁니다. 다른 스레드가 이미 새 서버를 찾아
     * 두었다면 그 값은 지우지 않도록, 실패한 서버와 같은 경우에만 지웁니다. HA가 아니면 아무것도 하지 않습니다.
     */
    void markFailed(URI server) {
        if (isHighAvailability() && Objects.equals(active, server)) {
            LOG.info("Atlas server {} failed; active server will be re-detected", server);
            active = null;
        }
    }

    // 서버를 설정된 순서대로 확인해 처음으로 ACTIVE라고 답한 서버를 돌려줍니다. 연결할 수 없는 서버는
    // 상태를 "UNREACHABLE (...)"로 기록해 두고 다음 서버로 넘어갑니다. 하나도 없으면 각 서버의 상태를
    // 예외 메시지에 담아 원인을 파악하기 쉽게 합니다.
    private URI detectActive() {
        Map<URI, String> states = new LinkedHashMap<>();
        for (URI server : servers) {
            String status;
            try {
                status = statusProbe.apply(server);
            } catch (RuntimeException e) {
                status = "UNREACHABLE (" + e.getMessage() + ")";
            }
            states.put(server, status);
            if (ACTIVE.equalsIgnoreCase(status)) {
                LOG.info("Using active Atlas server {}", server);
                return server;
            }
        }
        throw new AtlasConnectionException("No active Atlas server found: " + states);
    }
}
