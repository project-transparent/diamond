package org.transparent.diamond.compiletest;

import com.sun.tools.javac.file.JavacFileManager;
import org.transparent.diamond.DiamondConstants;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.transparent.diamond.compiletest.LambdaUtils.rethrowChecked;

/**
 * Various compile test utils intended to compile stuff at runtime.
 */
public class CompileTestUtils {

    private static final DiagnosticListener<JavaFileObject> SYS_ERR_DIAGNOSTICS = (d) -> {
        System.err.println(d.toString());
    };
    private final JavaCompiler compiler;
    private final File sourceRoot;
    private final HashSet<File> classpath;

    /**
     * Builds an instance of CompileTestUtils that wraps the system Java compiler.
     *
     * @param sourceRoot Root for source code files.
     * @param classpath  Directories or JARs on the compile classpath. May be <code>null</code>.
     * @throws RuntimeException If URI decoding of the Diamond jar file location fails
     */
    public CompileTestUtils(File sourceRoot, Collection<File> classpath) {
        this.compiler = buildCompiler();
        this.sourceRoot = sourceRoot;
        this.classpath = new HashSet<>();
        if (classpath != null) {
            this.classpath.addAll(classpath);
        }
        this.classpath.add(locationOfJar(CompileTestUtils.class));
        if (!isJava9OrAbove()) {
            this.classpath.add(locationOfJar(this.compiler.getClass()));
        }
    }

    private static boolean isJava9OrAbove() {
        return !System.getProperty("java.version").startsWith("1.");
    }

    /**
     * Gets the Jar file that a class originates from.
     *
     * @param clazz Class
     * @return Location of the Jar file a class was loaded from, as recorded by its classloader.
     * @throws RuntimeException If URI conversion of the jar file location fails
     */
    public static File locationOfJar(Class<?> clazz) {
        try {
            return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not decode JAR location for " + clazz.getName(), e);
        }
    }

    /**
     * @return the system Java compiler
     */
    // based off of org.gradle.api.internal.tasks.compile.JdkTools#buildJavaCompiler
    public static JavaCompiler buildCompiler() {
        try {
            if (isJava9OrAbove()) {
                Class<?> toolProviderClass = Class.forName("javax.tools.ToolProvider");
                return (JavaCompiler) toolProviderClass.getDeclaredMethod("getSystemJavaCompiler").invoke(null);
            } else {
                Class<?> compilerClass = Class.forName("com.sun.tools.javac.api.JavacTool");
                return (JavaCompiler) compilerClass.newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not find compiler class!");
        }
    }

    /**
     * Compiles the given class using the system Java compiler.
     * The source code for the class (and any dependencies) will be
     * searched for under the {@link CompileTestUtils#sourceRoot} as
     * well as in the Diamond-CompileTest jar file.
     *
     * @param clazzName Name of the class to compile, with packages separated by dots.
     * @return If compilation succeeds, returns an Optional containing the generated bytecode
     * for all compiled classes. If compilation fails, returns {@link Optional#empty()}.
     * @throws IOException If an error occurs in reading source files or writing output files.
     */
    public Optional<Map<String, byte[]>> compile(String clazzName) throws IOException {
        return compileWithAnnotationProcessor(clazzName, null);
    }

    /**
     * Compiles the given class using the system Java compiler.
     * The source code for the class (and any dependencies) will be
     * searched for under the {@link CompileTestUtils#sourceRoot} as
     * well as in the Diamond-CompileTest jar file.
     *
     * @param clazzName Name of the class to compile, with packages separated by dots.
     * @param processor Annotation processor to use.
     * @return If compilation succeeds, returns an Optional containing the generated bytecode
     * for all compiled classes. If compilation fails, returns {@link Optional#empty()}.
     * @throws IOException If an error occurs in reading source files or writing output files.
     */
    public Optional<Map<String, byte[]>> compileWithAnnotationProcessor(String clazzName, Processor processor) throws IOException {
        return compileWithAnnotationProcessor(clazzName, null, null);
    }

    /**
     * Compiles the given class using the system Java compiler
     * with the given annotation processor.
     *
     * @param clazzName   Name of the class to compile, with packages separated by dots.
     * @param clazzSource Optional source of the class to compile. If null, the
     *                    source code for the class (and any dependencies) will be
     *                    searched for under the {@link CompileTestUtils#sourceRoot}.
     * @param processor   Annotation processor to use.
     * @return If compilation succeeds, returns an Optional containing the generated bytecode
     * for all compiled classes. If compilation fails, returns {@link Optional#empty()}.
     * @throws IOException If an error occurs in reading source files or writing output files.
     */
    public Optional<Map<String, byte[]>> compileWithAnnotationProcessor(String clazzName, String clazzSource, Processor processor) throws IOException {
        String outputPathName = Long.toString(System.currentTimeMillis());
        Path outputPath = Files.createTempDirectory(outputPathName);
        String outputClassPathBase = clazzName.replace(".", File.separator);
        try (JavacFileManager fileManager = (JavacFileManager) this.compiler.getStandardFileManager(
                SYS_ERR_DIAGNOSTICS,
                Locale.ROOT,
                StandardCharsets.UTF_8
        )) {
            ArrayList<File> finalClasspath = new ArrayList<>(this.classpath);
            finalClasspath.add(sourceRoot);
            finalClasspath.add(outputPath.toFile());
            fileManager.setLocation(StandardLocation.CLASS_PATH, finalClasspath);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(outputPath.toFile()));
            File inputFile;
            if (clazzSource == null) {
                inputFile = new File(sourceRoot, clazzName.replace('.', File.separatorChar) + ".java");
            } else {
                inputFile = new File(outputPath.toFile(), outputClassPathBase + ".java");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(inputFile))) {
                    writer.write(clazzSource);
                }
            }
            Path outputClassPath = outputPath.resolve(outputClassPathBase + ".class");
            ArrayList<String> options = new ArrayList<>();
            if (isJava9OrAbove()) {
                for (String export : DiamondConstants.REQUIRED_EXPORTS) {
                    options.add("--add-exports");
                    options.add("jdk.compiler/" + export + "=ALL-UNNAMED");
                }
            }
            List<String> classesForAnnotationProcessing = null;
            if (processor != null) {
                classesForAnnotationProcessing = Collections.singletonList(clazzName);
            }
            JavaCompiler.CompilationTask task = this.compiler.getTask(
                    new PrintWriter(System.out),
                    fileManager,
                    SYS_ERR_DIAGNOSTICS,
                    options,
                    classesForAnnotationProcessing,
                    Collections.singletonList(fileManager.getRegularFile(inputFile))
            );
            if (processor != null) {
                task.setProcessors(Collections.singletonList(processor));
            }
            try {
                if (task.call() && outputClassPath.toFile().exists()) {
                    HashMap<String, byte[]> out = new HashMap<>();
                    Files.walk(outputPath)
                            .filter(Files::exists)
                            .filter(Files::isRegularFile)
                            .forEach(rethrowChecked(p -> {
                                String className = outputPath
                                        .relativize(p)
                                        .toString()
                                        .replace(".class", "")
                                        .replace(File.separator, ".");
                                byte[] bytecode = Files.readAllBytes(p);
                                out.put(className, bytecode);
                            }));
                    return Optional.of(out);
                } else {
                    return Optional.empty();
                }
            } catch (Throwable t) {
                t.printStackTrace();
                return Optional.empty();
            }
        } finally {
            // clean up the temp directory
            Files.walk(outputPath)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

}
