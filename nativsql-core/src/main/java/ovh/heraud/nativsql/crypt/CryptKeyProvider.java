package ovh.heraud.nativsql.crypt;

/**
 * Provides the symmetric encryption key used by {@link CryptUtils}.
 *
 * <p>Implementations are resolved at mapper construction time:
 * first via the Spring {@code ApplicationContext} if available,
 * then via a no-arg constructor. If neither is available, a
 * {@link ovh.heraud.nativsql.exception.NativSQLException} is thrown.
 *
 * <p><b>Key lifetime:</b> {@code getKey()} is called once at {@link CryptConfig} construction
 * time. The returned {@code byte[]} is held in memory for the lifetime of the mapper.
 * For higher security, delegate to a KMS/HSM that never exports the raw key bytes.
 */
@FunctionalInterface
public interface CryptKeyProvider {

    /**
     * Returns the raw symmetric key bytes for AES encryption.
     * Must be 16, 24, or 32 bytes (AES-128, AES-192, or AES-256).
     *
     * @return the key bytes — never null
     */
    byte[] getKey();
}
