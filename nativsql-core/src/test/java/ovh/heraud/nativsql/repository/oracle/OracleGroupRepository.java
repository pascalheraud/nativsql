package ovh.heraud.nativsql.repository.oracle;

import ovh.heraud.nativsql.domain.oracle.Group;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for Group entities using Oracle.
 */
@Repository
public class OracleGroupRepository extends OracleRepository<Group, Long> {

    @Override
    @NonNull
    public String getTableName() {
        return "user_group";
    }

    @Override
    @NonNull
    protected Class<Group> getEntityClass() {
        return Group.class;
    }
}
