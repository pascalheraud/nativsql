package ovh.heraud.nativsql.repository.postgres;

import ovh.heraud.nativsql.domain.postgres.Group;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class PGGroupRepository extends PGRepository<Group, Long> {

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
