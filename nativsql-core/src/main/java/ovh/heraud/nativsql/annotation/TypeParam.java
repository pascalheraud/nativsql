package ovh.heraud.nativsql.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import ovh.heraud.nativsql.crypt.CryptAlgorithm;

/**
 * A parameter for a {@code @Type} annotation.
 * Used only as a nested element inside {@code @Type(params = {...})}.
 *
 * <p>Which attribute to use per key:
 * <ul>
 *   <li>{@code ALGO}         → {@code algoValue()} (compile-time safe enum reference)</li>
 *   <li>{@code KEY_PROVIDER} → {@code classValue()} (IDE refactoring-safe); {@code value()} (FQCN String) accepted as fallback</li>
 *   <li>{@code PREFIX}, {@code COST}, {@code FORMAT} → {@code value()}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * {@literal @}Type(value = DbDataType.ENCRYPTED, params = {
 *     {@literal @}TypeParam(key = TypeParamKey.ALGO,         algoValue = CryptAlgorithm.GCM),
 *     {@literal @}TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class),
 *     {@literal @}TypeParam(key = TypeParamKey.PREFIX,       value = "{ENC}")
 * })
 * private String email;
 * </pre>
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeParam {

    /**
     * The parameter key.
     */
    TypeParamKey key();

    /**
     * String value for the parameter (PREFIX, COST, FORMAT).
     * Not used for ALGO or KEY_PROVIDER (use {@code algoValue} or {@code classValue} instead).
     */
    String value() default "";

    /**
     * Algorithm value for {@code ALGO}.
     * Supports multiple values for cascade fallback on read:
     * {@code algoValue = {CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC}}.
     * Defaults to empty array (= not set).
     */
    CryptAlgorithm[] algoValue() default {};

    /**
     * Key provider class for {@code KEY_PROVIDER}.
     * Allows IDE refactoring (rename, find usages) to track the provider class.
     * Defaults to {@code Void.class} (= not set). Takes precedence over {@code value()} when set.
     * The referenced class must implement {@code CryptKeyProvider}.
     *
     * <p>Example: {@code @TypeParam(key = TypeParamKey.KEY_PROVIDER, keyProviderClass = MyCryptKeyProvider.class)}
     */
    Class<?> keyProviderClass() default Void.class;
}
