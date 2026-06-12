package ovh.heraud.nativsql.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

public class DeleteQuery<T extends IEntity<ID>, ID> implements SQLBuilder {
    private final GenericRepository<T, ID> repository;
    private final AnnotationManager annotationManager;
    private final WhereClause whereClause = new WhereClause();

    private DeleteQuery(GenericRepository<T, ID> repository) {
        this.repository = repository;
        this.annotationManager = repository.getAnnotationManager();
    }

    public static <T extends IEntity<ID>, ID> DeleteQuery<T, ID> of(GenericRepository<T, ID> repository) {
        return new DeleteQuery<>(repository);
    }

    public DeleteQuery<T, ID> whereAndEquals(String column, Object value) {
        guardEncryptedColumn(column);
        whereClause.add(column, Operator.EQUALS, value);
        return this;
    }

    public DeleteQuery<T, ID> whereAndEquals(Getter<T> getter, Object value) {
        return whereAndEquals(ReflectionUtils.getColumnName(getter), value);
    }

    public DeleteQuery<T, ID> whereAndIn(String column, List<?> values) {
        guardEncryptedColumn(column);
        whereClause.add(column, Operator.IN, values);
        return this;
    }

    public DeleteQuery<T, ID> whereAndIn(Getter<T> getter, List<?> values) {
        return whereAndIn(ReflectionUtils.getColumnName(getter), values);
    }

    public DeleteQuery<T, ID> whereExpression(String expression, String paramName, Object value) {
        whereClause.custom(expression, paramName);
        whereClause.add(paramName, Operator.EQUALS, value);
        return this;
    }

    private void guardEncryptedColumn(String column) {
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

    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        String tableName = repository.getTableName();
        sb.append("DELETE FROM ").append(tableName);
        if (hasWhereConditions()) {
            sb.append("\nWHERE\n");
            whereClause.buildFormatted(sb, identifierConverter);
        }
        sb.append("\n");
    }

    public String buildString(IdentifierConverter identifierConverter) {
        StringBuilder sb = new StringBuilder();
        build(sb, identifierConverter);
        return sb.toString();
    }

    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        for (Condition condition : whereClause.getConditions()) {
            params.put(condition.getColumn(), condition.getValue());
        }
        return params;
    }

    public boolean hasWhereConditions() {
        return !whereClause.isEmpty();
    }
}
