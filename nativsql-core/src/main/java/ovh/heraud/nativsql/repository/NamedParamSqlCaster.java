package ovh.heraud.nativsql.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Injects dialect-specific casts (e.g. PostgreSQL's "::boolean") around named
 * parameter placeholders in hand-written {@code findExternal}/
 * {@code findAllExternal} SQL, so the database can determine a parameter's
 * type even when it is used without a typed comparison (issue #118).
 *
 * <p>
 * For each {@code :name} token found in the SQL text (outside string
 * literals and comments), the parameter's type is resolved, in order, from:
 * a {@link NullableParam} value, a matching entity field, or the runtime
 * class of a non-null value. The token's mapper is asked to
 * {@link ITypeMapper#formatParameter} it; a name with no resolvable type, or
 * whose mapper does not request a cast, is left unchanged. An occurrence
 * already followed by an explicit {@code ::} cast in the SQL text (whatever
 * the cast type — this is not checked against the resolved type) is left
 * unchanged too, to avoid stacking a redundant cast on top of one the caller
 * already wrote. Only the bare {@code ::} suffix is special-cased this way —
 * a parameter already wrapped in a standard {@code CAST(:name AS type)} is
 * not detected (that would require scanning backwards for a "CAST(" prefix)
 * and gets nested inside our own cast too (e.g.
 * {@code CAST((:name)::boolean AS boolean)}); harmless (a value cast to its
 * own type is a no-op) but redundant.
 *
 * <p>
 * The scanning algorithm (skipping string literals, quoted identifiers, and
 * comments while locating named-parameter tokens) is adapted from Spring
 * Framework's {@code org.springframework.jdbc.core.namedparam.NamedParameterUtils}
 * (Apache License 2.0) — reimplemented rather than reused because the
 * classes involved there are package-private to Spring's own package.
 *
 * <p>
 * Results are cached by the raw SQL string: the SQL text of a
 * {@code findExternal}/{@code findAllExternal} call is a source-level
 * constant, so the scan only needs to run once per distinct query. Callers
 * must wrap a nullable, unmapped parameter in {@link NullableParam}
 * consistently on every call for a given query, since only the first call's
 * {@code params} shape is used to build the cached rewrite.
 */
class NamedParamSqlCaster {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    String castNamedParameters(String sql, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager) {
        return cache.computeIfAbsent(sql,
                s -> rewrite(s, params, entityFields, dialect, annotationManager));
    }

    private String rewrite(String sql, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager) {
        List<int[]> tokens = new ArrayList<>();
        List<String> names = new ArrayList<>();
        scan(sql, tokens, names);
        if (names.isEmpty()) {
            return sql;
        }

        Map<String, String> replacementByName = new HashMap<>();
        for (String name : new LinkedHashSet<>(names)) {
            String replacement = resolveReplacement(name, params, entityFields, dialect, annotationManager);
            if (replacement != null) {
                replacementByName.put(name, replacement);
            }
        }
        if (replacementByName.isEmpty()) {
            return sql;
        }

        StringBuilder result = new StringBuilder();
        int last = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String replacement = replacementByName.get(names.get(i));
            int[] token = tokens.get(i);
            if (replacement == null || sql.startsWith("::", token[1])) {
                continue;
            }
            result.append(sql, last, token[0]).append(replacement);
            last = token[1];
        }
        result.append(sql, last, sql.length());
        return result.toString();
    }

    private String resolveReplacement(String name, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager) {
        Object value = params.get(name);
        if (value instanceof java.util.Collection || (value != null && value.getClass().isArray())) {
            // A list-valued param (e.g. "col IN (:name)") is expanded into "?, ?, ..." by
            // the JDBC template's own named-param parsing; wrapping ":name" in a cast
            // here would corrupt that expansion (e.g. "IN ((?, ?)::bigint)").
            return null;
        }
        FieldAccessor<?> fieldAccessor = resolveFieldAccessor(name, params, entityFields);
        if (fieldAccessor == null) {
            return null;
        }
        ITypeMapper<?> mapper = dialect.getMapper(fieldAccessor, annotationManager);
        if (mapper == null) {
            return null;
        }
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
        String formatted = mapper.formatParameter(name, typeInfo.getParams());
        return formatted.equals(":" + name) ? null : formatted;
    }

    private FieldAccessor<?> resolveFieldAccessor(String name, Map<String, Object> params, Fields entityFields) {
        Object value = params.get(name);
        if (value instanceof NullableParam nullableParam) {
            return new FieldAccessor<>(nullableParam.getType());
        }
        FieldAccessor<?> declared = entityFields.getOrNull(name);
        if (declared != null) {
            return declared;
        }
        return value != null ? new FieldAccessor<>(value.getClass()) : null;
    }

    /**
     * Scans sql for ":name" tokens, skipping single-quoted string literals,
     * double-quoted identifiers, "--" line comments and "/* *\/" block
     * comments, and "::" (PostgreSQL's cast operator). Appends each token's
     * [start, end) index range to tokens and its name to names, in order.
     */
    private void scan(String sql, List<int[]> tokens, List<String> names) {
        int len = sql.length();
        int i = 0;
        while (i < len) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipTo(sql, i + 1, c);
            } else if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                i = skipToEndOfLine(sql, i + 2);
            } else if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                i = skipToBlockCommentEnd(sql, i + 2);
            } else if (c == ':' && i + 1 < len && sql.charAt(i + 1) == ':') {
                i += 2;
            } else if (c == ':' && i + 1 < len && isParamNameStart(sql.charAt(i + 1))) {
                int start = i;
                int j = i + 1;
                while (j < len && isParamNamePart(sql.charAt(j))) {
                    j++;
                }
                names.add(sql.substring(start + 1, j));
                tokens.add(new int[] { start, j });
                i = j;
            } else {
                i++;
            }
        }
    }

    private static int skipTo(String sql, int from, char terminator) {
        int idx = sql.indexOf(terminator, from);
        return idx < 0 ? sql.length() : idx + 1;
    }

    private static int skipToEndOfLine(String sql, int from) {
        int idx = sql.indexOf('\n', from);
        return idx < 0 ? sql.length() : idx;
    }

    private static int skipToBlockCommentEnd(String sql, int from) {
        int idx = sql.indexOf("*/", from);
        return idx < 0 ? sql.length() : idx + 2;
    }

    private static boolean isParamNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isParamNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
