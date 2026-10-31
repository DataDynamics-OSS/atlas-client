package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.exception.AtlasNotFoundException;
import io.datadynamics.atlas.demo.config.DemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

/** Error page for failures outside {@link Actions}, and attributes shared by all pages. */
@ControllerAdvice
class ErrorAdvice {

    private final DemoProperties properties;

    ErrorAdvice(DemoProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("kafkaEnabled")
    boolean kafkaEnabled() {
        return properties.kafka().enabled();
    }

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
