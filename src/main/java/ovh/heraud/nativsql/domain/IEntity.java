package ovh.heraud.nativsql.domain;

/**
 * Minimal interface for entities with an identifier.
 * Provides core ID management methods that all entities should implement.
 *
 * @param <ID> the type of the entity's identifier
 */
public interface IEntity<ID> {

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
