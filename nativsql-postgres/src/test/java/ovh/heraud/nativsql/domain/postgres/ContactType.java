package ovh.heraud.nativsql.domain.postgres;

import ovh.heraud.nativsql.annotation.type.SqlType;

/**
 * Contact type enum that maps to database ENUM type.
 */
@SqlType("contact_type")
public enum ContactType {
    EMAIL,
    PHONE,
    FACEBOOK,
    TWITTER,
    LINKEDIN,
    WEBSITE
}
