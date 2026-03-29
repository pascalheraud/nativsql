package ovh.heraud.nativsql.repository;

import java.lang.reflect.InvocationTargetException;

/**
 * Interface that declares common exceptions for test methods.
 * This helps reduce duplication of exception declarations in test method signatures.
 */
public interface TestErrors {
    // Exception classes for test methods
    Class<?>[] COMMON_TEST_EXCEPTIONS = {
        InstantiationException.class,
        IllegalAccessException.class,
        IllegalArgumentException.class,
        InvocationTargetException.class,
        NoSuchMethodException.class,
        SecurityException.class
    };
}
