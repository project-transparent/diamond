package org.transparent.diamond.compiletest;

import java.lang.annotation.*;

/**
 * Marks an {@link javax.annotation.processing.Processor annotation processor}
 * for testing. Diamond will compile the processor, invoke javac with the processor
 * with a stub class, invoke vanilla javac with the provided test case, and directly
 * compare the bytecode results. Iff the bytecode matches <b>exactly</b>, the test passes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProcessorTest {

    /**
     * Annotation class to use. It should support the {@link #target()} ElementType.
     */
    Class<? extends Annotation> annotation() default AnnotationForTesting.class;

    /**
     * Diamond will give the annotation processor an appropriate
     * stub class with an annotated target of this type. The target,
     * when possible, will be named <code>Example</code> or <code>example</code>.
     * <br>
     * Currently only classes, methods, constructors, and fields are supported.
     */
    ElementType target();

    /**
     * Expected code to be generated by this processor (in source code form).
     */
    String[] source();

}