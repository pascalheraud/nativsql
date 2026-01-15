package io.github.pascalheraud.nativsql.domain.postgres;

import io.github.pascalheraud.nativsql.annotation.EnumMapping;

/**
 * Contact type enum that maps to PostgreSQL ENUM type.
 */
@EnumMapping(pgTypeName = "contact_type")
public enum ContactType {
    EMAIL,
    PHONE,
    FACEBOOK,
    TWITTER,
    LINKEDIN,
    WEBSITE
}
