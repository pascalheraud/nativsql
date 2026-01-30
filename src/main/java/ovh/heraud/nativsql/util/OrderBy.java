package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.db.IdentifierConverter;

/**
 * Builder for SQL ORDER BY clauses.
 * Example: new OrderBy().asc("name").desc("createdAt").build(converter) → "ORDER BY name ASC, created_at DESC"
 */
public class OrderBy implements SQLBuilder {
    private final List<Order> orders = new ArrayList<>();

    /**
     * Adds an ascending order by the specified column.
     */
    public OrderBy asc(String column) {
        orders.add(new Order(column, true));
        return this;
    }

    /**
     * Adds a descending order by the specified column.
     */
    public OrderBy desc(String column) {
        orders.add(new Order(column, false));
        return this;
    }

    /**
     * Builds the SQL ORDER BY clause.
     * Appends "ORDER BY xxx" to the StringBuilder if orders have been specified.
     * Example: "ORDER BY name ASC, created_at DESC"
     *
     * @param sb                  the StringBuilder to append the SQL to
     * @param converter           the identifier converter to use for column name transformation
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
     * @param converter the identifier converter to use for column name transformation
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
     * Inner class representing a single ORDER BY condition.
     * Implements SQLBuilder to generate its portion of the SQL statement.
     */
    private static class Order implements SQLBuilder {
        final String column;
        final boolean isAsc;

        Order(String column, boolean isAsc) {
            this.column = column;
            this.isAsc = isAsc;
        }

        @Override
        public void build(StringBuilder sb, IdentifierConverter converter) {
            String columnName = converter.toDB(column);
            sb.append(columnName).append(" ").append(isAsc ? "ASC" : "DESC");
        }
    }
}
