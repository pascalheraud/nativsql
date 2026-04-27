package ovh.heraud.nativsql.repository.oracle;

import ovh.heraud.nativsql.domain.oracle.Group;

import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities using Oracle.
 */
@Repository
public class OracleGroupRepository extends OracleRepository<Group, Long> {

    @Override
  public String getTableName() {
        return "user_group";
    }

    @Override
 protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
