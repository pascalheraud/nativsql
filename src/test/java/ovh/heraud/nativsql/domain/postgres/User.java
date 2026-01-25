package ovh.heraud.nativsql.domain.postgres;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.MappedBy;
import ovh.heraud.nativsql.annotation.OneToMany;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.repository.postgres.PGContactInfoRepository;
import ovh.heraud.nativsql.repository.postgres.PGGroupRepository;
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
    private Long groupId;
    @MappedBy(value = "groupId", repository = PGGroupRepository.class)
    private Group group;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(
        mappedBy = "userId",
        repository = PGContactInfoRepository.class
    )
    private List<ContactInfo> contacts;
}
