package ovh.heraud.nativsql.domain.mariadb;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.postgis.Point;

import ovh.heraud.nativsql.annotation.MappedBy;
import ovh.heraud.nativsql.annotation.OneToMany;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.repository.mysql.MySQLContactInfoRepository;
import ovh.heraud.nativsql.repository.mariadb.MariaDBGroupRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Entity<Long> {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UUID externalId;
    private UserStatus status;
    private Address address;
    private Preferences preferences;
    private Point position;
    private Long groupId;
    @MappedBy(value = "groupId", repository = MariaDBGroupRepository.class)
    private Group group;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(
        mappedBy = "userId",
        repository = MySQLContactInfoRepository.class
    )
    private List<ContactInfo> contacts;
}
