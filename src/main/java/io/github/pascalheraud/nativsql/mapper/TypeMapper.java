package io.github.pascalheraud.nativsql.mapper;
import java.sql.ResultSet;
import java.sql.SQLException;
@FunctionalInterface
public interface TypeMapper<T> {
    T map(ResultSet rs, String columnName) throws SQLException;
}
