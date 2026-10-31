package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.exception.AtlasNotFoundException;
import io.datadynamics.atlas.demo.config.DemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@link Actions} 밖에서 난 실패를 오류 페이지로 보여 주고, 모든 페이지가 함께 쓰는 모델 속성을 넣습니다.
 */
@ControllerAdvice
class ErrorAdvice {

    private final DemoProperties properties;

    ErrorAdvice(DemoProperties properties) {
        this.properties = properties;
    }

    /** 모든 페이지에 {@code kafkaEnabled} 속성을 넣습니다. 레이아웃이 Kafka 메뉴를 보일지 정할 때 씁니다. */
    @ModelAttribute("kafkaEnabled")
    boolean kafkaEnabled() {
        return properties.kafka().enabled();
    }

    /**
     * 컨트롤러에서 처리하지 않은 클라이언트 예외와 잘못된 입력을 {@code failure} 페이지로 보여 줍니다.
     * <p>
     * 응답 상태는 {@link IllegalArgumentException}이면 400, {@link AtlasNotFoundException}이면 404,
     * 그 밖의 클라이언트 예외는 Atlas 쪽 문제이므로 502(Bad Gateway)입니다.
     */
    @ExceptionHandler({AtlasClientException.class, IllegalArgumentException.class})
    ModelAndView clientFailure(RuntimeException e) {
        ModelAndView view = new ModelAndView("failure");
        view.addObject(Actions.FAILURE, Failure.of(e));
        view.setStatus(e instanceof AtlasNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY);
        if (e instanceof IllegalArgumentException) {
            view.setStatus(HttpStatus.BAD_REQUEST);
        }
        return view;
    }
}
