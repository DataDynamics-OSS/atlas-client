package io.datadynamics.atlas.client.kafka.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

/**
 * {@code ATLAS_HOOK} 토픽에 보내는 메시지입니다. REST API와 같은 페이로드를 담으며, Atlas가 비동기로 적용합니다.
 * Atlas hook(Hive, Spark 등)도 이 메시지로 메타데이터를 보고합니다.
 * <p>
 * JSON의 {@code type} 속성으로 하위 클래스를 고릅니다. {@code type}은 생성자에서 정해지므로 역직렬화할 때
 * 값으로 설정하지 않고(쓰기 무시), 직렬화할 때만 출력합니다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EntityCreateRequest.class, name = "ENTITY_CREATE_V2"),
        @JsonSubTypes.Type(value = EntityPartialUpdateRequest.class, name = "ENTITY_PARTIAL_UPDATE_V2"),
        @JsonSubTypes.Type(value = EntityFullUpdateRequest.class, name = "ENTITY_FULL_UPDATE_V2"),
        @JsonSubTypes.Type(value = EntityDeleteRequest.class, name = "ENTITY_DELETE_V2")})
@JsonIgnoreProperties(value = "type", allowGetters = true)
public abstract class HookMessage extends AtlasModel {

    private final HookMessageType type;
    private String user;

    /**
     * 하위 클래스가 자신의 메시지 종류를 정합니다.
     *
     * @param type 메시지 종류
     */
    protected HookMessage(HookMessageType type) {
        this.type = type;
    }

    /**
     * 메시지 종류를 돌려줍니다.
     *
     * @return 메시지 종류
     */
    public HookMessageType getType() {
        return type;
    }

    /**
     * Atlas가 변경의 작성자로 기록할 사용자를 돌려줍니다.
     *
     * @return 사용자. 정하지 않았으면 {@code null}(보낼 때 퍼블리셔의 기본 사용자가 채워집니다)
     */
    public String getUser() {
        return user;
    }

    /**
     * Atlas가 변경의 작성자로 기록할 사용자를 정합니다.
     *
     * @param user 사용자
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Atlas가 메시지를 읽어 처리할 때 거부할 내용을 미리 검사합니다. 검사하지 않으면 그런 실패는 Atlas 서버
     * 로그에만 남으므로, 보내기 전에 호출하는 쪽에서 바로 알 수 있게 합니다.
     *
     * @throws IllegalArgumentException Atlas가 처리할 수 없는 메시지인 경우
     */
    public abstract void validate();

    /**
     * Atlas는 부분 갱신과 삭제 대상 엔티티를 guid가 아니라 typeName과 uniqueAttributes로 찾습니다. 그래서
     * typeName이 있고, uniqueAttributes 중 비어 있지 않은 값이 적어도 하나 있는지 확인합니다.
     *
     * @param what 오류 메시지에 쓸 대상 설명
     */
    static void requireUniqueAttributes(AtlasObjectId id, String what) {
        if (id == null || id.getTypeName() == null || id.getUniqueAttributes() == null
                || id.getUniqueAttributes().values().stream().allMatch(HookMessage::isBlank)) {
            throw new IllegalArgumentException(what + " needs a typeName and uniqueAttributes "
                    + "(Atlas looks entities of hook messages up by unique attributes): " + id);
        }
    }

    /** 값이 {@code null}이거나 문자열로 바꿨을 때 공백뿐이면 {@code true}입니다. */
    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
