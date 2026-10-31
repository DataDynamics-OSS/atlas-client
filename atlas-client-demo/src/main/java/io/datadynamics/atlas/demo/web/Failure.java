package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageException;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypePlanException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자에게 보여 줄 형태로 바꾼 예외입니다. 이 데모는 클라이언트가 문제를 어떻게 알려 주는지 보여 주는 것이
 * 목적이므로, 클라이언트 예외의 타입(클래스)을 함께 보여 주는 것이 중요합니다.
 *
 * @param type      예외 클래스의 전체 이름
 * @param status    Atlas가 돌려준 HTTP 상태 코드(있을 때만)
 * @param errorCode {@code ATLAS-404-00-005} 같은 Atlas 오류 코드(있을 때만)
 * @param message   오류 메시지
 * @param details   추가로 보여 줄 줄들: 리니지에서 찾지 못한 엔티티, 타입 plan 오류 등
 */
public record Failure(String type, Integer status, String errorCode, String message, List<String> details) {

    /**
     * 예외를 {@link Failure}로 바꿉니다.
     * <p>
     * {@link LineageException}이면 찾지 못한 엔티티 목록을, {@link TypePlanException}이면 plan의 문제 목록을
     * {@code details}에 넣습니다. {@link AtlasServerException}이면 상태 코드와 오류 코드를 채우고, 메시지는
     * Atlas가 보낸 오류 메시지를 우선 씁니다. 메시지가 없는 예외는 {@code toString()} 결과를 메시지로 씁니다.
     *
     * @param e 변환할 예외
     * @return 화면에 보여 줄 실패 정보
     */
    public static Failure of(Throwable e) {
        List<String> details = List.of();
        if (e instanceof LineageException lineage && lineage.getMissingEntities() != null) {
            details = lineage.getMissingEntities().stream().map(EntityRef::toString).collect(Collectors.toList());
        } else if (e instanceof TypePlanException plan) {
            details = plan.getPlan().getIssues().stream().map(TypeIssue::toString).collect(Collectors.toList());
        }
        if (e instanceof AtlasServerException server) {
            return new Failure(e.getClass().getName(), server.getStatusCode(), server.getErrorCode(),
                    server.getErrorMessage() != null ? server.getErrorMessage() : e.getMessage(), details);
        }
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        return new Failure(e.getClass().getName(), null, null, message, details);
    }

    /**
     * 패키지를 뺀 예외 클래스 이름을 돌려줍니다.
     *
     * @return 예: {@code AtlasNotFoundException}
     */
    public String simpleType() {
        return type.substring(type.lastIndexOf('.') + 1);
    }
}
