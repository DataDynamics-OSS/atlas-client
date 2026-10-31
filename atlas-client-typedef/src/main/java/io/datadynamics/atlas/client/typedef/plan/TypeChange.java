package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;

import java.util.List;

/**
 * 타입 하나에 대해 계획된 변경입니다.
 *
 * @param name     타입 이름
 * @param category 타입 범주(ENTITY, CLASSIFICATION 등)
 * @param action   수행할 동작
 * @param details  무엇이 바뀌는지 사람이 읽을 수 있게 적은 목록({@code + attribute 'owner' (string)} 등)
 * @param payload  Atlas로 보낼 정의. 생성이면 원하는 정의, 수정이면 서버 정의 위에 원하는 정의를 덮어쓴 것,
 *                 삭제면 서버 정의입니다
 */
public record TypeChange(String name, TypeCategory category, Action action, List<String> details,
                         AtlasBaseTypeDef payload) {

    /** 타입에 대해 수행할 동작입니다. {@link TypePlan#render()} 출력에 쓰는 기호와 이름을 함께 갖습니다. */
    public enum Action {
        /** 새 타입을 생성합니다. */
        CREATE("+", "create"),
        /** 기존 타입을 수정합니다. */
        UPDATE("~", "update"),
        /** 바뀌는 것이 없습니다. Atlas를 호출하지 않습니다. */
        UNCHANGED("=", "unchanged"),
        /** 기존 타입을 삭제합니다. */
        DELETE("-", "delete");

        private final String symbol;
        private final String label;

        Action(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        /**
         * plan 출력 줄 맨 앞에 붙는 기호({@code +}, {@code ~}, {@code =}, {@code -})를 돌려줍니다.
         *
         * @return 한 글자 기호
         */
        public String symbol() {
            return symbol;
        }

        /**
         * plan 출력에 쓰는 소문자 동작 이름을 돌려줍니다.
         *
         * @return 동작 이름
         */
        public String label() {
            return label;
        }
    }

    /** {@code details}를 불변 목록으로 복사해 record가 바깥 목록 변경에 영향받지 않게 합니다. */
    public TypeChange {
        details = List.copyOf(details);
    }
}
