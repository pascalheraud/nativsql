package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import ovh.heraud.nativsql.crypt.CryptAlgorithm;

/**
 * Specifies the encryption algorithm(s) for a {@code @Type(DbDataType.ENCRYPTED)} field.
 *
 * <p>Multiple algorithms = cascade fallback on read: algorithms are tried in order
 * until one succeeds. Only the first is used on write.
 *
 * <p>Mandatory when {@code DbDataType.ENCRYPTED}.
 *
 * <pre>
 * {@literal @}Type(DbDataType.ENCRYPTED)
 * {@literal @}CryptAlgo(CryptAlgorithm.GCM)
 * private String email;
 *
 * // Cascade (migration from AES_CBC to GCM):
 * {@literal @}Type(DbDataType.ENCRYPTED)
 * {@literal @}CryptAlgo({CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC})
 * private String legacy;
 * </pre>
 */
@CryptParam(key = TypeParamKey.ALGO)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptAlgo {
    CryptAlgorithm[] value();
}
