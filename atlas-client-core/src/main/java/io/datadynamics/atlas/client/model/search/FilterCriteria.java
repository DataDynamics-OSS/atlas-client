package io.datadynamics.atlas.client.model.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * basic 검색의 필터 조건입니다. 단일 조건({@code attributeName operator attributeValue})이거나,
 * 여러 조건({@code criterion})을 {@code condition}(AND/OR)으로 묶은 그룹 중 하나입니다.
 * 그룹 안에 다시 그룹을 넣어 복잡한 조건을 만들 수 있습니다.
 * <pre>{@code
 * FilterCriteria.and(
 *     FilterCriteria.of("owner", Operator.EQ, "kim"),
 *     FilterCriteria.of("name", Operator.STARTS_WITH, "sales_"))
 * }</pre>
 */
public class FilterCriteria extends AtlasModel {

    /** 그룹 안의 조건들을 묶는 방법입니다. */
    public enum Condition {
        AND,
        OR
    }

    /**
     * 필터 연산자입니다. JSON으로 쓸 때는 각 연산자에 대해 Atlas가 받아들이는 기호 중 첫 번째를 씁니다.
     * 읽을 때는 enum 이름이나 등록된 모든 기호를 받아들입니다.
     */
    public enum Operator {
        LT("<", "lt"),
        GT(">", "gt"),
        LTE("<=", "lte"),
        GTE(">=", "gte"),
        EQ("=", "eq"),
        NEQ("!=", "neq"),
        IN("in", "IN"),
        LIKE("like", "LIKE"),
        STARTS_WITH("startsWith", "STARTSWITH", "begins_with", "BEGINS_WITH"),
        ENDS_WITH("endsWith", "ENDSWITH", "ends_with", "ENDS_WITH"),
        CONTAINS("contains", "CONTAINS"),
        NOT_CONTAINS("not_contains", "NOT_CONTAINS"),
        CONTAINS_ANY("containsAny", "CONTAINSANY", "contains_any", "CONTAINS_ANY"),
        CONTAINS_ALL("containsAll", "CONTAINSALL", "contains_all", "CONTAINS_ALL"),
        IS_NULL("isNull", "ISNULL", "is_null", "IS_NULL"),
        NOT_NULL("notNull", "NOTNULL", "not_null", "NOT_NULL"),
        TIME_RANGE("timerange", "TIMERANGE", "time_range", "TIME_RANGE"),
        NOT_EMPTY("notEmpty", "NOTEMPTY", "not_empty", "NOT_EMPTY");

        // Atlas가 받아들이는 기호들입니다. 첫 번째가 직렬화에 쓰는 대표 기호입니다.
        private final String[] symbols;

        Operator(String... symbols) {
            this.symbols = symbols;
        }

        /** JSON으로 쓸 대표 기호(첫 번째 기호)를 돌려줍니다. */
        @JsonValue
        public String getSymbol() {
            return symbols[0];
        }

        /**
         * enum 이름이나 기호로 연산자를 찾습니다. 대소문자는 등록된 형태와 정확히 같아야 합니다.
         *
         * @param symbol enum 이름(예: {@code STARTS_WITH}) 또는 기호(예: {@code startsWith}, {@code begins_with})
         * @return 찾은 연산자
         * @throws IllegalArgumentException 알 수 없는 기호일 때
         */
        @JsonCreator
        public static Operator fromSymbol(String symbol) {
            for (Operator operator : values()) {
                if (operator.name().equals(symbol) || Arrays.asList(operator.symbols).contains(symbol)) {
                    return operator;
                }
            }
            throw new IllegalArgumentException("Unknown filter operator: " + symbol);
        }
    }

    private String attributeName;
    private Operator operator;
    private String attributeValue;
    private Condition condition;
    private List<FilterCriteria> criterion;

    /**
     * 단일 조건을 만듭니다. 값은 {@link Object#toString()}으로 문자열이 됩니다.
     *
     * @param attributeName 속성 이름
     * @param operator      연산자
     * @param value         비교할 값, 필요 없는 연산자면 {@code null}
     * @return 새 조건
     */
    public static FilterCriteria of(String attributeName, Operator operator, Object value) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setAttributeName(attributeName);
        criteria.setOperator(operator);
        criteria.setAttributeValue(value == null ? null : value.toString());
        return criteria;
    }

    /** 속성 값이 없는(null) 엔티티를 찾는 조건을 만듭니다. */
    public static FilterCriteria isNull(String attributeName) {
        return of(attributeName, Operator.IS_NULL, null);
    }

    /** 속성 값이 있는(null이 아닌) 엔티티를 찾는 조건을 만듭니다. */
    public static FilterCriteria notNull(String attributeName) {
        return of(attributeName, Operator.NOT_NULL, null);
    }

    /**
     * 주어진 조건을 모두 만족해야 하는 AND 그룹을 만듭니다.
     *
     * @param criteria 묶을 조건들
     * @return 새 그룹 조건
     */
    public static FilterCriteria and(FilterCriteria... criteria) {
        return group(Condition.AND, criteria);
    }

    /**
     * 주어진 조건 중 하나라도 만족하면 되는 OR 그룹을 만듭니다.
     *
     * @param criteria 묶을 조건들
     * @return 새 그룹 조건
     */
    public static FilterCriteria or(FilterCriteria... criteria) {
        return group(Condition.OR, criteria);
    }

    // 그룹 조건을 만듭니다. 나중에 조건을 더 넣을 수 있도록 수정 가능한 리스트로 복사합니다.
    private static FilterCriteria group(Condition condition, FilterCriteria... criteria) {
        FilterCriteria group = new FilterCriteria();
        group.setCondition(condition);
        group.setCriterion(new ArrayList<>(Arrays.asList(criteria)));
        return group;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    /** 비교할 값을 문자열로 돌려줍니다. 그룹 조건이면 {@code null}입니다. */
    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    /** 그룹 조건의 결합 방법을 돌려줍니다. 단일 조건이면 {@code null}입니다. */
    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /** 그룹에 속한 하위 조건들을 돌려줍니다. 단일 조건이면 {@code null}입니다. */
    public List<FilterCriteria> getCriterion() {
        return criterion;
    }

    public void setCriterion(List<FilterCriteria> criterion) {
        this.criterion = criterion;
    }
}
