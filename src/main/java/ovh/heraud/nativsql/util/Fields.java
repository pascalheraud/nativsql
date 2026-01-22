package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around a list of FieldAccessors with fast lookup by name.
 */
public class Fields {
    private final List<FieldAccessor> fields;
    private final Map<String, FieldAccessor> fieldsByName;

    public Fields(List<FieldAccessor> fields) {
        this.fields = new ArrayList<>(fields);
        this.fieldsByName = new HashMap<>();
        for (FieldAccessor field : fields) {
            fieldsByName.put(field.getName(), field);
        }
    }

    /**
     * Gets a field by name.
     */
    public FieldAccessor get(String name) {
        return fieldsByName.get(name);
    }

    /**
     * Gets all fields as a list.
     */
    public List<FieldAccessor> list() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Gets all field names.
     */
    public java.util.Set<String> names() {
        return Collections.unmodifiableSet(fieldsByName.keySet());
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
