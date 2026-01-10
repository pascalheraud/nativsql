package io.github.pascalheraud.nativsql.exception;

/**
 * Runtime exception thrown when SQL operations fail.
 * Wraps underlying SQL and reflection exceptions for easier handling.
 */
public class SQLException extends RuntimeException {

    public SQLException(String message) {
        super(message);
    }

    public SQLException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQLException(Throwable cause) {
        super(cause);
    }
}
