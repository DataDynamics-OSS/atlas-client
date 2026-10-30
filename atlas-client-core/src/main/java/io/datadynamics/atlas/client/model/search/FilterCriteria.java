package io.datadynamics.atlas.client.model.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Basic search filter. Either a leaf ({@code attributeName operator attributeValue}) or a group
 * ({@code condition} over {@code criterion}).
 * <pre>{@code
 * FilterCriteria.and(
 *     FilterCriteria.of("owner", Operator.EQ, "kim"),
 *     FilterCriteria.of("name", Operator.STARTS_WITH, "sales_"))
 * }</pre>
 */
public class FilterCriteria extends AtlasModel {

    public enum Condition {
        AND,
        OR
    }

    /**
     * Filter operators. Serialized with the first symbol Atlas accepts for each.
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

        private final String[] symbols;

        Operator(String... symbols) {
            this.symbols = symbols;
        }

        @JsonValue
        public String getSymbol() {
            return symbols[0];
        }

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

    public static FilterCriteria of(String attributeName, Operator operator, Object value) {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setAttributeName(attributeName);
        criteria.setOperator(operator);
        criteria.setAttributeValue(value == null ? null : value.toString());
        return criteria;
    }

    public static FilterCriteria isNull(String attributeName) {
        return of(attributeName, Operator.IS_NULL, null);
    }

    public static FilterCriteria notNull(String attributeName) {
        return of(attributeName, Operator.NOT_NULL, null);
    }

    public static FilterCriteria and(FilterCriteria... criteria) {
        return group(Condition.AND, criteria);
    }

    public static FilterCriteria or(FilterCriteria... criteria) {
        return group(Condition.OR, criteria);
    }

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

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public List<FilterCriteria> getCriterion() {
        return criterion;
    }

    public void setCriterion(List<FilterCriteria> criterion) {
        this.criterion = criterion;
    }
}
