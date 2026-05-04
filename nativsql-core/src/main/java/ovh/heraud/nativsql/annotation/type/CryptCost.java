package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cost factor for one-way hashing algorithms (e.g. BCRYPT work factor).
 *
 * <p>Optional — defaults to {@code 12} if absent.
 *
 * <pre>
 * {@literal @}CryptCost(14)
 * </pre>
 */
@CryptParam(key = TypeParamKey.COST)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptCost {
    int value() default 12;
}
