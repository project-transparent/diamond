package org.transparent.diamond.compiletest;

import java.security.SecureClassLoader;
import java.util.Collections;
import java.util.Map;

/**
 * A classloader that allows specifying the bytecode of some classes.
 */
public class AnnotationProcessorClassLoader extends SecureClassLoader {

    static {
        registerAsParallelCapable();
    }

    private final Map<String, byte[]> classes;

    /**
     * Builds an AnnotationProcessorClassLoader from a map of classes.
     */
    public AnnotationProcessorClassLoader(Map<String, byte[]> classes) {
        this.classes = Collections.unmodifiableMap(classes);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (classes.containsKey(name)) {
                    return findClass(name);
                } else {
                    return super.loadClass(name, resolve);
                }
            } else {
                return c;
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (classes.containsKey(name)) {
            byte[] bytecode = classes.get(name);
            return this.defineClass(name, bytecode, 0, bytecode.length);
        } else {
            throw new ClassNotFoundException();
        }
    }

}
