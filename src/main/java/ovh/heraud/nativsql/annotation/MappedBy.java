package ovh.heraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.lang.NonNull;

/**
 * Annotation to specify the foreign key property and repository for a jointure.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MappedBy {

    /**
     * The foreign key property name that references the joined object.
     *
     * @return the foreign key property name (e.g., "groupId")
     */
    @NonNull
    String value();

    /**
     * The repository class for the joined entity.
     *
     * @return the repository class
     */
    @NonNull
    Class<?> repository();
}
