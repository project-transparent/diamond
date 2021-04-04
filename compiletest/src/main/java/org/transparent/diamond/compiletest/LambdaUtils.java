package org.transparent.diamond.compiletest;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Incredibly hacky solutions to make lambdas look better.
 */
public class LambdaUtils {

    /**
     * {@link Consumer}-like interface that can throw arbitrary exceptions.
     */
    public interface ConsumerThatCanThrow<T, E extends Throwable> {
        void apply(T t) throws E;
    }

    /**
     * {@link Function}-like interface that can throw arbitrary exceptions.
     */
    public interface FunctionThatCanThrow<T, R, E extends Throwable> {
        R apply(T t) throws E;
    }

    /**
     * Wrapper for lambdas that can throw exceptions.
     */
    public static <T, E extends Throwable> Consumer<T> rethrowChecked(ConsumerThatCanThrow<T, E> function) {
        return t -> {
            try {
                function.apply(t);
            } catch (Throwable e) {
                throwChecked(e);
            }
        };
    }

    /**
     * Wrapper for lambdas that can throw exceptions.
     */
    public static <T, R, E extends Throwable> Function<T, R> rethrowChecked(FunctionThatCanThrow<T, R, E> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Throwable e) {
                throwChecked(e);
                return null; // unreachable
            }
        };
    }

    /**
     * Sneakily throws checked exceptions as if they were unchecked.
     */
    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public static <T extends Throwable> RuntimeException throwChecked(Throwable throwable) throws T {
        throw (T) throwable;
    }

}
