package ovh.heraud.nativsql.util;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public class ExistsQuery<T extends IEntity<ID>, ID> extends WhereQuery<T, ID, ExistsQuery<T, ID>> {

    private ExistsQuery(GenericRepository<T, ID> repository) {
        super(repository);
    }

    public static <T extends IEntity<ID>, ID> ExistsQuery<T, ID> of(GenericRepository<T, ID> repository) {
        return new ExistsQuery<>(repository);
    }

    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        String tableName = repository.getTableName();
        sb.append("SELECT 1 FROM ").append(tableName);
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
}
