package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that marks a {@link CryptParam} annotation whose {@code value()}
 * is a {@code Class<?>} that must be resolved to an instance at annotation-read time.
 *
 * <p>When {@code AnnotationManager} encounters a field annotation carrying both
 * {@link CryptParam} and {@code @Inject}, it resolves the {@code Class<?>} returned
 * by {@code value()} to an object instance:
 * <ol>
 *   <li>If a Spring {@code ApplicationContext} is available, fetches the bean from it.</li>
 *   <li>Otherwise, instantiates via the no-arg constructor.</li>
 * </ol>
 * The resolved instance replaces the raw {@code Class<?>} in the {@code TypeInfo} params map.
 *
 * <p>This mechanism is generic — any future {@code @CryptParam} annotation that holds a
 * class reference requiring Spring injection only needs to be annotated with {@code @Inject};
 * no change to {@code AnnotationManager} is needed.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inject {
}
