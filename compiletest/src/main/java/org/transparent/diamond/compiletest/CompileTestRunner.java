package org.transparent.diamond.compiletest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import javax.annotation.processing.Processor;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.transparent.diamond.compiletest.LambdaUtils.rethrowChecked;

public class CompileTestRunner {

    public static class CompilerException extends Exception {
        public CompilerException(String clazz) {
            super("Could not compile " + clazz);
        }
    }

    /**
     * Tests each processor annotated with {@link ProcessorTest} in the given directory.
     *
     * @param sourceRoot Directory to look in for tests
     * @return A stream of {@link DynamicTest}s that can be passed to JUnit
     * @throws IllegalArgumentException      If sourceRoot is null, does not exist, or is not a directory
     * @throws UnsupportedOperationException If a processor test has an invalid or unsupported configuration
     * @throws CompilerException             If an error occurs in compiling a processor
     * @throws IOException                   If an error occurs in IO
     */
    public static Stream<DynamicTest> runProcessorTestsIn(File sourceRoot)
            throws IllegalArgumentException, UnsupportedOperationException, CompilerException, IOException {
        return runProcessorTestsIn(sourceRoot, null);
    }

    /**
     * Tests each processor annotated with {@link ProcessorTest} in the given directory.
     *
     * @param sourceRoot Directory to look in for tests
     * @param classpath  Compile classpath for javac
     * @return A stream of {@link DynamicTest}s that can be passed to JUnit
     * @throws IllegalArgumentException      If sourceRoot is null, does not exist, or is not a directory
     * @throws UnsupportedOperationException If a processor test has an invalid or unsupported configuration
     * @throws CompilerException             If an error occurs in compiling a processor
     * @throws IOException                   If an error occurs in IO
     */
    public static Stream<DynamicTest> runProcessorTestsIn(File sourceRoot, Collection<File> classpath)
            throws IllegalArgumentException, UnsupportedOperationException, CompilerException, IOException {
        if (sourceRoot == null || !sourceRoot.exists() || !sourceRoot.isDirectory()) {
            throw new IllegalArgumentException("sourceRoot must be a directory!");
        }
        CompileTestUtils compileTestUtils = new CompileTestUtils(sourceRoot, classpath);
        Path sourceRootPath = sourceRoot.toPath();
        return Files.walk(sourceRootPath)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> {
                    File f = p.toFile();
                    return f.exists() && f.isFile();
                })
                .map(sourceRootPath::relativize)
                .map(rethrowChecked(p -> {
                    String className = p.toString().replace(".java", "").replace(File.separator, ".");
                    Map<String, byte[]> byteCode = compileTestUtils.compile(className).orElseThrow(() -> new CompilerException(className));
                    AnnotationProcessorClassLoader classLoader = new AnnotationProcessorClassLoader(byteCode);
                    Class<?> maybeProcessorClass = classLoader.loadClass(className);
                    HashSet<Class<?>> interfaces = new HashSet<>();
                    Class<?> maybeProcessorSuperclass = maybeProcessorClass;
                    // A wild do-while loop appears!
                    do {
                        Collections.addAll(interfaces, maybeProcessorSuperclass.getInterfaces());
                    } while ((maybeProcessorSuperclass = maybeProcessorSuperclass.getSuperclass()) != null);
                    if (interfaces.contains(Processor.class)) {
                        return maybeProcessorClass;
                    } else {
                        return null;
                    }
                }))
                .filter(Objects::nonNull)
                .map(rethrowChecked(c -> {
                    ProcessorTest annotation = null;
                    for (Annotation maybeAnnotation : c.getAnnotations()) {
                        if (maybeAnnotation instanceof ProcessorTest) {
                            annotation = (ProcessorTest) maybeAnnotation;
                            break;
                        }
                    }
                    if (annotation == null) {
                        return null;
                    }
                    if (!annotation.annotation().isAnnotation() || annotation.annotation().isArray()) {
                        throw new UnsupportedOperationException("annotation must be an annotation class");
                    }

                    String annotationClassName = annotation.annotation().getName();
                    String stubSource;
                    if (annotation.target() == ElementType.TYPE) {
                        stubSource = new StringBuilder()
                                .append("import " + annotationClassName + ";\n")
                                .append("@" + annotationClassName + "\n")
                                .append("public class Example {}")
                                .toString();
                    } else if (annotation.target() == ElementType.METHOD) {
                        stubSource = new StringBuilder()
                                .append("import " + annotationClassName + ";\n")
                                .append("public class Example {\n")
                                .append("    @" + annotationClassName + "\n")
                                .append("    public void example() {}\n")
                                .append("}")
                                .toString();
                    } else if (annotation.target() == ElementType.CONSTRUCTOR) {
                        stubSource = new StringBuilder()
                                .append("import " + annotationClassName + ";\n")
                                .append("public class Example {\n")
                                .append("    @" + annotationClassName + "\n")
                                .append("    public Example() {}\n")
                                .append("}")
                                .toString();
                    } else if (annotation.target() == ElementType.FIELD) {
                        stubSource = new StringBuilder()
                                .append("import " + annotationClassName + ";\n")
                                .append("public class Example {\n")
                                .append("    @" + annotationClassName + "\n")
                                .append("    public int example;\n")
                                .append("}")
                                .toString();
                    } else {
                        throw new UnsupportedOperationException("Unsupported test target type " + annotation.target().toString());
                    }

                    String expectedSource;
                    if (!annotation.expectedFile().isEmpty()) {
                        Path expectedSourcePath = sourceRootPath.resolve(annotation.expectedFile());
                        expectedSource = String.join("\n", Files.readAllLines(expectedSourcePath));
                    } else if (annotation.expected().length > 0) {
                        String[] rawExpectedSource = annotation.expected();
                        StringJoiner expectedSourceBuilder = new StringJoiner("\n");
                        for (int i = 0; i < rawExpectedSource.length; i++) {
                            expectedSourceBuilder.add(rawExpectedSource[i]);
                        }
                        expectedSource = expectedSourceBuilder.toString();
                    } else {
                        return DynamicTest.dynamicTest(c.getName(), () -> {
                            throw new IllegalArgumentException("Either one of `expected` or `expectedFile` must be set!");
                        });
                    }

                    return DynamicTest.dynamicTest(c.getName(), () -> {
                        byte[] generatedBytecode = compileTestUtils
                                .compileWithAnnotationProcessor("Example", stubSource, (Processor) c.newInstance())
                                .orElseThrow(() -> new CompilerException("Example (with annotation processor)"))
                                .get("Example");
                        byte[] expectedBytecode = compileTestUtils
                                .compileWithAnnotationProcessor("Example", expectedSource, null)
                                .orElseThrow(() -> new CompilerException("Example (no annotation processor)"))
                                .get("Example");
                        Assertions.assertArrayEquals(stripDebugInfo(generatedBytecode), stripDebugInfo(expectedBytecode));
                    });
                }))
                .filter(Objects::nonNull);
    }

    private static byte[] stripDebugInfo(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(writer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

}
