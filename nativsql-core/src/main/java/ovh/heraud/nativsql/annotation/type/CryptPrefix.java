package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prepends a fixed string to the encrypted value before storing it in the database.
 *
 * <p>Serves as both a format marker and a migration discriminator: on write,
 * stored value = prefix + Base64(ciphertext); on read, if value starts with prefix
 * → strip and decrypt, otherwise → return as-is (not yet migrated row).
 *
 * <p>Mandatory for reversible algorithms. Not applicable for one-way algorithms.
 *
 * <pre>
 * {@literal @}CryptPrefix("{ENC}")
 * </pre>
 */
@CryptParam(key = TypeParamKey.PREFIX)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptPrefix {
    String value();
}
