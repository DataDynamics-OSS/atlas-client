package io.datadynamics.atlas.client.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.datadynamics.atlas.client.json.AtlasJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atlas와 주고받는 모든 JSON 모델(wire model)의 최상위 클래스입니다.
 * <p>
 * 하위 클래스가 필드로 매핑하지 않은 속성은 {@link #getAdditionalProperties()}에 보관했다가 직렬화할 때
 * 다시 써 넣습니다. 덕분에 Atlas 버전이 올라가며 새 필드가 추가되어도 "읽기 → 수정 → 쓰기" 과정에서
 * 정보가 사라지지 않습니다. 예를 들어 Atlas 2.3에서 가져온 typedef를, 이 클라이언트가 모르는 필드를
 * 잃지 않고 그대로 수정해 다시 보낼 수 있습니다.
 * <p>
 * 이 클래스와 하위 모델 클래스는 스레드 안전하지 않습니다. 여러 스레드에서 같은 객체를 수정하려면
 * 호출하는 쪽에서 동기화해야 합니다.
 */
public abstract class AtlasModel {

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    /**
     * 하위 클래스가 알지 못하는 JSON 속성들을 돌려줍니다. Jackson이 직렬화할 때 이 맵의 항목을 최상위 속성으로
     * 펼쳐 씁니다.
     *
     * @return 속성 이름에서 값으로의 맵(수정 가능한 원본 맵이며, 순서는 읽은 순서를 유지합니다)
     */
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    /**
     * 알 수 없는 JSON 속성 하나를 보관합니다. 역직렬화할 때 Jackson이 매핑되지 않은 속성마다 호출합니다.
     *
     * @param name  속성 이름
     * @param value 속성 값(JSON 객체는 {@link Map}, 배열은 {@link java.util.List} 등으로 들어옵니다)
     */
    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    /** 디버깅용 문자열을 돌려줍니다. 클래스 이름 뒤에 이 객체의 JSON 표현을 붙인 형태입니다. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + AtlasJson.toJson(this);
    }
}
