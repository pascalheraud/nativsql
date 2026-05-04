package ovh.heraud.nativsql.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import ovh.heraud.nativsql.annotation.type.CryptParam;
import ovh.heraud.nativsql.annotation.type.Inject;
import ovh.heraud.nativsql.annotation.type.Type;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.EnumMappingInfo;
import ovh.heraud.nativsql.util.MappedByInfo;
import ovh.heraud.nativsql.util.OneToManyAssociation;
import ovh.heraud.nativsql.util.JsonInfo;
import ovh.heraud.nativsql.util.CompositeTypeInfo;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Centralized component for managing and retrieving annotations from entity classes.
 * This component encapsulates all annotation-related operations, providing a single
 * point of access for annotation metadata extraction.
 *
 * Rather than returning raw annotation objects, this manager returns domain-specific
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
    private final Map<Class<?>, EnumMappingInfo> enumMappingCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, JsonInfo> jsonInfoCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, CompositeTypeInfo> compositeTypeCache = new ConcurrentHashMap<>();
    private final Map<FieldKey, TypeInfo> typeCache = new ConcurrentHashMap<>();

    /**
     * Creates a FieldKey from a FieldAccessor.
     *
     * @param fieldAccessor the field accessor
     * @return the field key representing the field
     */
    private FieldKey createFieldKey(FieldAccessor<?> fieldAccessor) {
        return new FieldKey(fieldAccessor.getField().getDeclaringClass(), fieldAccessor.getName());
    }

    /**
     * Retrieves MappedBy association information from a field.
     * Returns a MappedByInfo object containing the foreign key property and repository class.
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
     * Returns a OneToManyAssociation object containing the foreign key and repository class.
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
     * Retrieves EnumMapping annotation information from a class.
     * Returns an EnumMappingInfo object containing the database enum type name.
     * Result is cached for subsequent calls.
     *
     * @param enumClass the enum class to inspect
     * @return EnumMappingInfo if @EnumMapping is present, null otherwise
     */
    public EnumMappingInfo getEnumMappingInfo(Class<?> enumClass) {
        return enumMappingCache.computeIfAbsent(enumClass, clazz -> {
            EnumMapping enumMapping = clazz.getAnnotation(EnumMapping.class);
            if (enumMapping == null) {
                return null;
            }
            return new EnumMappingInfo(enumMapping.typeName());
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
    public JsonInfo getJsonInfo(Class<?> jsonClass) {
        return jsonInfoCache.computeIfAbsent(jsonClass, clazz -> {
            Json json = clazz.getAnnotation(Json.class);
            if (json == null) {
                return null;
            }
            return new JsonInfo();
        });
    }

    /**
     * Retrieves CompositeType annotation information from a class.
     * Returns a CompositeTypeInfo object containing the database composite type name.
     * Result is cached for subsequent calls.
     *
     * @param compositeClass the composite class to inspect
     * @return CompositeTypeInfo if @CompositeType is present, null otherwise
     */
    public CompositeTypeInfo getCompositeTypeInfo(Class<?> compositeClass) {
        return compositeTypeCache.computeIfAbsent(compositeClass, clazz -> {
            CompositeType compositeType = clazz.getAnnotation(CompositeType.class);
            if (compositeType == null) {
                return null;
            }
            return new CompositeTypeInfo(compositeType.typeName());
        });
    }

    /**
     * Retrieves Type annotation information from a field.
     * Returns a TypeInfo object containing the database data type and optional params.
     * Result is cached for subsequent calls.
     *
     * @param fieldAccessor the field accessor to inspect
     * @return TypeInfo if @Type is present, null otherwise
     */
    public TypeInfo getTypeInfo(FieldAccessor<?> fieldAccessor) {
        FieldKey key = createFieldKey(fieldAccessor);
        return typeCache.computeIfAbsent(key, k -> {
            Type type = fieldAccessor.getAnnotation(Type.class);
            if (type == null) {
                return null;
            }
            Map<TypeParamKey, Object> params = scanCryptParams(fieldAccessor);
            if (params.isEmpty()) {
                return new TypeInfo(type.value());
            }
            return new TypeInfo(type.value(), params);
        });
    }

    /**
     * Scans the field's annotations for any annotation carrying {@link CryptParam},
     * reads its {@code value()} via reflection, and returns the collected params.
     *
     * <p>If the annotation also carries {@link Inject}, the {@code Class<?>} returned by
     * {@code value()} is resolved to an instance (Spring context first, then no-arg constructor).
     */
    private Map<TypeParamKey, Object> scanCryptParams(FieldAccessor<?> fieldAccessor) {
        Map<TypeParamKey, Object> params = new HashMap<>();
        for (java.lang.annotation.Annotation annotation : fieldAccessor.getField().getAnnotations()) {
            CryptParam meta = annotation.annotationType().getAnnotation(CryptParam.class);
            if (meta == null) {
                continue;
            }
            try {
                Object value = annotation.annotationType().getMethod("value").invoke(annotation);
                if (annotation.annotationType().isAnnotationPresent(Inject.class)
                        && value instanceof Class<?> cls) {
                    value = resolveBean(cls, fieldAccessor.getName());
                }
                params.put(meta.key(), value);
            } catch (ReflectiveOperationException e) {
                throw new NativSQLException(
                        "Failed to read @CryptParam value from @"
                                + annotation.annotationType().getSimpleName()
                                + " on field " + fieldAccessor.getName(), e);
            }
        }
        return params;
    }

    /**
     * Resolves a class to an instance: Spring context first, then no-arg constructor.
     */
    private Object resolveBean(Class<?> cls, String fieldName) {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(cls);
            } catch (Exception ignored) {
                // not a Spring bean — fall through
            }
        }
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new NativSQLException(
                    "Field '" + fieldName + "': " + cls.getName()
                            + " has no no-arg constructor and is not a Spring bean", e);
        } catch (ReflectiveOperationException e) {
            throw new NativSQLException(
                    "Field '" + fieldName + "': failed to instantiate " + cls.getName(), e);
        }
    }

    /**
     * Registers MappedBy association information programmatically.
     *
     * @param clazz the class declaring the field
     * @param fieldName the name of the field
     * @param foreignKeyProperty the property name that contains the foreign key
     * @param repositoryClass the repository class to use
     */
    public void setMappedByInfo(Class<?> clazz, String fieldName, String foreignKeyProperty, Class<?> repositoryClass) {
        FieldKey key = new FieldKey(clazz, fieldName);
        mappedByCache.put(key, new MappedByInfo(foreignKeyProperty, repositoryClass));
    }

    /**
     * Registers OneToMany association information programmatically.
     *
     * @param clazz the class declaring the field
     * @param fieldName the name of the field
     * @param foreignKey the field name in the target entity that references this entity's ID
     * @param repositoryClass the repository class to use
     */
    public void setOneToManyInfo(Class<?> clazz, String fieldName, String foreignKey, Class<?> repositoryClass) {
        FieldKey key = new FieldKey(clazz, fieldName);
        oneToManyCache.put(key, new OneToManyAssociation(foreignKey, repositoryClass));
    }

    /**
     * Registers EnumMapping information programmatically.
     *
     * @param enumClass the enum class key
     * @param typeName the database enum type name
     */
    public void setEnumMappingInfo(Class<?> enumClass, String typeName) {
        enumMappingCache.put(enumClass, new EnumMappingInfo(typeName));
    }

    /**
     * Registers a class as a JSON type programmatically.
     *
     * @param jsonClass the class to mark as a JSON type
     */
    public void setJsonInfo(Class<?> jsonClass) {
        jsonInfoCache.put(jsonClass, new JsonInfo());
    }

    /**
     * Registers CompositeType information programmatically.
     *
     * @param compositeClass the composite class key
     * @param typeName the database composite type name
     */
    public void setCompositeTypeInfo(Class<?> compositeClass, String typeName) {
        compositeTypeCache.put(compositeClass, new CompositeTypeInfo(typeName));
    }

    /**
     * Registers Type information programmatically (without params).
     *
     * @param clazz the class declaring the field
     * @param fieldName the name of the field
     * @param dataType the database data type
     */
    public void setTypeInfo(Class<?> clazz, String fieldName, DbDataType dataType) {
        FieldKey key = new FieldKey(clazz, fieldName);
        typeCache.put(key, new TypeInfo(dataType));
    }

    /**
     * Registers Type information programmatically with params.
     *
     * @param clazz     the class declaring the field
     * @param fieldName the name of the field
     * @param dataType  the database data type
     * @param params    the type parameters (e.g. ALGO, KEY_PROVIDER, PREFIX)
     */
    public void setTypeInfo(Class<?> clazz, String fieldName, DbDataType dataType,
                            Map<TypeParamKey, Object> params) {
        FieldKey key = new FieldKey(clazz, fieldName);
        typeCache.put(key, new TypeInfo(dataType, params));
    }

    /**
     * Clears all cached annotation information.
     * This is useful for testing to ensure tests don't interfere with each other.
     */
    public void clearCache() {
        mappedByCache.clear();
        oneToManyCache.clear();
        enumMappingCache.clear();
        jsonInfoCache.clear();
        compositeTypeCache.clear();
        typeCache.clear();
    }
}
