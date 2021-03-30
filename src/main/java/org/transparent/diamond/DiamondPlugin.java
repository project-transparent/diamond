package org.transparent.diamond;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class DiamondPlugin implements Plugin<Project> {

    private static final String[] REQUIRED_EXPORTS = new String[] {
            "com.sun.source.doctree",
            "com.sun.source.tree",
            "com.sun.source.util",
            "com.sun.tools.javac",
            "com.sun.tools.javac.api",
            "com.sun.tools.javac.code",
            "com.sun.tools.javac.comp",
            "com.sun.tools.javac.file",
            "com.sun.tools.javac.jvm",
            "com.sun.tools.javac.main",
            "com.sun.tools.javac.model",
            "com.sun.tools.javac.nio",
            "com.sun.tools.javac.parser",
            "com.sun.tools.javac.processing",
            "com.sun.tools.javac.resources",
            "com.sun.tools.javac.services",
            "com.sun.tools.javac.sym",
            "com.sun.tools.javac.tree",
            "com.sun.tools.javac.util",
    };

    @Override
    public void apply(Project project) {
        // Require JavaPlugin
        project.getPluginManager().apply(JavaPlugin.class);

        // On JDK <= 8, we need to add `tools.jar` to the classpath
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null && toolsJar.exists()) {
            project.getDependencies().add("implementation", project.files(toolsJar));
        }

        // On JDK >= 9, we also need to configure module information.
        // Since annotation processors are loaded by javac at runtime,
        // they are allowed to see jdk.compiler
        project.afterEvaluate(project2 -> {
            if (project2.getConvention().getPlugin(JavaPluginConvention.class).getSourceCompatibility().isJava9Compatible()) {
                project2.getTasks().withType(JavaCompile.class).configureEach(task -> {
                    task.getOptions().getCompilerArgumentProviders().add(() -> {
                        ArrayList<String> list = new ArrayList<>();
                        for (String export : REQUIRED_EXPORTS) {
                            list.add("--add-exports");
                            list.add("jdk.compiler/" + export + "=ALL-UNNAMED");
                        }
                        return list;
                    });
                });
            }
        });
    }

}
