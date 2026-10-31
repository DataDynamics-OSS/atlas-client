package io.datadynamics.atlas.client.typedef;

/**
 * 타입 정의를 검증하거나 plan을 세우는 중에 발견한 문제 하나를 나타냅니다.
 * <p>
 * {@code ERROR}는 Atlas가 거부하거나 의도와 다르게 조용히 처리될 문제이며 {@code apply}를 막습니다.
 * {@code WARNING}은 참고용 정보입니다.
 *
 * @param severity 심각도
 * @param typeName 문제가 있는 타입 이름
 * @param message  문제 설명(영어)
 */
public record TypeIssue(Severity severity, String typeName, String message) {

    /** 문제의 심각도입니다. */
    public enum Severity {
        /** apply를 막는 오류입니다. */
        ERROR,
        /** 참고용 경고입니다. */
        WARNING
    }

    /**
     * {@code ERROR} 심각도의 문제를 만듭니다.
     *
     * @param typeName 문제가 있는 타입 이름
     * @param message  문제 설명
     * @return 새 {@link TypeIssue}
     */
    public static TypeIssue error(String typeName, String message) {
        return new TypeIssue(Severity.ERROR, typeName, message);
    }

    /**
     * {@code WARNING} 심각도의 문제를 만듭니다.
     *
     * @param typeName 문제가 있는 타입 이름
     * @param message  문제 설명
     * @return 새 {@link TypeIssue}
     */
    public static TypeIssue warning(String typeName, String message) {
        return new TypeIssue(Severity.WARNING, typeName, message);
    }

    /**
     * 이 문제가 오류인지 알려 줍니다.
     *
     * @return 심각도가 {@code ERROR}이면 {@code true}
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** {@code "ERROR my_table: message"} 형태의 한 줄 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return severity + " " + typeName + ": " + message;
    }
}
