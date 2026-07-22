package ovh.heraud.nativsql.domain.mariadb;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Report class extending {@link User} with a computed field, used to verify
 * {@code FindQuery.selectExpression(...)} and
 * {@code GenericRepository.find/findAll(query, resultClass)} against
 * MariaDB (issue #98).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserActivityReport extends User {
    private Long contactCount;
}
