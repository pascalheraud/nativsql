package ovh.heraud.nativsql.util;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public class CountQuery<T extends IEntity<ID>, ID> extends AbstractWhereQuery<T, ID, CountQuery<T, ID>> {

    private CountQuery(GenericRepository<T, ID> repository) {
        super(repository);
    }

    public static <T extends IEntity<ID>, ID> CountQuery<T, ID> of(GenericRepository<T, ID> repository) {
        return new CountQuery<>(repository);
    }

    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        String tableName = repository.getTableName();
        sb.append("SELECT COUNT(*) FROM ").append(tableName);
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
