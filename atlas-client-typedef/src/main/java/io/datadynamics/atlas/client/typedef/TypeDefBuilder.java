package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 모든 타입 정의 빌더의 기반 클래스입니다.
 * <p>
 * 설명, 버전, serviceType, 옵션 같은 모든 타입에 공통인 설정과 검증을 포함한 {@link #build()}를 제공합니다.
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 *
 * @param <B> 메서드 체이닝을 위한 구체 빌더 타입
 * @param <D> 만들어지는 정의 타입
 */
public abstract class TypeDefBuilder<B extends TypeDefBuilder<B, D>, D extends AtlasBaseTypeDef> {

    /** 빌더가 채워 나가는 정의입니다. {@link #build()}는 이 객체의 복사본을 돌려줍니다. */
    protected final D def;

    /**
     * 빈 정의 객체에 이름을 설정하여 빌더를 초기화합니다.
     *
     * @param def  채워 나갈 빈 정의 객체
     * @param name 타입 이름
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    protected TypeDefBuilder(D def, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Type name must not be blank");
        }
        this.def = def;
        def.setName(name);
    }

    /**
     * 구체 빌더 타입으로 캐스팅한 {@code this}를 돌려줍니다. 하위 클래스가 {@code B}를 자기 자신으로
     * 선언하므로 안전한 캐스팅입니다.
     *
     * @return 이 빌더
     */
    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    /**
     * 타입 설명을 지정합니다.
     *
     * @param description 설명 문구
     * @return 이 빌더
     */
    public B description(String description) {
        def.setDescription(description);
        return self();
    }

    /**
     * 정의의 버전(예: {@code "1.0"})을 지정합니다. plan 출력에서 변경을 추적할 때 유용합니다.
     *
     * @param typeVersion 버전 문자열
     * @return 이 빌더
     */
    public B typeVersion(String typeVersion) {
        def.setTypeVersion(typeVersion);
        return self();
    }

    /**
     * 관련 타입을 묶는 serviceType(예: {@code hive}, {@code my_company})을 지정합니다.
     * serviceType 단위로 내보내기(export)할 때 쓰입니다.
     *
     * @param serviceType 서비스 타입 이름
     * @return 이 빌더
     */
    public B serviceType(String serviceType) {
        def.setServiceType(serviceType);
        return self();
    }

    /**
     * 타입 수준 옵션을 하나 추가합니다.
     *
     * @param key   옵션 키
     * @param value 옵션 값
     * @return 이 빌더
     */
    public B option(String key, String value) {
        def.putOption(key, value);
        return self();
    }

    /**
     * 검증 직전에 값을 채워 넣어야 하는 빌더(예: 기본값 적용)를 위한 확장 지점입니다.
     * 빌더 원본이 아니라 {@link #build()}가 만든 복사본을 받으므로, 여기서 바꿔도 빌더 상태는 그대로입니다.
     * 기본 구현은 아무것도 하지 않습니다.
     *
     * @param definition 곧 검증되고 반환될 정의 복사본
     */
    protected void beforeBuild(D definition) {
    }

    /**
     * 검증을 거친 새 정의를 돌려줍니다. 호출 후에도 빌더를 계속 재사용할 수 있습니다.
     * <p>
     * 정의를 깊은 복사한 뒤 {@link #beforeBuild(AtlasBaseTypeDef)}를 적용하고,
     * {@link TypeDefValidator#validateStructure(AtlasBaseTypeDef)}로 구조를 검사합니다.
     * 경고는 무시하고 오류만 예외로 알립니다.
     *
     * @return 새 정의 객체
     * @throws IllegalStateException 정의가 구조적으로 잘못되었을 때. 메시지에 모든 오류가 {@code ; }로 이어져 들어갑니다
     */
    public D build() {
        D copy = AtlasJson.deepCopy(def);
        beforeBuild(copy);
        List<TypeIssue> errors = TypeDefValidator.validateStructure(copy).stream()
                .filter(TypeIssue::isError)
                .collect(Collectors.toList());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid type definition " + def.getName() + ": "
                    + errors.stream().map(TypeIssue::message).collect(Collectors.joining("; ")));
        }
        return copy;
    }
}
