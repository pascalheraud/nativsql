package ovh.heraud.nativsql.repository.mysql;

import ovh.heraud.nativsql.domain.mysql.Group;

import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class MySQLGroupRepository extends MySQLRepository<Group, Long> {

    @Override
  public String getTableName() {
        return "user_group";
    }

    @Override
 protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
