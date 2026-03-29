package ovh.heraud.nativsql.domain.mysql;

import ovh.heraud.nativsql.annotation.EnumMapping;

/**
 * Contact type enum that maps to database ENUM type.
 */
@EnumMapping(typeName = "contact_type")
public enum ContactType {
    EMAIL,
    PHONE,
    FACEBOOK,
    TWITTER,
    LINKEDIN,
    WEBSITE
}
