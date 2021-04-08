package org.transparent.diamond.misc.annotation;

import java.lang.annotation.*;

/**
 * This annotation serves as metadata to illustrate
 * who should be setting a configuration property.
 *
 * @author Maow
 * @version %I
 * @since 1.1.0
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface Usage {
    /**
     * @return an enum representing the target user of this property
     */
    Target value();

    enum Target {
        /**
         * This property should be set by any user of the plugin.
         */
        ALL,

        /**
         * This property is designed to be set by only Project Transparent contributors.
         */
        TRANSPARENT
    }
}
