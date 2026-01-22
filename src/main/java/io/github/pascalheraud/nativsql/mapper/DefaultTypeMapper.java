package io.github.pascalheraud.nativsql.mapper;

import java.math.BigDecimal;
import java.sql.ResultSet;

import io.github.pascalheraud.nativsql.exception.NativSQLException;
import org.springframework.jdbc.support.JdbcUtils;

public class DefaultTypeMapper<T> implements ITypeMapper<T> {

    @SuppressWarnings("unchecked")
    @Override
    public T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            return (T) convertValue(value);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    /**
     * Converts SQL numeric types to Java numeric types intelligently.
     * For example, converts BigDecimal to Long if the target is Long.
     */
    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            // Check if it's a whole number
            if (decimal.scale() <= 0) {
                // Try to convert to Long if it fits
                try {
                    return decimal.longValueExact();
                } catch (ArithmeticException e) {
                    // If it doesn't fit in a Long, keep as BigDecimal
                    return decimal;
                }
            }
        }

        return value;
    }

    @Override
    public Object toDatabase(T value) {
        return value;
    }

    @Override
    public String formatParameter(String paramName) {
        return ":" + paramName;
    }

}