package ovh.heraud.nativsql.domain.postgres;

import ovh.heraud.nativsql.annotation.EnumMapping;

/**
 * User status enum that maps to PostgreSQL ENUM type.
 */
@EnumMapping(typeName = "user_status")
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}