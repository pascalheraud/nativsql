package ovh.heraud.nativsql.repository.mariadb;

import ovh.heraud.nativsql.domain.mariadb.Group;

import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class MariaDBGroupRepository extends MariaDBRepository<Group, Long> {

    @Override
  public String getTableName() {
        return "user_group";
    }

    @Override
 protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
