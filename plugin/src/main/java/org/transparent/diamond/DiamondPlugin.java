package org.transparent.diamond;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.Jvm;
import org.transparent.diamond.misc.PublishType;

import java.io.File;
import java.util.ArrayList;

/**
 * The main plugin class.
 *
 * @author Arc'blroth, Maow
 * @version %I
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class DiamondPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Add the Java plugin if it isn't already present.
        project.getPluginManager().apply(JavaPlugin.class);

        project.getExtensions().add(DiamondConfigExtension.class, "diamond", new DiamondConfigExtension());

        // Add PublishType to ext so that it doesn't have to be imported manually.
        // IntelliJ doesn't seem to like this, but I don't really care.
        project.getExtensions().getByType(ExtraPropertiesExtension.class).set("PublishType", PublishType.class);

        project.afterEvaluate(project2 -> {
            DiamondConfigExtension extension = project2.getExtensions().getByType(DiamondConfigExtension.class);
            JavaPluginConvention convention = project2.getConvention().getPlugin(JavaPluginConvention.class);

            if (extension.compatibility) {
                // On JDK <= 8, we need to add the JDK's 'tools.jar' to the classpath.
                File toolsJar = Jvm.current().getToolsJar();
                if (toolsJar != null && toolsJar.exists()) {
                    project.getDependencies().add("compileOnly", project.files(toolsJar));
                    project.getDependencies().add("testImplementation", project.files(toolsJar));
                }

                // On JDK >= 9, we also need to configure module information.
                // Since annotation processors are loaded by javac at runtime,
                // they are allowed to see jdk.compiler.
                if (convention.getSourceCompatibility().isJava9Compatible()) {
                    project2.getTasks().withType(JavaCompile.class).configureEach(task -> {
                        task.getOptions().getCompilerArgumentProviders().add(() -> {
                            ArrayList<String> list = new ArrayList<>();
                            for (String export : DiamondConstants.REQUIRED_EXPORTS) {
                                list.add("--add-exports");
                                list.add("jdk.compiler/" + export + "=ALL-UNNAMED");
                            }
                            return list;
                        });
                    });
                }
            }

            // Setup auto-publishing if enabled.
            if (extension.publishType != PublishType.NONE) {
                project2.getPluginManager().apply(MavenPublishPlugin.class);

                MavenPublication pub = project2.getExtensions().getByType(PublishingExtension.class)
                        .getPublications().create("mavenJava", MavenPublication.class);
                pub.from(project2.getComponents().getByName("java"));

                if (extension.publishType == PublishType.ALL) {
                    Jar sourcesJar = project2.getTasks().register("sourcesJar", Jar.class).get();
                    sourcesJar.from(convention.getSourceSets().getByName("main").getAllJava());
                    sourcesJar.getArchiveClassifier().set("sources");
                    pub.artifact(sourcesJar);
                }
            }
        });
    }

}
