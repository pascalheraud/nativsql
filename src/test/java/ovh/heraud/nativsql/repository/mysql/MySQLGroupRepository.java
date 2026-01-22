package ovh.heraud.nativsql.repository.mysql;

import ovh.heraud.nativsql.domain.mysql.Group;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class MySQLGroupRepository extends MySQLRepository<Group, Long> {

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
