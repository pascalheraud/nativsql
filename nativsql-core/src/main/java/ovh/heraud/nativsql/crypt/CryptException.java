package ovh.heraud.nativsql.crypt;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Exception thrown when encryption or decryption fails.
 * Extends {@link NativSQLException} so callers can catch either.
 * Carries a {@link CryptErrorCode} to allow programmatic handling.
 *
 * <p>Messages include column name and algorithm — never a raw or decrypted value.
 */
public class CryptException extends NativSQLException {

    private final CryptErrorCode code;

    public CryptException(CryptErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CryptException(CryptErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public CryptErrorCode getCode() {
        return code;
    }
}
