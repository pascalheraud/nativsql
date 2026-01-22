package ovh.heraud.nativsql.domain.postgres;

import ovh.heraud.nativsql.annotation.EnumMapping;

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
