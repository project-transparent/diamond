package org.transparent.diamond;

import org.transparent.diamond.misc.PublishType;
import org.transparent.diamond.misc.annotation.Usage;
import org.transparent.diamond.misc.annotation.Usage.Target;

/**
 * Contains all configurations for the plugin.
 *
 * @author Arc'blroth, Maow
 * @version %I
 * @since 1.0.0
 */
public class DiamondConfigExtension {
    /**
     * Automatically generate a Maven publication that consists of
     * the JAR(s) generated by the <code>java</code> component and a source code JAR.
     */
    @Usage(Target.TRANSPARENT)
    public PublishType publishType = PublishType.NONE;

    /**
     * Automatically adds all required exports or dependencies for cross-version compatibility.
     * <p>
     * This is the main purpose of the plugin, but can be disabled if it isn't required.
     */
    @Usage(Target.ALL)
    public boolean compatibility = true;
}