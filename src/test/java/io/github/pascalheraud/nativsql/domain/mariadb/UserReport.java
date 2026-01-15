package io.github.pascalheraud.nativsql.domain.mariadb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User statistics report.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {
    private long totalUsers;
    private long usersWithEmailContact;
    private long usersWithFrenchPreference;
}
