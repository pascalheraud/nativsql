package ovh.heraud.nativsql.util;

import org.jspecify.annotations.NonNull;

/**
 * Represents the details of an EnumMapping annotation for database enum types.
 */
public class EnumMappingInfo {
    private final @NonNull String typeName;

    /**
     * Creates a new EnumMappingInfo.
     *
     * @param typeName the database enum type name
     */
    public EnumMappingInfo(@NonNull String typeName) {
        this.typeName = typeName;
    }

    /**
     * Gets the database enum type name.
     *
     * @return the database type name (e.g., "contact_type")
     */
    @NonNull
    public String getTypeName() {
        return typeName;
    }
}
