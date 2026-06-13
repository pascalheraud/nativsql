package ovh.heraud.nativsql.util;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public class DeleteQuery<T extends IEntity<ID>, ID> extends AbstractWhereQuery<T, ID, DeleteQuery<T, ID>> {

    private DeleteQuery(GenericRepository<T, ID> repository) {
        super(repository);
    }

    public static <T extends IEntity<ID>, ID> DeleteQuery<T, ID> of(GenericRepository<T, ID> repository) {
        return new DeleteQuery<>(repository);
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
}
