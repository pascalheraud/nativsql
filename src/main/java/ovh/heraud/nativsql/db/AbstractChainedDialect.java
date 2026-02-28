package ovh.heraud.nativsql.db;

import java.util.Map;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Abstract base class implementing the Chain of Responsibility pattern for
 * database dialects.
 *
 * Provides the chain mechanism that allows specialized dialects to delegate to
 * a next dialect
 * if they cannot handle a specific type.
 *
 * Also provides common type registration methods for enums, composite types,
 * and JSON types.
 * Subclasses can leverage these registrations or provide their own
 * implementations.
 *
 * Example hierarchy:
 * - PostgresPostGISDialect (handles Point types) -> PostgresDialect (handles
 * UUID, enums) -> GenericDialect (handles basic types)
 */
public abstract class AbstractChainedDialect implements DatabaseDialect {

    protected DatabaseDialect nextDialect;

    /**
     * Create a chained dialect with a next dialect to delegate to.
     *
     * @param nextDialect the next dialect in the chain, null if this is the end of
     *                    the chain
     */
    public AbstractChainedDialect(DatabaseDialect nextDialect) {
        this.nextDialect = nextDialect;
    }

    /**
     * Create a chained dialect with no next dialect (end of chain).
     */
    public AbstractChainedDialect() {
        this.nextDialect = null;
    }


    /**
     * Gets the appropriate TypeMapper for the given class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific type mappings.
     *
     * @param fieldAccessor the field accessor for the type to get a mapper for
     * @param annotationManager the annotation manager for type detection
     * @return a TypeMapper for the type, or null if not found
     * @param <T> the type
     */
    @Override
    public <T> ITypeMapper<T> getMapper(FieldAccessor fieldAccessor, AnnotationManager annotationManager) {
        if (nextDialect != null) {
            return nextDialect.getMapper(fieldAccessor, annotationManager);
        }
        return null;
    }

    /**
     * Gets an enum mapper for the specified enum class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific enum mapping.
     *
     * @param enumClass the enum class
     * @param annotationManager the annotation manager for type detection
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager) {
        if (nextDialect != null) {
            return nextDialect.getEnumMapper(enumClass, annotationManager);
        }
        return null;
    }

    /**
     * Gets a JSON mapper for the specified JSON class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific JSON mapping.
     *
     * @param jsonClass the JSON class
     * @return a mapper for JSON
     * @param <T> the type
     */
    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        if (nextDialect != null) {
            return nextDialect.getJsonMapper(jsonClass);
        }
        return null;
    }

    /**
     * Gets a composite mapper for the specified composite class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific composite mapping.
     *
     * @param compositeClass the composite class
     * @return a mapper for the composite
     * @param <T> the type
     */
    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager) {
        if (nextDialect != null) {
            return nextDialect.getCompositeMapper(compositeClass, annotationManager);
        }
        return null;
    }

    @Override
    public <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn) {
        if (nextDialect != null) {
            return nextDialect.getGeneratedKey(keys, idColumn);
        }
        return (ID) keys.get(idColumn);
    }
}
