package io.github.pascalheraud.nativsql.mapper;

import java.sql.ResultSet;

import io.github.pascalheraud.nativsql.exception.NativSQLException;
import org.springframework.jdbc.support.JdbcUtils;

public class DefaultTypeMapper<T> implements ITypeMapper<T> {

    @SuppressWarnings("unchecked")
    @Override
    public T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            return (T) JdbcUtils.getResultSetValue(rs, index);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
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