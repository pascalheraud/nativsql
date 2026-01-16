package io.github.pascalheraud.nativsql.repository.mariadb;

import io.github.pascalheraud.nativsql.domain.mariadb.Group;
import org.springframework.lang.NonNull;
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
