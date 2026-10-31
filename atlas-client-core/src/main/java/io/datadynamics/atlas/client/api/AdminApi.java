package io.datadynamics.atlas.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;

import java.util.Collection;
import java.util.LinkedHashSet;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;

/**
 * {@code /api/atlas/admin} 엔드포인트로 서버 버전, HA 상태를 확인하고 소프트 삭제된 엔티티를 purge합니다.
 * <p>
 * 상태를 가지지 않으므로 여러 스레드에서 함께 써도 됩니다.
 */
public final class AdminApi {

    private static final String BASE = "/api/atlas/admin";

    private final AtlasTransport transport;

    /**
     * API 객체를 만듭니다.
     *
     * @param transport 요청을 보낼 전송 계층
     */
    public AdminApi(AtlasTransport transport) {
        this.transport = transport;
    }

    /**
     * Atlas 서버의 버전 정보를 가져옵니다. {@code GET /api/atlas/admin/version}을 호출합니다.
     *
     * @return 버전 정보
     */
    public AtlasVersion version() {
        return transport.call(GET, BASE + "/version", null, null, AtlasVersion.class);
    }

    /**
     * 현재 사용 중인 서버의 HA 상태(예: {@code ACTIVE}, {@code PASSIVE})를 가져옵니다.
     * {@code GET /api/atlas/admin/status}를 호출합니다.
     *
     * @return 상태 문자열, 응답 본문이 비어 있으면 {@code null}
     */
    public String status() {
        AtlasStatus status = transport.call(GET, BASE + "/status", null, null, AtlasStatus.class);
        return status == null ? null : status.status;
    }

    /**
     * 이미 소프트 삭제된({@code DELETED} 상태) 엔티티를 영구히 제거합니다. {@code PUT /api/atlas/admin/purge}를
     * 호출하며, 관리자 purge 권한이 필요합니다.
     *
     * @param guids 제거할 엔티티 guid 목록(중복은 한 번만 보냅니다)
     * @return 영향을 받은 엔티티 정보
     */
    public EntityMutationResponse purge(Collection<String> guids) {
        return transport.call(PUT, BASE + "/purge", null, new LinkedHashSet<>(guids), EntityMutationResponse.class);
    }

    /**
     * {@code /api/atlas/admin/version} 응답입니다. Atlas는 이 응답의 필드 이름을 대문자로 시작하는
     * ({@code Version}, {@code Name}, {@code Description}) 형태로 보내므로 {@link JsonProperty}로 맞춥니다.
     */
    public static class AtlasVersion extends AtlasModel {

        @JsonProperty("Version")
        private String version;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Description")
        private String description;

        /** Atlas 버전 문자열을 돌려줍니다. */
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // /api/atlas/admin/status 응답입니다. 필드 이름이 대문자로 시작하는 "Status"입니다.
    static class AtlasStatus extends AtlasModel {
        @JsonProperty("Status")
        String status;
    }
}
