package io.github.pascalheraud.nativsql.config;

import io.github.pascalheraud.nativsql.annotation.EnumMapping;
import io.github.pascalheraud.nativsql.mapper.INativSQLMapper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Scans the classpath for enum types annotated with {@link EnumMapping}
 * and automatically registers them with the NativSQL type mapper.
 */
public class EnumMappingScanner {

    private static final Logger logger = LoggerFactory.getLogger(EnumMappingScanner.class);

    /**
     * Scans the specified base packages for enum types annotated with {@link EnumMapping}
     * and registers them with the mapper.
     *
     * @param mapper the INativSQLMapper to register enum types with
     * @param basePackages the base packages to scan
     */
    public static void scanAndRegister(INativSQLMapper mapper, String... basePackages) {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(EnumMapping.class));

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition candidate : candidates) {
                try {
                    Class<?> clazz = Class.forName(candidate.getBeanClassName());

                    if (clazz.isEnum()) {
                        EnumMapping annotation = clazz.getAnnotation(EnumMapping.class);
                        if (annotation != null) {
                            String dbTypeName = annotation.pgTypeName();
                            registerEnum(mapper, clazz, dbTypeName);

                            logger.info("Registered enum type {} with database type name '{}'",
                                clazz.getSimpleName(), dbTypeName);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    logger.error("Failed to load class: {}", candidate.getBeanClassName(), e);
                }
            }
        }
    }

    /**
     * Helper method to register an enum type with proper generic type handling.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void registerEnum(INativSQLMapper mapper, Class<?> enumClass, String pgTypeName) {
        mapper.registerEnumType((Class<E>) enumClass, pgTypeName);
    }
}
