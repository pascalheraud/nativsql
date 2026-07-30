package ovh.heraud.nativsql.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

/**
 * Abstract base class for query builders that support WHERE clauses.
 * Provides shared WHERE condition methods for FindQuery and DeleteQuery.
 *
 * @param <T>    the entity type implementing IEntity
 * @param <ID>   the entity ID type
 * @param <Self> the concrete subclass type for fluent chaining
 */
public abstract class WhereQuery<T extends IEntity<ID>, ID, Self extends WhereQuery<T, ID, Self>>
        implements SQLBuilder {

    protected final GenericRepository<T, ID> repository;
    protected final AnnotationManager annotationManager;
    protected final WhereClause whereClause = new WhereClause();

    protected WhereQuery(GenericRepository<T, ID> repository) {
        this.repository = repository;
        this.annotationManager = repository.getAnnotationManager();
    }

    @SuppressWarnings("unchecked")
    protected Self self() {
        return (Self) this;
    }

    /**
     * Adds a WHERE condition with EQUALS operator on root or joined entity
     * (column = value).
     */
    public Self whereAndEquals(String column, Object value) {
        guardEncryptedColumn(column);
        guardJsonColumn(column);
        whereClause.add(column, Operator.EQUALS, value);
        return self();
    }

    /**
     * Adds a WHERE condition with EQUALS operator on root entity, using a getter
     * method reference.
     */
    public Self whereAndEquals(Getter<T> getter, Object value) {
        return whereAndEquals(ReflectionUtils.getColumnName(getter), value);
    }

    /**
     * Adds a WHERE condition with IN operator on root or joined entity
     * (column IN (...)).
     */
    public Self whereAndIn(String column, List<?> values) {
        guardEncryptedColumn(column);
        guardJsonColumn(column);
        whereClause.add(column, Operator.IN, values);
        return self();
    }

    /**
     * Adds a WHERE condition with IN operator on root entity, using a getter
     * method reference.
     */
    public Self whereAndIn(Getter<T> getter, List<?> values) {
        return whereAndIn(ReflectionUtils.getColumnName(getter), values);
    }

    /**
     * Adds a custom WHERE expression (e.g., "(address).city" for composite types).
     *
     * @param expression the SQL expression (e.g., "(address).city")
     * @param paramName  the parameter name to use in the query
     * @param value      the value to bind to the parameter
     */
    public Self whereExpression(String expression, String paramName, Object value) {
        whereClause.custom(expression, paramName, value);
        return self();
    }

    /**
     * Adds a WHERE condition with an explicit operator on root or joined entity.
     *
     * @param column   the column name (camelCase)
     * @param operator the comparison operator
     * @param value    the value to compare against
     */
    public Self whereAndOperator(String column, Operator operator, Object value) {
        guardEncryptedColumn(column);
        guardJsonColumn(column);
        whereClause.add(column, operator, value);
        return self();
    }

    /**
     * Adds a WHERE condition with an explicit operator on root entity, using a
     * getter method reference.
     */
    public Self whereAndOperator(Getter<T> getter, Operator operator, Object value) {
        return whereAndOperator(ReflectionUtils.getColumnName(getter), operator, value);
    }

    /**
     * Adds a column-only WHERE condition (e.g., IS NULL, IS NOT NULL) on root or
     * joined entity.
     *
     * @param column   the column name (camelCase)
     * @param operator the column operator
     */
    public Self whereAndColumnOperator(String column, ColumnOperator operator) {
        guardEncryptedColumn(column);
        guardJsonColumn(column);
        whereClause.addColumnOperator(column, operator);
        return self();
    }

    /**
     * Adds a column-only WHERE condition on root entity, using a getter method
     * reference.
     */
    public Self whereAndColumnOperator(Getter<T> getter, ColumnOperator operator) {
        return whereAndColumnOperator(ReflectionUtils.getColumnName(getter), operator);
    }

    /**
     * Adds a BETWEEN range WHERE condition on root or joined entity.
     *
     * @param column   the column name (camelCase)
     * @param operator the range operator
     * @param low      the lower bound (required, not null)
     * @param high     the upper bound (required, not null)
     */
    public Self whereAndRange(String column, RangeOperator operator, Object low, Object high) {
        if (low == null) {
            throw new NativSQLException("Range low value cannot be null for column '" + column + "'");
        }
        if (high == null) {
            throw new NativSQLException("Range high value cannot be null for column '" + column + "'");
        }
        guardEncryptedColumn(column);
        guardJsonColumn(column);
        String camelBase = SqlUtils.columnPathToParamName(column);
        whereClause.addRangeOperator(column, operator, camelBase + "Low", low, camelBase + "High", high);
        return self();
    }

    /**
     * Adds a BETWEEN range WHERE condition on root entity, using a getter method
     * reference.
     */
    public Self whereAndRange(Getter<T> getter, RangeOperator operator, Object low, Object high) {
        return whereAndRange(ReflectionUtils.getColumnName(getter), operator, low, high);
    }

    /**
     * Checks if there are any WHERE conditions.
     */
    public boolean hasWhereConditions() {
        return !whereClause.isEmpty();
    }

    /**
     * Gets the parameters map for the SQL query.
     *
     * @return a map of parameter names to values
     */
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        for (Condition condition : whereClause.getConditions()) {
            params.put(SqlUtils.columnPathToParamName(condition.getColumn()), condition.getValue());
        }
        for (RangeCondition rc : whereClause.getRangeConditions()) {
            params.put(rc.getParamLow(), rc.getLow());
            params.put(rc.getParamHigh(), rc.getHigh());
        }
        for (CustomCondition cc : whereClause.getCustomConditions()) {
            params.put(cc.getParamName(), cc.getValue());
        }
        return params;
    }

    private void guardEncryptedColumn(String column) {
        if (column.contains(".")) {
            return; // joined entity column — encryption guard does not apply
        }
        Fields entityFields = repository.getEntityFields();
        if (entityFields == null) {
            return;
        }
        FieldAccessor<?> field = entityFields.get(column);
        TypeInfo typeInfo = annotationManager.getTypeInfo(field);
        CryptAlgorithm[] algos = (CryptAlgorithm[]) typeInfo.getParam(TypeParamKey.ALGO);
        if (algos == null) {
            return;
        }
        for (CryptAlgorithm algo : algos) {
            if (algo.isOneWay()) {
                throw new NativSQLException("Column '" + column
                        + "' uses a one-way algorithm and cannot be used in a WHERE equality check");
            }
            if (!algo.isDeterministic()) {
                throw new NativSQLException("Column '" + column
                        + "' uses a non-deterministic algorithm and cannot be used in a WHERE equality check");
            }
        }
    }

    private void guardJsonColumn(String column) {
        if (column.contains(".")) {
            return; // joined entity column — JSON guard does not apply
        }
        Fields entityFields = repository.getEntityFields();
        if (entityFields == null) {
            return;
        }
        FieldAccessor<?> field = entityFields.getOrNull(column);
        if (field == null) {
            return;
        }
        TypeInfo typeInfo = annotationManager.getTypeInfo(field);
        if (Boolean.TRUE.equals(typeInfo.getParam(TypeParamKey.JSON))) {
            throw new NativSQLException("Column '" + column
                    + "' is a JSON column and cannot be used in a standard WHERE condition; use whereExpression(...) with a JSON-specific SQL expression instead");
        }
    }
}
