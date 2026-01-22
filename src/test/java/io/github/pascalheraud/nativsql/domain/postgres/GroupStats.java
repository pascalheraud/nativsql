package io.github.pascalheraud.nativsql.domain.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for a group.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupStats {
    private Long groupId;
    private String groupName;
    private Long userCount;
    private Long activeUserCount;
    private Long emailContactCount;
}
