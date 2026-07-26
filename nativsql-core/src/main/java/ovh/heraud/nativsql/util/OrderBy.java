package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

/**
 * Builder for SQL ORDER BY clauses.
 * Example: new OrderBy().asc("name").desc("createdAt").build(converter) →
 * "ORDER BY name ASC, created_at DESC"
 */
public class OrderBy implements SQLBuilder {
    private final List<Order> orders = new ArrayList<>();
    private String tablePrefix = "";
    private boolean hasJoins = false;
    private JoinResolver joinResolver = null;

    /**
     * Sets the table prefix for column names (e.g., "user_table").
     * Used when there are JOINs to avoid column ambiguity.
     *
     * @param tablePrefix the table name to prefix columns with
     * @return this for method chaining
     */
    public OrderBy withTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        return this;
    }

    /**
     * Sets whether there are JOINs in the query.
     * When true, columns will be prefixed with the table name.
     *
     * @param hasJoins whether the query has JOINs
     * @return this for method chaining
     */
    public OrderBy withJoins(boolean hasJoins) {
        this.hasJoins = hasJoins;
        return this;
    }

    /**
     * Registers the resolver that translates dot-notation column paths
     * (e.g. "group.name") to fully-qualified SQL column references.
     * Must be set before {@link #build} is called when dot-notation columns are used.
     *
     * @param resolver the join resolver
     * @return this for method chaining
     */
    public OrderBy withJoinResolver(JoinResolver resolver) {
        this.joinResolver = resolver;
        return this;
    }

    /**
     * Adds an ascending order by the specified column.
     */
    public OrderBy asc(String column) {
        orders.add(new Order(column, true));
        return this;
    }

    /**
     * Adds an ascending order by the column derived from the getter method
     * reference.
     */
    public <T> OrderBy asc(Getter<T> getter) {
        return asc(ReflectionUtils.getColumnName(getter));
    }

    /**
     * Adds a descending order by the specified column.
     */
    public OrderBy desc(String column) {
        orders.add(new Order(column, false));
        return this;
    }

    /**
     * Adds a descending order by the column derived from the getter method
     * reference.
     */
    public <T> OrderBy desc(Getter<T> getter) {
        return desc(ReflectionUtils.getColumnName(getter));
    }

    /**
     * Builds the SQL ORDER BY clause.
     * Appends "ORDER BY xxx" to the StringBuilder if orders have been specified.
     * Example: "ORDER BY name ASC, created_at DESC"
     *
     * @param sb        the StringBuilder to append the SQL to
     * @param converter the identifier converter to use for column name
     *                  transformation
     */
    public void build(StringBuilder sb, IdentifierConverter converter) {
        if (orders.isEmpty()) {
            return;
        }

        sb.append("ORDER BY ");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            orders.get(i).build(sb, converter);
        }
    }

    /**
     * Builds the SQL ORDER BY clause and returns it as a String.
     * This is a convenience method that creates a StringBuilder internally.
     *
     * @param converter the identifier converter to use for column name
     *                  transformation
     * @return the SQL ORDER BY clause or empty string if no orders specified
     */
    public String buildString(IdentifierConverter converter) {
        if (orders.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        build(sb, converter);
        return sb.toString();
    }

    /**
     * Returns true if at least one order has been specified.
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }

    /**
     * Copies all order conditions from another OrderBy builder into this one.
     * This is more efficient than parsing a string representation.
     *
     * @param other the OrderBy to copy orders from
     */
    public void copyFrom(OrderBy other) {
        for (Order order : other.orders) {
            if (order.isAsc) {
                this.asc(order.column);
            } else {
                this.desc(order.column);
            }
        }
    }

    /**
     * Builds the SQL ORDER BY clause with formatting (newlines and indentation).
     * Appends "ORDER BY ..." to the StringBuilder if orders have been specified.
     *
     * @param sb        the StringBuilder to append the SQL to
     * @param converter the identifier converter to use for column name
     *                  transformation
     */
    public void buildFormatted(StringBuilder sb, IdentifierConverter converter) {
        if (orders.isEmpty()) {
            return;
        }

        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append("    ");
            orders.get(i).build(sb, converter);
        }
    }

    /**
     * Resolves a column name/path to its fully-qualified SQL column reference,
     * mirroring {@code WhereClause.toDbCol}.
     *
     * @param converter the identifier converter for column name transformation
     * @param column    the column name or dot-notation path
     * @return the resolved SQL column reference
     */
    private String toDbCol(IdentifierConverter converter, String column) {
        if (column.contains(".")) {
            if (joinResolver == null) {
                throw new NativSQLException(
                        "Dot-notation column paths are not supported in this query type: '" + column + "'");
            }
            return joinResolver.resolve(column, converter);
        }
        String dbCol = converter.toDB(column);
        if (hasJoins && !tablePrefix.isEmpty()) {
            dbCol = tablePrefix + "." + dbCol;
        }
        return dbCol;
    }

    /**
     * Inner class representing a single ORDER BY condition.
     * Implements SQLBuilder to generate its portion of the SQL statement.
     */
    private class Order implements SQLBuilder {
        final String column;
        final boolean isAsc;

        Order(String column, boolean isAsc) {
            this.column = column;
            this.isAsc = isAsc;
        }

        @Override
        public void build(StringBuilder sb, IdentifierConverter converter) {
            String columnName = toDbCol(converter, column);
            sb.append(columnName).append(" ").append(isAsc ? "ASC" : "DESC");
        }
    }
}
