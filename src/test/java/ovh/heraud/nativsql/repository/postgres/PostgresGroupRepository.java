package ovh.heraud.nativsql.repository.postgres;

import ovh.heraud.nativsql.domain.postgres.Group;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class PostgresGroupRepository extends PostgresRepository<Group, Long> {

    @Override
    @NonNull
    protected String getTableName() {
        return "user_group";
    }

    @Override
    @NonNull
    protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
