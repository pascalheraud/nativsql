package io.github.pascalheraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.lang.NonNull;

/**
 * Annotation to mark a field as a one-to-many relationship.
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * @Data
 * public class User implements Entity<Long> {
 *     private Long id;
 *
 *     @OneToMany(mappedBy = "userId", repository = ContactInfoRepository.class)
 *     private List<ContactInfo> contacts;
 * }
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToMany {

    /**
     * The field name in the target entity that references this entity's ID.
     *
     * @return the foreign key field name (e.g., "userId")
     */
    @NonNull
    String mappedBy();

    /**
     * The repository class to use for loading the associated entities.
     *
     * @return the repository class
     */
    @NonNull
    Class<?> repository();
}
