package ovh.heraud.nativsql.repository.oracle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for exists() / ExistsQuery on Oracle, using the default
 * supported Oracle version (23).
 */
@Import({ OracleUserRepository.class })
class OracleExistsQueryTest extends OracleExistsQueryTestBase {

    @Autowired
    private OracleUserRepository userRepository;

    @Override
    protected OracleUserRepository getUserRepository() {
        return userRepository;
    }
}
