package ovh.heraud.nativsql.domain.postgres;

import ovh.heraud.nativsql.annotation.type.SqlType;

/**
 * User status enum that maps to PostgreSQL ENUM type.
 */
@SqlType("user_status")
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}