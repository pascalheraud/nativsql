package io.github.pascalheraud.nativsql.repository;

import java.util.Arrays;

/**
 * Configuration for loading a OneToMany association.
 * Specifies which association to load and which columns to retrieve.
 */
public class AssociationConfig {

    private final String associationField;
    private final String[] columns;

    /**
     * Creates a new association configuration.
     *
     * @param associationField the name of the association field
     * @param columns the columns to load for the association
     */
    public AssociationConfig(String associationField, String... columns) {
        this.associationField = associationField;
        this.columns = columns;
    }

    /**
     * Gets the association field name.
     *
     * @return the association field name
     */
    public String getAssociationField() {
        return associationField;
    }

    /**
     * Gets the columns to load.
     *
     * @return the columns to load
     */
    public String[] getColumns() {
        return columns;
    }

    /**
     * Creates an association configuration with all columns.
     *
     * @param associationField the name of the association field
     * @return a new AssociationConfig with no specific columns (loads all)
     */
    public static AssociationConfig of(String associationField) {
        return new AssociationConfig(associationField);
    }

    /**
     * Creates an association configuration with specific columns.
     *
     * @param associationField the name of the association field
     * @param columns the columns to load
     * @return a new AssociationConfig
     */
    public static AssociationConfig of(String associationField, String... columns) {
        return new AssociationConfig(associationField, columns);
    }

    @Override
    public String toString() {
        return "AssociationConfig{" +
                "associationField='" + associationField + '\'' +
                ", columns=" + Arrays.toString(columns) +
                '}';
    }
}
