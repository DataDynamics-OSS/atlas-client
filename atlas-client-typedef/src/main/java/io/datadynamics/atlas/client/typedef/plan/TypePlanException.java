package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.typedef.TypeIssue;

import java.util.stream.Collectors;

/**
 * 오류가 있는 plan을 적용하려 할 때 던져집니다. 이 예외가 났다면 Atlas에는 아무것도 보내지 않은 상태입니다.
 * <p>
 * 예외 메시지에는 plan의 모든 오류가 한 줄에 하나씩 들어갑니다.
 */
public class TypePlanException extends AtlasClientException {

    /**
     * {@link TypePlan}은 {@code Serializable}이 아니므로 예외 직렬화에서 빼기 위해 {@code transient}로 둡니다.
     * 그래서 역직렬화된 예외에서는 {@code null}입니다.
     */
    private final transient TypePlan plan;

    /**
     * plan의 오류 목록으로 메시지를 만들어 예외를 생성합니다.
     *
     * @param plan 오류가 있는 plan
     */
    public TypePlanException(TypePlan plan) {
        super("Type plan has errors, nothing was applied:\n" + plan.getErrors().stream()
                .map(TypeIssue::toString)
                .collect(Collectors.joining("\n")));
        this.plan = plan;
    }

    /**
     * 적용하려던 plan을 돌려줍니다. {@link TypePlan#getErrors()}로 오류를 확인할 수 있습니다.
     *
     * @return 거부된 plan
     */
    public TypePlan getPlan() {
        return plan;
    }
}
