package ovh.heraud.nativsql.exception;

/**
 * Checked exception thrown by {@code fromValue()} implementations when a value
 * cannot be converted to the target Java type.
 *
 * <p>Caught exclusively by {@code AbstractTypeMapper.map()}, which produces the
 * final {@link NativSQLException} with full context (column name, value, source
 * class). Values are masked as {@code #######} for encrypted fields.
 */
public class ConversionException extends Exception {

    private final String targetName;

    public ConversionException(Class<?> targetType) {
        super();
        this.targetName = targetType.getSimpleName();
    }

    public ConversionException(Class<?> targetType, Throwable cause) {
        super(cause);
        this.targetName = targetType.getSimpleName();
    }

    public ConversionException(String targetName) {
        super();
        this.targetName = targetName;
    }

    public String getTargetName() {
        return targetName;
    }
}
