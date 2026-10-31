package io.datadynamics.atlas.demo.trace;

import io.datadynamics.atlas.client.http.RequestListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Atlas 클라이언트가 보낸 HTTP 요청을 기록합니다. 각 페이지는 이 기록으로 데모 동작이 실제로
 * 어떤 요청을 보냈는지 보여 줍니다.
 * <p>
 * 클라이언트 호출은 서블릿 스레드에서 실행되므로, {@link #begin()}과 {@link #end()} 사이에 그 스레드가 보낸
 * 요청을 스레드별로 모읍니다({@link TraceInterceptor} 참고). 모든 요청은 크기가 제한된 전체 이력에도 쌓입니다.
 * <p>
 * 스레드 안전합니다. 스레드별 목록은 {@link ThreadLocal}로, 전체 이력은 {@code synchronized}로 보호합니다.
 */
@Component
public class RequestTrace {

    /** 전체 이력에 보관하는 최대 요청 수입니다. 넘으면 오래된 것부터 버립니다. */
    static final int HISTORY_SIZE = 300;
    /** 요청·응답 본문을 보관할 최대 글자 수입니다. 넘는 부분은 잘라 냅니다. */
    static final int MAX_BODY_LENGTH = 20_000;

    private final ThreadLocal<List<TracedRequest>> current = new ThreadLocal<>();
    private final Deque<TracedRequest> history = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 현재 스레드가 보내는 요청을 모으기 시작합니다.
     *
     * @return 이후 요청이 계속 추가되는(살아 있는) 목록
     */
    public List<TracedRequest> begin() {
        List<TracedRequest> requests = new ArrayList<>();
        current.set(requests);
        return requests;
    }

    /** 현재 스레드의 요청 수집을 끝냅니다. 스레드 풀에서 재사용될 스레드에 목록이 남지 않도록 반드시 호출해야 합니다. */
    public void end() {
        current.remove();
    }

    /**
     * {@link #begin()} 이후 현재 스레드가 보낸 요청을 돌려줍니다.
     *
     * @return 요청 목록. 추적 중인 웹 요청 밖에서 부르면 빈 목록
     */
    public List<TracedRequest> current() {
        List<TracedRequest> requests = current.get();
        return requests != null ? requests : List.of();
    }

    /**
     * HTTP 시도 하나를 기록합니다. {@link RequestListener}로 클라이언트에 등록되어 시도마다 호출됩니다.
     * <p>
     * 본문은 {@link #MAX_BODY_LENGTH}까지만 남기고, 응답 없이 실패했으면 예외 클래스 이름과 메시지를 남깁니다.
     * 현재 스레드가 추적 중이면 스레드별 목록에도 추가하고, 항상 전체 이력 맨 앞에 추가합니다.
     *
     * @param exchange 클라이언트가 보고한 요청·응답 정보
     */
    public void record(RequestListener.Exchange exchange) {
        Throwable failure = exchange.failure();
        TracedRequest request = new TracedRequest(sequence.incrementAndGet(), Instant.now(), exchange.method(),
                exchange.uri().toString(), exchange.attempt(), exchange.statusCode(), exchange.elapsed().toMillis(),
                truncate(exchange.requestBody()), truncate(exchange.responseBody()),
                failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage());
        List<TracedRequest> requests = current.get();
        if (requests != null) {
            requests.add(request);
        }
        synchronized (history) {
            history.addFirst(request);
            while (history.size() > HISTORY_SIZE) {
                history.removeLast();
            }
        }
    }

    /**
     * 전체 요청 이력의 복사본을 돌려줍니다.
     *
     * @return 최근 요청이 앞에 오는 목록(최대 {@link #HISTORY_SIZE}개)
     */
    public List<TracedRequest> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /** 전체 요청 이력을 비웁니다. */
    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }

    /** 본문이 너무 길면 앞부분만 남기고, 잘린 글자 수를 끝에 덧붙입니다. 화면과 메모리 사용량을 제한하기 위해서입니다. */
    private static String truncate(String body) {
        if (body == null || body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH) + "\n... (" + (body.length() - MAX_BODY_LENGTH) + " more characters)";
    }
}
