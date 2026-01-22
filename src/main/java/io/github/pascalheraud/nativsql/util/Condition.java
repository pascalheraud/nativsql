package io.github.pascalheraud.nativsql.util;

import java.util.Objects;

/**
 * Represents a WHERE condition in a SQL query.
 * Contains a column, an operator (EQUALS, IN), and a value.
 */
public class Condition {
    private final String column;
    private final Operator operator;
    private final Object value;

    /**
     * Creates a new Condition.
     *
     * @param column   the column name (required)
     * @param operator the operator to use (required)
     * @param value    the value to compare (required)
     */
    public Condition(String column, Operator operator, Object value) {
        this.column = Objects.requireNonNull(column, "Column cannot be null");
        this.operator = Objects.requireNonNull(operator, "Operator cannot be null");
        this.value = Objects.requireNonNull(value, "Value cannot be null");
    }

    public String getColumn() {
        return column;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Condition condition = (Condition) o;
        return Objects.equals(column, condition.column) &&
                operator == condition.operator &&
                Objects.equals(value, condition.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, operator, value);
    }

    @Override
    public String toString() {
        return "Condition{" +
                "column='" + column + '\'' +
                ", operator=" + operator +
                ", value=" + value +
                '}';
    }
}
