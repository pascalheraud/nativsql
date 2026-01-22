package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an association to load in a query.
 * Specifies which association to load and which columns to retrieve.
 */
@Data
@NoArgsConstructor
public class Association {
    private String name;
    private List<String> columns;

    public Association(String name, List<String> columns) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
    }
}
