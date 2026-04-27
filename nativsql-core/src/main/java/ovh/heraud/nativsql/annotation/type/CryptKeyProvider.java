package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the {@link ovh.heraud.nativsql.crypt.CryptKeyProvider} class for an
 * {@code @Encrypted} field.
 *
 * <p>
 * The class is resolved at startup: if it is a Spring bean, it is fetched from
 * the application context; otherwise instantiated via its no-arg constructor.
 *
 * <p>
 * Mandatory for reversible algorithms. Not required for one-way algorithms
 * (e.g. BCRYPT).
 *
 * <pre>
 * {@literal @}Encrypted
 * {@literal @}CryptAlgo(CryptAlgorithm.GCM)
 * {@literal @}CryptKeyProvider(MyCryptKeyProvider.class)
 * private String email;
 * </pre>
 */
@TypeParam(key = TypeParamKey.KEY_PROVIDER)
@Inject
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CryptKeyProvider {
    Class<? extends ovh.heraud.nativsql.crypt.CryptKeyProvider> value();
}
