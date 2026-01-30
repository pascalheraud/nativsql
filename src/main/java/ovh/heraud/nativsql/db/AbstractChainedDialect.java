package ovh.heraud.nativsql.db;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ovh.heraud.nativsql.mapper.ITypeMapper;

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
    protected final Map<Class<?>, String> jsonTypes = new HashMap<>();
    protected final Map<Class<?>, String> enumDbTypes = new ConcurrentHashMap<>();
    protected final Map<Class<?>, String> compositeTypes = new ConcurrentHashMap<>();

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
     * Register a JSON type for this dialect.
     * The registration is propagated through the dialect chain.
     *
     * @param jsonClass the JSON class to register
     * @param <T>       the type
     */
    @Override
    public <T> void registerJsonType(Class<T> jsonClass) {
        jsonTypes.putIfAbsent(jsonClass, jsonClass.getSimpleName());
        if (nextDialect != null) {
            nextDialect.registerJsonType(jsonClass);
        }
    }

    /**
     * Register an enum type for this dialect.
     * The registration is propagated through the dialect chain.
     *
     * @param enumClass  the enum class to register
     * @param dbTypeName the database type name for this enum
     * @param <E>        the enum type
     */
    @Override
    public <E extends Enum<E>> void registerEnumType(Class<E> enumClass, String dbTypeName) {
        enumDbTypes.put(enumClass, dbTypeName);
        if (nextDialect != null) {
            nextDialect.registerEnumType(enumClass, dbTypeName);
        }
    }

    /**
     * Register a composite type for this dialect.
     * The registration is propagated through the dialect chain.
     *
     * @param compositeClass the composite class to register
     * @param dbTypeName     the database type name for this composite
     * @param <T>            the type
     */
    @Override
    public <T> void registerCompositeType(Class<T> compositeClass, String dbTypeName) {
        compositeTypes.put(compositeClass, dbTypeName);
        if (nextDialect != null) {
            nextDialect.registerCompositeType(compositeClass, dbTypeName);
        }
    }

    /**
     * Gets the appropriate TypeMapper for the given class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific type mappings.
     *
     * @param targetType the type to get a mapper for
     * @return a TypeMapper for the type, or null if not found
     * @param <T> the type
     */
    @Override
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        if (nextDialect != null) {
            return nextDialect.getMapper(targetType);
        }
        return null;
    }

    /**
     * Gets an enum mapper for the specified enum class.
     * Default implementation delegates to the next dialect in the chain.
     * Subclasses can override to provide dialect-specific enum mapping.
     *
     * @param enumClass the enum class
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass) {
        if (nextDialect != null) {
            return nextDialect.getEnumMapper(enumClass);
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
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass) {
        if (nextDialect != null) {
            return nextDialect.getCompositeMapper(compositeClass);
        }
        return null;
    }
}
