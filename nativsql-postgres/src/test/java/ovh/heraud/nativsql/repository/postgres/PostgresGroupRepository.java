package ovh.heraud.nativsql.repository.postgres;

import ovh.heraud.nativsql.domain.postgres.Group;

import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities.
 */
@Repository
public class PostgresGroupRepository extends PostgresRepository<Group, Long> {

    @Override
  public String getTableName() {
        return "user_group";
    }

    @Override
  protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
