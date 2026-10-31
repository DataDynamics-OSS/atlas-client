package io.datadynamics.atlas.demo.web;

import org.springframework.ui.Model;

import java.util.function.Supplier;

/**
 * 데모 동작을 실행하고, 실패하면 오류 페이지로 넘어가는 대신 같은 페이지에 실패 내용을 보여 줍니다.
 * 이렇게 하면 사용자가 입력한 폼 값이 그대로 남아 고쳐서 다시 시도할 수 있습니다.
 * <p>
 * {@link RuntimeException}만 잡아 {@link Failure}로 바꿔 모델에 넣습니다.
 */
final class Actions {

    /** 실패 내용({@link Failure})을 담는 모델 속성 이름입니다. */
    static final String FAILURE = "failure";
    /** 성공 메시지를 담는 모델 속성 이름입니다. */
    static final String MESSAGE = "message";

    private Actions() {
    }

    /**
     * 결과를 돌려주는 동작을 실행합니다.
     *
     * @return 동작의 결과. 실패하면 모델에 {@code failure}를 넣고 {@code null}을 돌려줍니다.
     */
    static <T> T call(Model model, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            model.addAttribute(FAILURE, Failure.of(e));
            return null;
        }
    }

    /**
     * 결과가 없는 동작을 실행합니다. 성공하면 모델에 {@code message}를, 실패하면 {@code failure}를 넣습니다.
     *
     * @return 동작이 성공했으면 {@code true}
     */
    static boolean run(Model model, String successMessage, Runnable action) {
        try {
            action.run();
            model.addAttribute(MESSAGE, successMessage);
            return true;
        } catch (RuntimeException e) {
            model.addAttribute(FAILURE, Failure.of(e));
            return false;
        }
    }
}
