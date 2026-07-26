package ovh.heraud.nativsql.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import ovh.heraud.nativsql.annotation.type.Inject;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.SqlType;
import ovh.heraud.nativsql.annotation.type.Type;
import ovh.heraud.nativsql.annotation.type.TypeParam;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.crypt.CryptKeyProvider;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.CompositeTypeInfo;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.MappedByInfo;
import ovh.heraud.nativsql.util.OneToManyAssociation;
import ovh.heraud.nativsql.util.ComputedFieldInfo;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.TypeInfo;
import ovh.heraud.nativsql.util.ComputedValueProvider;

/**
 * Centralized component for managing and retrieving annotations from entity
 * classes.
 * This component encapsulates all annotation-related operations, providing a
 * single
 * point of access for annotation metadata extraction.
 *
 * Rather than returning raw annotation objects, this manager returns
 * domain-specific
 * information classes (e.g., MappedByInfo, OneToManyAssociation) that contain
 * the extracted and processed annotation data.
 *
 * Caches results of annotation introspection for performance optimization.
 */
@Component
public class AnnotationManager {

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    private final Map<FieldKey, MappedByInfo> mappedByCache = new ConcurrentHashMap<>();
    private final Map<FieldKey, OneToManyAssociation> oneToManyCache = new ConcurrentHashMap<>();
    private final Set<Class<?>> jsonClassCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Class<?>, CompositeTypeInfo> compositeTypeCache = new ConcurrentHashMap<>();
    private final Map<FieldKey, TypeInfo> typeCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<ComputedFieldInfo>> onUpdateCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<ComputedFieldInfo>> onInsertCache = new ConcurrentHashMap<>();

    /**
     * Creates a FieldKey from a FieldAccessor.
     *
     * @param fieldAccessor the field accessor
     * @return the field key representing the field
     */
    private FieldKey createFieldKey(FieldAccessor<?> fieldAccessor) {
        return new FieldKey(fieldAccessor.getDeclaringClass(), fieldAccessor.getName());
    }

    /**
     * Retrieves MappedBy association information from a field.
     * Returns a MappedByInfo object containing the foreign key property and
     * repository class.
     * Result is cached for subsequent calls.
     *
     * @param fieldAccessor the field accessor to inspect
     * @return MappedByInfo if @MappedBy is present, null otherwise
     */

    public MappedByInfo getMappedByInfo(FieldAccessor<?> fieldAccessor) {
        FieldKey key = createFieldKey(fieldAccessor);
        return mappedByCache.computeIfAbsent(key, k -> {
            MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
            if (mappedBy == null) {
                return null;
            }
            return new MappedByInfo(mappedBy.value(), mappedBy.repository());
        });
    }

    /**
     * Retrieves OneToMany association information from a field.
     * Returns a OneToManyAssociation object containing the foreign key and
     * repository class.
     * Result is cached for subsequent calls.
     *
     * @param fieldAccessor the field accessor to inspect
     * @return OneToManyAssociation if @OneToMany is present, null otherwise
     */

    public OneToManyAssociation getOneToManyInfo(FieldAccessor<?> fieldAccessor) {
        FieldKey key = createFieldKey(fieldAccessor);
        return oneToManyCache.computeIfAbsent(key, k -> {
            OneToMany oneToMany = fieldAccessor.getAnnotation(OneToMany.class);
            if (oneToMany == null) {
                return null;
            }
            return new OneToManyAssociation(oneToMany.mappedBy(), oneToMany.repository());
        });
    }

    /**
     * Retrieves Json annotation information from a class.
     * Returns a JsonInfo object indicating a class is marked as a JSON type.
     * Result is cached for subsequent calls.
     *
     * @param jsonClass the class to inspect
     * @return JsonInfo if @Json is present, null otherwise
     */

    /**
     * Retrieves CompositeType annotation information from a class.
     * The class must carry {@link CompositeType} as marker and
     * {@link ovh.heraud.nativsql.annotation.type.SqlType}
     * for the type name.
     * Result is cached for subsequent calls.
     *
     * @param compositeClass the composite class to inspect
     * @return CompositeTypeInfo if @CompositeType is present, null otherwise
     */

    private CompositeTypeInfo getCompositeTypeInfo(Class<?> compositeClass) {
        return computeIfAbsent(compositeTypeCache, compositeClass, clazz -> {
            if (getAnnotation(clazz, CompositeType.class) == null) {
                return null;
            }
            SqlType sqlType = getAnnotation(clazz, SqlType.class);
            return new CompositeTypeInfo(sqlType != null ? sqlType.value() : null);
        });
    }

    public interface Extractor<T> {

        T extract(Class<?> clazz);
    }

    private <T> T computeIfAbsent(Map<Class<?>, T> cache, Class<?> clazz,
            Extractor<T> extractor) {
        return cache.computeIfAbsent(clazz, k -> {
            return extractor.extract(clazz);
        });
    }

    private <B extends Annotation> B getAnnotation(Class<?> clazz,
            Class<B> annotationClass) {
        return clazz.getAnnotation(annotationClass);
    }

    /**
     * Retrieves type parameters for a field from its annotations.
     * Scans all {@link TypeParam}-carrying annotations (encryption params, etc.)
     * and adds {@code DB_DATA_TYPE} from {@code @Type} if present.
     * For encrypted fields without {@code @Type}, defaults to {@code STRING}.
     * Returns an empty {@code TypeInfo} for plain unannotated fields.
     * Result is cached for subsequent calls.
     *
     * @param fieldAccessor the field accessor to inspect
     * @return TypeInfo for the field, never null
     */
    public TypeInfo getTypeInfo(FieldAccessor<?> fieldAccessor) {
        FieldKey key = createFieldKey(fieldAccessor);
        return typeCache.computeIfAbsent(key, k -> {
            return getTypeInfoValue(fieldAccessor);
        });
    }

    private TypeInfo getTypeInfoValue(FieldAccessor<?> fieldAccessor) {
        Map<ParamKey, Object> params = new HashMap<>(scanCryptParams(fieldAccessor));
        Type type = fieldAccessor.getAnnotation(Type.class);
        if (type != null) {
            params.put(TypeParamKey.DB_DATA_TYPE, type.value());
        }

        // Pull JSON flag from field annotation or class annotation
        Class<?> fieldType = fieldAccessor.getType();
        if (fieldAccessor.getAnnotation(Json.class) != null
                || getAnnotation(fieldType, Json.class) != null
                || jsonClassCache.contains(fieldType)) {
            params.put(TypeParamKey.JSON, Boolean.TRUE);
        }

        // Pull SQL_TYPE from class-level annotation into the field's params
        if (fieldType.isEnum()) {
            String classSqlType = getEnumSqlType(fieldType);
            if (classSqlType != null) {
                if (params.containsKey(TypeParamKey.SQL_TYPE)) {
                    Object existing = params.get(TypeParamKey.SQL_TYPE);
                    if (!classSqlType.equals(existing)) {
                        throw new NativSQLException("Conflict: field "
                                + fieldAccessor.getDeclaringClass().getSimpleName() + "."
                                + fieldAccessor.getName()
                                + " has SQL_TYPE=" + existing
                                + " but class-level annotation says " + classSqlType);
                    }
                } else {
                    params.put(TypeParamKey.SQL_TYPE, classSqlType);
                }
            }
        } else {
            CompositeTypeInfo compositeTypeInfo = getCompositeTypeInfo(fieldType);
            if (compositeTypeInfo != null) {
                params.put(TypeParamKey.COMPOSITE, Boolean.TRUE);
                String classSqlType = compositeTypeInfo.getSqlType();
                if (classSqlType != null) {
                    if (params.containsKey(TypeParamKey.SQL_TYPE)) {
                        Object existing = params.get(TypeParamKey.SQL_TYPE);
                        if (!classSqlType.equals(existing)) {
                            throw new NativSQLException("Conflict: field "
                                    + fieldAccessor.getDeclaringClass().getSimpleName() + "."
                                    + fieldAccessor.getName()
                                    + " has SQL_TYPE=" + existing
                                    + " but class-level annotation says " + classSqlType);
                        }
                    } else {
                        params.put(TypeParamKey.SQL_TYPE, classSqlType);
                    }
                }
            }
        }

        if (params.isEmpty()) {
            return new TypeInfo();
        }
        if (params.containsKey(TypeParamKey.ENCRYPTED)) {
            DbDataType dbDataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
            if (dbDataType == null) {
                params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.STRING);
            } else if (dbDataType != DbDataType.STRING && dbDataType != DbDataType.BYTE_ARRAY) {
                throw new NativSQLException("Encrypted field '" + fieldAccessor.getName()
                        + "': @Type must be STRING or BYTE_ARRAY, got " + dbDataType);
            }
        }
        return new TypeInfo(params);
    }

    /**
     * Scans the field's annotations for any annotation carrying {@link TypeParam},
     * reads its {@code value()} via reflection, and returns the collected params.
     *
     * <p>
     * If the annotation also carries {@link Inject}, the {@code Class<?>} returned
     * by
     * {@code value()} is resolved to an instance (Spring context first, then no-arg
     * constructor).
     */
    private Map<ParamKey, Object> scanCryptParams(FieldAccessor<?> fieldAccessor) {
        Map<ParamKey, Object> params = new HashMap<>();
        for (java.lang.annotation.Annotation annotation : fieldAccessor.getAnnotations()) {
            TypeParam meta = annotation.annotationType().getAnnotation(TypeParam.class);
            if (meta != null) {
                Object value = ReflectionUtils.readAnnotationValue(annotation);
                if (annotation.annotationType().isAnnotationPresent(Inject.class)
                        && value instanceof Class<?> cls) {
                    value = resolveBean(cls, fieldAccessor.getName());
                }
                params.put(meta.key(), value);
            }
        }
        return params;
    }

    /**
     * Resolves a class to an instance: Spring context first, then no-arg
     * constructor.
     */
    private Object resolveBean(Class<?> cls, String fieldName) {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(cls);
            } catch (Exception ignored) {
                // not a Spring bean — fall through
            }
        }
        return ReflectionUtils.instantiate(cls, fieldName);
    }

    /**
     * Retrieves the {@code @OnUpdate}-annotated fields of an entity class, each
     * resolved to its {@link ComputedValueProvider} instance (Spring bean first,
     * then no-arg constructor).
     * Result is cached for subsequent calls; an empty list means no
     * {@code @OnUpdate} field is present.
     *
     * @param entityClass the entity class to inspect
     * @return the list of {@link ComputedFieldInfo}, never null, possibly empty
     */
    public List<ComputedFieldInfo> getComputedFieldInfos(Class<?> entityClass) {
        return onUpdateCache.computeIfAbsent(entityClass, cls -> {
            List<ComputedFieldInfo> infos = new ArrayList<>();
            for (FieldAccessor<?> fa : ReflectionUtils.getFields(cls).list()) {
                OnUpdate ann = fa.getAnnotation(OnUpdate.class);
                if (ann != null) {
                    ComputedValueProvider<?> provider = (ComputedValueProvider<?>) resolveBean(ann.value(), fa.getName());
                    infos.add(new ComputedFieldInfo(fa.getName(), provider));
                }
            }
            return infos;
        });
    }

    /**
     * Registers an {@code @OnUpdate} provider programmatically, replacing any
     * previously registered/discovered provider for that field.
     *
     * @param clazz     the class declaring the field
     * @param fieldName the name of the field
     * @param provider  the provider to invoke on every update
     */
    public void setComputedFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider) {
        onUpdateCache.compute(clazz, (c, existing) -> {
            List<ComputedFieldInfo> infos = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            infos.removeIf(i -> i.fieldName().equals(fieldName));
            infos.add(new ComputedFieldInfo(fieldName, provider));
            return infos;
        });
    }

    /**
     * Retrieves the {@code @OnInsert}-annotated fields of an entity class, each
     * resolved to its {@link ComputedValueProvider} instance (Spring bean first,
     * then no-arg constructor).
     * Result is cached for subsequent calls; an empty list means no
     * {@code @OnInsert} field is present.
     *
     * @param entityClass the entity class to inspect
     * @return the list of {@link ComputedFieldInfo}, never null, possibly empty
     */
    public List<ComputedFieldInfo> getOnInsertFieldInfos(Class<?> entityClass) {
        return onInsertCache.computeIfAbsent(entityClass, cls -> {
            List<ComputedFieldInfo> infos = new ArrayList<>();
            for (FieldAccessor<?> fa : ReflectionUtils.getFields(cls).list()) {
                OnInsert ann = fa.getAnnotation(OnInsert.class);
                if (ann != null) {
                    ComputedValueProvider<?> provider = (ComputedValueProvider<?>) resolveBean(ann.value(), fa.getName());
                    infos.add(new ComputedFieldInfo(fa.getName(), provider));
                }
            }
            return infos;
        });
    }

    /**
     * Registers an {@code @OnInsert} provider programmatically, replacing any
     * previously registered/discovered provider for that field.
     *
     * @param clazz     the class declaring the field
     * @param fieldName the name of the field
     * @param provider  the provider to invoke on every insert
     */
    public void setOnInsertFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider) {
        onInsertCache.compute(clazz, (c, existing) -> {
            List<ComputedFieldInfo> infos = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            infos.removeIf(i -> i.fieldName().equals(fieldName));
            infos.add(new ComputedFieldInfo(fieldName, provider));
            return infos;
        });
    }

    /**
     * Registers MappedBy association information programmatically.
     *
     * @param clazz              the class declaring the field
     * @param fieldName          the name of the field
     * @param foreignKeyProperty the property name that contains the foreign key
     * @param repositoryClass    the repository class to use
     */
    public void setMappedByInfo(Class<?> clazz, String fieldName, String foreignKeyProperty,
            Class<?> repositoryClass) {
        FieldKey key = new FieldKey(clazz, fieldName);
        mappedByCache.put(key, new MappedByInfo(foreignKeyProperty, repositoryClass));
    }

    /**
     * Registers OneToMany association information programmatically.
     *
     * @param clazz           the class declaring the field
     * @param fieldName       the name of the field
     * @param foreignKey      the field name in the target entity that references
     *                        this entity's ID
     * @param repositoryClass the repository class to use
     */
    public void setOneToManyInfo(Class<?> clazz, String fieldName, String foreignKey,
            Class<?> repositoryClass) {
        FieldKey key = new FieldKey(clazz, fieldName);
        oneToManyCache.put(key, new OneToManyAssociation(foreignKey, repositoryClass));
    }

    /**
     * Registers a class as a JSON type programmatically.
     * Propagates the JSON flag to all already-loaded field TypeInfos whose type is jsonClass.
     *
     * @param jsonClass the class to mark as a JSON type
     */
    public void setJsonInfo(Class<?> jsonClass) {
        jsonClassCache.add(jsonClass);
        propagateJsonToFields(jsonClass);
    }

    /**
     * Registers a specific field as a JSON field programmatically.
     *
     * @param clazz     the class declaring the field
     * @param fieldName the field name
     */
    public void setJsonInfo(Class<?> clazz, String fieldName) {
        getTypeInfoParams(clazz, fieldName).put(TypeParamKey.JSON, Boolean.TRUE);
    }

    /**
     * Registers CompositeType information programmatically.
     * Propagates the SQL type to all already-loaded field TypeInfos whose type is compositeClass.
     *
     * @param compositeClass the composite class key
     * @param sqlType        the database composite type name
     */
    public void setCompositeTypeInfo(Class<?> compositeClass, String sqlType) {
        compositeTypeCache.put(compositeClass, new CompositeTypeInfo(sqlType));
        propagateCompositeToFields(compositeClass, sqlType);
    }

    /**
     * Registers the database data type for a field programmatically.
     *
     * @param clazz     the class declaring the field
     * @param fieldName the name of the field
     * @param dataType  the database data type
     */
    public void setDbDataType(Class<?> clazz, String fieldName, DbDataType dataType) {
        Map<ParamKey, Object> existing = getTypeInfoParams(clazz, fieldName);
        if (existing.containsKey(TypeParamKey.ENCRYPTED)) {
            throw new NativSQLException("Cannot override DB_DATA_TYPE on an already-encrypted field: "
                    + clazz.getSimpleName() + "." + fieldName);
        }
        existing.put(TypeParamKey.DB_DATA_TYPE, dataType);
    }

    private Map<ParamKey, Object> getTypeInfoParams(Class<?> clazz, String fieldName) {
        return getTypeInfo(clazz, fieldName).getParams();
    }

    private TypeInfo getTypeInfo(Class<?> clazz, String fieldName) {
        FieldKey key = new FieldKey(clazz, fieldName);
        TypeInfo existing = typeCache.get(key);
        if (existing == null) {
            FieldAccessor<Object> fieldAccessor = ReflectionUtils.getFields(clazz).get(fieldName);
            existing = getTypeInfo(fieldAccessor);
            typeCache.putIfAbsent(key, existing);
            existing = typeCache.get(key);
        }
        return existing;
    }

    /**
     * Registers encryption parameters for a field programmatically.
     *
     * @param clazz      the class declaring the field
     * @param fieldName  the name of the field
     * @param algorithms the encryption algorithms to use (mandatory)
     * @param rawKey     the encryption key bytes (nullable for one-way algorithms)
     * @param prefix     the ciphertext prefix (nullable)
     * @param cost       the bcrypt cost factor (nullable, defaults to 12)
     * @param dbDataType the storage type (nullable, defaults to STRING; must be
     *                   STRING or BYTE_ARRAY)
     */
    public void setEncrypted(Class<?> clazz, String fieldName,
            CryptAlgorithm[] algorithms,
            byte[] rawKey,
            String prefix,
            Integer cost,
            DbDataType dbDataType) {
        Map<ParamKey, Object> existing = getTypeInfoParams(clazz, fieldName);
        if (existing.containsKey(TypeParamKey.DB_DATA_TYPE)) {
            throw new NativSQLException("Cannot apply encryption on a field that already has a DB_DATA_TYPE: "
                    + clazz.getSimpleName() + "." + fieldName);
        }
        if (dbDataType != null && dbDataType != DbDataType.STRING && dbDataType != DbDataType.BYTE_ARRAY) {
            throw new NativSQLException(
                    "Encrypted field: DB_DATA_TYPE must be STRING or BYTE_ARRAY, got " + dbDataType);
        }
        existing.put(TypeParamKey.DB_DATA_TYPE, dbDataType != null ? dbDataType : DbDataType.STRING);
        existing.put(TypeParamKey.ENCRYPTED, Boolean.TRUE);
        existing.put(TypeParamKey.ALGO, algorithms);
        if (rawKey != null)
            existing.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> rawKey);
        if (prefix != null)
            existing.put(TypeParamKey.PREFIX, prefix);
        if (cost != null)
            existing.put(TypeParamKey.COST, cost);
    }

    /**
     * Registers a dialect-specific SQL type name for a field programmatically.
     *
     * @param clazz       the class declaring the field
     * @param fieldName   the name of the field
     * @param dataType    the database data type
     * @param sqlTypeName the SQL type name (e.g. {@code "contact_type"})
     */
    public void setSqlType(Class<?> clazz, String fieldName, DbDataType dataType,
            String sqlTypeName) {
        Map<ParamKey, Object> existing = getTypeInfoParams(clazz, fieldName);
        existing.put(TypeParamKey.DB_DATA_TYPE, dataType);
        existing.put(TypeParamKey.SQL_TYPE, sqlTypeName);
    }

    private final Map<Class<?>, String> enumSqlTypeCache = new ConcurrentHashMap<>();

    /**
     * Returns the SQL type name for an enum class.
     * Reads {@link SqlType} from the class annotation, cached.
     *
     * @param enumClass the enum class to inspect
     * @return the SQL type name, or null if not set
     */
    private String getEnumSqlType(Class<?> enumClass) {
        return enumSqlTypeCache.computeIfAbsent(enumClass, clazz -> {
            SqlType sqlType = getAnnotation(enumClass, SqlType.class);
            return sqlType != null ? sqlType.value() : null;
        });
    }

    /**
     * Propagates a SQL type name to all cached fields whose type matches targetType.
     *
     * @param targetType  the field type to match
     * @param sqlTypeName the SQL type name to propagate
     */
    private void propagateCompositeToFields(Class<?> targetType, String sqlType) {
        for (Map.Entry<FieldKey, TypeInfo> entry : typeCache.entrySet()) {
            FieldKey key = entry.getKey();
            TypeInfo typeInfo = entry.getValue();
            FieldAccessor<Object> fa = ReflectionUtils.getFields(key.clazz()).getOrNull(key.fieldName());
            if (fa != null && fa.getType() == targetType) {
                typeInfo.getParams().put(TypeParamKey.COMPOSITE, Boolean.TRUE);
                if (sqlType != null) {
                    Object existing = typeInfo.getParams().get(TypeParamKey.SQL_TYPE);
                    if (existing != null && !sqlType.equals(existing)) {
                        throw new NativSQLException("Conflict: field "
                                + key.clazz().getSimpleName() + "." + key.fieldName()
                                + " already has SQL_TYPE=" + existing
                                + ", cannot override with " + sqlType);
                    }
                    typeInfo.getParams().put(TypeParamKey.SQL_TYPE, sqlType);
                }
            }
        }
    }

    private void propagateJsonToFields(Class<?> targetType) {
        for (Map.Entry<FieldKey, TypeInfo> entry : typeCache.entrySet()) {
            FieldKey key = entry.getKey();
            TypeInfo typeInfo = entry.getValue();
            FieldAccessor<Object> fa = ReflectionUtils.getFields(key.clazz()).getOrNull(key.fieldName());
            if (fa != null && fa.getType() == targetType) {
                typeInfo.getParams().put(TypeParamKey.JSON, Boolean.TRUE);
            }
        }
    }

    private void propagateSqlTypeToFields(Class<?> targetType, String sqlTypeName) {
        for (Map.Entry<FieldKey, TypeInfo> entry : typeCache.entrySet()) {
            FieldKey key = entry.getKey();
            TypeInfo typeInfo = entry.getValue();
            Fields fields = ReflectionUtils.getFields(key.clazz());
            FieldAccessor<Object> fa = fields.getOrNull(key.fieldName());
            if (fa != null && fa.getType() == targetType) {
                if (typeInfo.getParams().containsKey(TypeParamKey.SQL_TYPE)) {
                    Object existing = typeInfo.getParams().get(TypeParamKey.SQL_TYPE);
                    if (!sqlTypeName.equals(existing)) {
                        throw new NativSQLException("Conflict: field "
                                + key.clazz().getSimpleName() + "." + key.fieldName()
                                + " already has SQL_TYPE=" + existing
                                + ", cannot override with " + sqlTypeName);
                    }
                    // same value, no-op
                } else {
                    typeInfo.getParams().put(TypeParamKey.SQL_TYPE, sqlTypeName);
                }
            }
        }
    }

    /**
     * Registers the SQL type name for an enum class programmatically.
     *
     * @param enumClass   the enum class
     * @param sqlTypeName the SQL type name (e.g. {@code "user_status"})
     */
    public void setEnumSqlType(Class<?> enumClass, String sqlTypeName) {
        String existing = enumSqlTypeCache.get(enumClass);
        if (existing != null && !existing.equals(sqlTypeName)) {
            throw new NativSQLException("Conflict: enum SQL type already set to " + existing
                    + " for " + enumClass.getSimpleName());
        }
        enumSqlTypeCache.put(enumClass, sqlTypeName);
        propagateSqlTypeToFields(enumClass, sqlTypeName);
    }

    /**
     * Clears all cached annotation information.
     * This is useful for testing to ensure tests don't interfere with each other.
     */
    public void clearCache() {
        mappedByCache.clear();
        oneToManyCache.clear();
        jsonClassCache.clear();
        compositeTypeCache.clear();
        typeCache.clear();
        enumSqlTypeCache.clear();
        onUpdateCache.clear();
        onInsertCache.clear();
    }
}
