package ovh.heraud.nativsql.exception;

/**
 * Runtime exception thrown when SQL operations fail.
 * Wraps underlying SQL and reflection exceptions for easier handling.
 */
public class NativSQLException extends RuntimeException {

    public NativSQLException(String message) {
        super(message);
    }

    public NativSQLException(String message, Throwable cause) {
        super(message, cause);
    }

    public NativSQLException(Throwable cause) {
        super(cause);
    }
}
