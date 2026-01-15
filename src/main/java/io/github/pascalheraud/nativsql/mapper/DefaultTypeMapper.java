package io.github.pascalheraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.support.JdbcUtils;

public class DefaultTypeMapper<T> implements ITypeMapper<T> {

    @SuppressWarnings("unchecked")
    @Override
    public T map(ResultSet rs, String columnName) throws SQLException {
        int index = rs.findColumn(columnName);
        return (T) JdbcUtils.getResultSetValue(rs, index);
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