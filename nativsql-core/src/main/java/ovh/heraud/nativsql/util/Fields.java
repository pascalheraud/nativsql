package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Wrapper around a list of FieldAccessors with fast lookup by name.
 */
public class Fields {
    private final List<FieldAccessor<?>> fields;
    private final Map<String, FieldAccessor<?>> fieldsByName;

    public Fields(List<FieldAccessor<?>> fields) {
        this.fields = new ArrayList<>(fields);
        this.fieldsByName = new HashMap<>();
        for (FieldAccessor<?> field : fields) {
            fieldsByName.put(field.getName(), field);
        }
    }

    /**
     * Gets a field by name.
     * 
     * @throws NativSQLException if the field does not exist
     */
    public <T> FieldAccessor<T> get(String name) {
        FieldAccessor<T> fieldAccessor = getOrNull(name);
        if (fieldAccessor == null) {
            throw new NativSQLException("Field with name " + name + " not found");
        }
        return fieldAccessor;
    }

    /**
     * Gets a field by name.
     * 
     * @return null if the field does not exist
     */
    @SuppressWarnings("unchecked")
    public <T> FieldAccessor<T> getOrNull(String name) {
        return (FieldAccessor<T>) fieldsByName.get(name);
    }

    /**
     * Gets all fields as a list.
     */
    public List<FieldAccessor<?>> list() {
        return (Collections.unmodifiableList(fields));
    }

    /**
     * Gets all field names.
     */
    public Set<String> names() {
        return (Collections.unmodifiableSet(fieldsByName.keySet()));
    }

    /**
     * Checks if a field exists.
     */
    public boolean contains(String name) {
        return fieldsByName.containsKey(name);
    }

    /**
     * Gets the number of fields.
     */
    public int size() {
        return fields.size();
    }

    /**
     * Checks if there are no fields.
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }
}
