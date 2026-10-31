package io.datadynamics.atlas.client.model.typedef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * struct 타입 정의입니다. 엔티티, classification, business metadata 정의의 부모 클래스이기도 합니다.
 * <p>
 * 속성 목록({@link AtlasAttributeDef})을 가지는 모든 타입 정의가 이 클래스를 상속합니다.
 */
public class AtlasStructDef extends AtlasBaseTypeDef {

    private List<AtlasAttributeDef> attributeDefs;

    /** 빈 struct 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasStructDef() {
        super(TypeCategory.STRUCT);
    }

    /**
     * 이름을 지정해 struct 정의를 만듭니다.
     *
     * @param name struct 타입 이름
     */
    public AtlasStructDef(String name) {
        super(TypeCategory.STRUCT, name);
    }

    /**
     * 하위 클래스가 자신의 분류를 지정할 때 쓰는 생성자입니다.
     *
     * @param category 하위 클래스의 타입 분류
     */
    protected AtlasStructDef(TypeCategory category) {
        super(category);
    }

    /**
     * 하위 클래스가 분류와 이름을 지정할 때 쓰는 생성자입니다.
     *
     * @param category 하위 클래스의 타입 분류
     * @param name     타입 이름
     */
    protected AtlasStructDef(TypeCategory category, String name) {
        super(category, name);
    }

    public List<AtlasAttributeDef> getAttributeDefs() {
        return attributeDefs;
    }

    public void setAttributeDefs(List<AtlasAttributeDef> attributeDefs) {
        this.attributeDefs = attributeDefs;
    }

    /**
     * 속성 정의를 목록 끝에 추가합니다. 목록이 없으면 새로 만듭니다.
     *
     * @param attributeDef 추가할 속성 정의
     */
    public void addAttributeDef(AtlasAttributeDef attributeDef) {
        if (attributeDefs == null) {
            attributeDefs = new ArrayList<>();
        }
        attributeDefs.add(attributeDef);
    }

    /**
     * 이름으로 속성 정의를 찾습니다. 이 정의에 직접 선언된 속성만 찾으며, 상위 타입에서 물려받은 속성은
     * 찾지 않습니다.
     *
     * @param attributeName 찾을 속성 이름
     * @return 같은 이름의 첫 번째 속성 정의, 없으면 빈 {@link Optional}
     */
    public Optional<AtlasAttributeDef> findAttributeDef(String attributeName) {
        if (attributeDefs == null) {
            return Optional.empty();
        }
        return attributeDefs.stream().filter(a -> attributeName.equals(a.getName())).findFirst();
    }
}
