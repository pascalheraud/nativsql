package ovh.heraud.nativsql.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A key/value parameter for a {@code @Type} annotation.
 * Used only as a nested element inside {@code @Type(params = {...})}.
 *
 * <p>The {@code value} is always a String; {@code AnnotationManager} interprets it
 * according to the key:
 * <ul>
 *   <li>{@code ALGO}         → enum name(s), comma-separated for cascade: {@code "GCM"} or {@code "GCM,AES_CBC"}</li>
 *   <li>{@code KEY_PROVIDER} → fully-qualified class name implementing {@code CryptKeyProvider}</li>
 *   <li>{@code PREFIX}       → string prefix, e.g. {@code "{ENC}"}</li>
 *   <li>{@code COST}         → integer string, e.g. {@code "12"}</li>
 *   <li>{@code FORMAT}       → {@code "STRING"} (default) or {@code "BINARY"}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * {@literal @}Type(value = DbDataType.ENCRYPTED, params = {
 *     {@literal @}TypeParam(key = TypeParamKey.ALGO,         value = "GCM"),
 *     {@literal @}TypeParam(key = TypeParamKey.KEY_PROVIDER, value = "com.example.MyCryptKeyProvider"),
 *     {@literal @}TypeParam(key = TypeParamKey.PREFIX,       value = "{ENC}")
 * })
 * private String email;
 * </pre>
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeParam {

    /** The parameter key. */
    TypeParamKey key();

    /**
     * String value for the parameter.
     * Interpreted by {@code AnnotationManager} based on the key (see class javadoc).
     * Mutually exclusive with {@link #classValue()}.
     */
    String value() default "";

    /**
     * Class value — alternative to {@link #value()} when the parameter holds a class reference.
     * Provides IDE refactoring support (rename, find usages).
     * Defaults to {@code Void.class} (= not set). Takes precedence over {@code value()} when set.
     *
     * <p>Primary use: {@code KEY_PROVIDER} — the class must implement {@code CryptKeyProvider}.
     * <pre>
     * {@literal @}TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class)
     * </pre>
     */
    Class<?> classValue() default Void.class;
}
