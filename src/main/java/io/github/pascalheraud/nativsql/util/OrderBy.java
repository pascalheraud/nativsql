package io.github.pascalheraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;

/**
 * Builder for SQL ORDER BY clauses.
 * Example: new OrderBy().asc("name").desc("createdAt").build() → "ORDER BY name ASC, created_at DESC"
 */
public class OrderBy {
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
     * Returns "ORDER BY xxx" or empty string if no orders have been specified.
     * Example: "ORDER BY name ASC, created_at DESC"
     *
     * @param dialect the database dialect for identifier conversion
     * @return the SQL ORDER BY clause
     */
    public String build(DatabaseDialect dialect) {
        if (orders.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("ORDER BY ");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Order order = orders.get(i);
            String columnName = dialect.javaToDBIdentifier(order.column);
            sb.append(columnName).append(" ").append(order.isAsc ? "ASC" : "DESC");
        }
        return sb.toString();
    }

    /**
     * Returns true if at least one order has been specified.
     */
    public boolean isEmpty() {
        return orders.isEmpty();
    }

    /**
     * Inner class representing a single ORDER BY condition.
     */
    private static class Order {
        final String column;
        final boolean isAsc;

        Order(String column, boolean isAsc) {
            this.column = column;
            this.isAsc = isAsc;
        }
    }
}
