package ovh.heraud.nativsql.annotation;

/**
 * Enumeration of all database data types supported by NativSQL.
 * Used by the @Type annotation to specify the database type of a field.
 * When no @Type annotation is present, dataType is passed as null to the mapper.
 */
public enum DbDataType {
    /**
     * Identity type: marker used in switch statements to indicate invalid usage.
     * Should never be passed to mappers - null is used instead for default behavior.
     */
    IDENTITY,

    /**
     * Text/String type: VARCHAR, TEXT, etc.
     */
    STRING,

    /**
     * Integer types: INT, INTEGER, etc.
     */
    INTEGER,

    /**
     * Long integer type: BIGINT, LONG, etc.
     */
    LONG,

    /**
     * Short integer type: SMALLINT, SHORT, etc.
     */
    SHORT,

    /**
     * Byte integer type: TINYINT, BYTE, etc.
     */
    BYTE,

    /**
     * Floating point type: FLOAT, REAL, etc.
     */
    FLOAT,

    /**
     * Double precision floating point type: DOUBLE, DOUBLE PRECISION, etc.
     */
    DOUBLE,

    /**
     * Fixed-point decimal type: DECIMAL, NUMERIC, etc.
     */
    DECIMAL,

    /**
     * Large integer type: BIGINT (used for arbitrary precision), etc.
     */
    BIG_INTEGER,

    /**
     * Boolean/Logical type: BOOLEAN, BIT, etc.
     */
    BOOLEAN,

    /**
     * Date type: DATE, etc.
     */
    DATE,

    /**
     * Date and time type: TIMESTAMP, DATETIME, etc.
     */
    DATE_TIME,

    /**
     * Local date and time type: TIMESTAMP (without timezone), etc.
     * Specifically for LocalDateTime (Java 8 time API).
     */
    LOCAL_DATE_TIME,

    /**
     * UUID type: UUID, UNIQUEIDENTIFIER, etc.
     */
    UUID,

    /**
     * Binary data type: BYTEA, BLOB, etc.
     */
    BYTE_ARRAY,

    /**
     * Encrypted type: field is stored as an encrypted VARCHAR (STRING format) or VARBINARY (BINARY format).
     * Requires {@code @TypeParam(key=ALGO)} and {@code @TypeParam(key=KEY_PROVIDER)} on the {@code @Type} annotation,
     * plus {@code @TypeParam(key=PREFIX)} for reversible algorithms.
     * One-way algorithms (BCRYPT) do not require KEY_PROVIDER or PREFIX.
     *
     * <p>Note: entity fields must use boxed types ({@code Integer}, {@code Long}, …) — primitive types are not supported.
     */
    ENCRYPTED
}
