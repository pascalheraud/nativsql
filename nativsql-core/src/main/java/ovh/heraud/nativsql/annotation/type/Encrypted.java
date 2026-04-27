package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as stored encrypted in the database.
 *
 * <p>
 * Requires {@link CryptAlgo} and {@link CryptKeyProvider} on the field.
 * {@link CryptPrefix} is mandatory for reversible algorithms.
 * Use {@code @Type(DbDataType.BYTE_ARRAY)} for binary storage (defaults to STRING).
 *
 * <pre>
 * {@literal @}Encrypted
 * {@literal @}CryptAlgo(CryptAlgorithm.GCM)
 * {@literal @}CryptKeyProvider(MyKeyProvider.class)
 * {@literal @}CryptPrefix("{enc}")
 * private String email;
 * </pre>
 */
@TypeParam(key = TypeParamKey.ENCRYPTED)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encrypted {
    boolean value() default true;
}
