package io.datadynamics.atlas.demo.web;

import org.springframework.ui.Model;

import java.util.function.Supplier;

/**
 * Runs a demo action and shows a failure on the same page instead of an error page,
 * so the user keeps the form input.
 */
final class Actions {

    static final String FAILURE = "failure";
    static final String MESSAGE = "message";

    private Actions() {
    }

    /** Returns the result, or {@code null} after adding {@code failure} to the model. */
    static <T> T call(Model model, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            model.addAttribute(FAILURE, Failure.of(e));
            return null;
        }
    }

    /** Returns whether the action succeeded; adds {@code message} on success, {@code failure} otherwise. */
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
