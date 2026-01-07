package io.github.pascalheraud.nativsql.domain;

/**
 * Base interface for all entities with an identifier.
 *
 * @param <ID> the type of the entity's identifier
 */
public interface Entity<ID> {

    /**
     * Gets the entity's identifier.
     *
     * @return the identifier
     */
    ID getId();

    /**
     * Sets the entity's identifier.
     *
     * @param id the identifier to set
     */
    void setId(ID id);
}
