package io.github.pascalheraud.nativsql.domain;

/**
 * User status enum that maps to PostgreSQL ENUM type.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}