package ovh.heraud.nativsql.util;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a raw SQL expression added to the SELECT clause via
 * {@link FindQuery#selectExpression}.
 * The expression is rendered (mostly) verbatim and aliased with
 * {@code AS "alias"} — the literal token {@code {{table}}} is substituted
 * with the query's table name when the SQL is built.
 */
@Data
@NoArgsConstructor
public class ExpressionColumn {
    private String alias;
    private String sql;
    private Map<String, Object> params;

    public ExpressionColumn(String alias, String sql, Map<String, Object> params) {
        this.alias = alias;
        this.sql = sql;
        this.params = new HashMap<>(params);
    }
}
