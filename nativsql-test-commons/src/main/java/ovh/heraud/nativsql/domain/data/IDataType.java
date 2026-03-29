package ovh.heraud.nativsql.domain.data;

import ovh.heraud.nativsql.domain.IEntity;

/**
 * Combined interface for data type entities with both ID and data properties.
 * Extends both IEntity (for ID management) and IData (for data management).
 *
 * @param <T> the type of the data property
 */
public interface IDataType<T> extends IEntity<Long>, IData<T> {
}
