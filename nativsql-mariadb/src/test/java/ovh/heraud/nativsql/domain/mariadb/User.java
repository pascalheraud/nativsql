package ovh.heraud.nativsql.domain.mariadb;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.Type;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.MappedBy;
import ovh.heraud.nativsql.annotation.OneToMany;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.mariadb.MariaDBContactInfoRepository;
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
public class User implements IEntity<Long> {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UUID externalId;
    private UserStatus status;
    private Address address;
    @Type(DbDataType.BIG_INTEGER)
    private Integer age;
    private Preferences preferences;
    // Point (PostGIS) is not applicable to MariaDB
    private Long groupId;
    @MappedBy(value = "groupId", repository = MariaDBGroupRepository.class)
    private Group group;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(
        mappedBy = "userId",
        repository = MariaDBContactInfoRepository.class
    )
    private List<ContactInfo> contacts;
}
