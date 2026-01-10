package io.github.pascalheraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an association to load in a query.
 * Specifies which association to load and which columns to retrieve.
 */
public class Association {
    private final String name;
    private final List<String> columns;

    public Association(String name, List<String> columns) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
    }

    public String getName() {
        return name;
    }

    /**
     * Alias for getName() for compatibility.
     */
    public String getAssociationField() {
        return name;
    }

    public List<String> getColumns() {
        return new ArrayList<>(columns);
    }

    public String[] getColumnsArray() {
        return columns.toArray(new String[0]);
    }

    /**
     * Factory method to create an association with all columns.
     */
    public static Association of(String associationField) {
        return new Association(associationField, new ArrayList<>());
    }

    /**
     * Factory method to create an association with specific columns.
     */
    public static Association of(String associationField, String... columns) {
        return new Association(associationField, Arrays.asList(columns));
    }

    /**
     * Factory method to create an association with a list of columns.
     */
    public static Association of(String associationField, List<String> columns) {
        return new Association(associationField, columns);
    }
}
