package io.github.pascalheraud.nativsql.repository.mysql;

import io.github.pascalheraud.nativsql.domain.mysql.Group;
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
