package ovh.heraud.nativsql.util;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.DbDataType;

/**
 * Contains information about a field type extracted from the @Type annotation.
 */
public class TypeInfo {
    private final @NonNull DbDataType dataType;

    /**
     * Creates a new TypeInfo.
     *
     * @param dataType the database data type for this field
     */
    public TypeInfo(@NonNull DbDataType dataType) {
        this.dataType = dataType;
    }

    /**
     * Gets the database data type.
     *
     * @return the database data type
     */
    @NonNull
    public DbDataType getDataType() {
        return dataType;
    }
}
