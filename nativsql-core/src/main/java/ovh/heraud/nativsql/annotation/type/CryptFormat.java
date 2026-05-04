package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Storage format for encrypted values.
 *
 * <ul>
 *   <li>{@link Format#STRING} (default) — Base64-encoded ciphertext stored in a VARCHAR column.</li>
 *   <li>{@link Format#BINARY} — raw cipher bytes stored in a VARBINARY/BLOB column.</li>
 * </ul>
 *
 * <p>Optional — defaults to {@link Format#STRING} if absent.
 *
 * <pre>
 * {@literal @}CryptFormat(CryptFormat.Format.BINARY)
 * </pre>
 */
@CryptParam(key = TypeParamKey.FORMAT)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptFormat {

    enum Format { STRING, BINARY }

    Format value() default Format.STRING;
}
