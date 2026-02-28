package ovh.heraud.nativsql.repository;

/**
 * Custom runtime exception that wraps common test-related exceptions.
 * This exception is used to simplify exception handling in test methods.
 */
public class TestError extends RuntimeException {
    
    public TestError(String message) {
        super(message);
    }
    
    public TestError(String message, Throwable cause) {
        super(message, cause);
    }
    
    public TestError(Throwable cause) {
        super(cause);
    }
}
