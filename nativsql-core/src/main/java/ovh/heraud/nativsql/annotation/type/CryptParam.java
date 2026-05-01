package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation marking a field annotation as a crypt parameter.
 * {@code AnnotationManager} scans field annotations, finds those carrying this
 * meta-annotation, reads their {@code value()} via reflection, and stores the
 * result in {@code TypeInfo}'s params map under the declared {@link TypeParamKey}.
 *
 * <p>Adding a new crypt param annotation requires no change to {@code AnnotationManager}:
 * just declare the annotation, annotate it with {@code @CryptParam}, and it is
 * picked up automatically.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptParam {
    TypeParamKey key();
}
