package io.datadynamics.atlas.client.model.typedef;

/**
 * 속성 또는 relationship 끝(end)의 cardinality, 즉 값을 몇 개 가질 수 있는지를 나타냅니다.
 */
public enum Cardinality {
    /** 값을 하나만 가집니다. */
    SINGLE,
    /** 값을 여러 개 가지며 순서가 있고 중복을 허용합니다. */
    LIST,
    /** 값을 여러 개 가지며 중복을 허용하지 않습니다. */
    SET
}
