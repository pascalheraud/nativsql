package ovh.heraud.nativsql.repository.mariadb;

import ovh.heraud.nativsql.domain.mariadb.Group;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class MariaDBGroupRepository extends MariaDBRepository<Group, Long> {

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
