package ovh.heraud.nativsql.db.oracle;

import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.oracle.mapper.OracleUUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Oracle-specific implementation of DatabaseDialect.
 *
 * Handles Oracle-specific SQL formatting and type conversions including:
 * - JSON types via CLOB/VARCHAR2 (Jackson serialization)
 * - Enum types as VARCHAR2 (uses generic behavior)
 * - Composite types not supported (Oracle doesn't have native composite types)
 * - Generated keys extracted from OJDBC KeyHolder using uppercase column names
 *
 * Part of the Chain of Responsibility pattern, chains to GenericDialect for unmapped types.
 */
public class OracleDialect extends AbstractChainedDialect {

    /**
     * Create an Oracle dialect that chains to the generic dialect.
     *
     * @param nextDialect the next dialect to delegate to
     */
    public OracleDialect(DatabaseDialect nextDialect) {
        super(nextDialect);
    }

    /**
     * Create an Oracle dialect with a generic dialect as the next in chain.
     */
    public OracleDialect() {
        super(new GenericDialect());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ITypeMapper<T> getMapper(FieldAccessor<T> fieldAccessor, AnnotationManager annotationManager) {
        // Check for UUID types (handle oracle.sql.BLOB)
        if (fieldAccessor.getType() == UUID.class) {
            return (ITypeMapper<T>) new OracleUUIDTypeMapper();
        }

        // Check for JSON types via AnnotationManager
        if (annotationManager != null && annotationManager.getJsonInfo(fieldAccessor.getType()) != null) {
            return (ITypeMapper<T>) getJsonMapper(fieldAccessor.getType());
        }

        // Fall back to next dialect in chain for other types
        return super.getMapper(fieldAccessor, annotationManager);
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager) {
        // Oracle does not support native composite types
        throw new UnsupportedOperationException(
                "Oracle does not support native composite types. " +
                        "Use JSON columns instead for composite data: " + compositeClass.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn) {
        // Oracle JDBC returns column names in uppercase in KeyHolder.getKeys()
        Object value = keys.get(idColumn.toUpperCase());
        if (value == null) {
            // Fallback to the exact column name if uppercase doesn't work
            value = keys.get(idColumn);
        }
        return (ID) value;
    }
}
