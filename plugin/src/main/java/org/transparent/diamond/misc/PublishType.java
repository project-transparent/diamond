package org.transparent.diamond.misc;

/**
 * Represents what files should be added to automatic local Maven publishing.
 *
 * @author Maow
 * @version %I
 * @since 1.1.0
 */
public enum PublishType {
    /**
     * Publish nothing.
     */
    NONE,
    /**
     * Publish classes but skip sources.
     */
    CLASSES,
    /**
     * Publish classes and sources.
     */
    ALL
}
