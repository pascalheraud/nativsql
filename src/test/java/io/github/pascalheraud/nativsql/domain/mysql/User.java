package io.github.pascalheraud.nativsql.domain.mysql;

import java.time.LocalDateTime;
import java.util.List;

import io.github.pascalheraud.nativsql.annotation.OneToMany;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.mysql.MySQLContactInfoRepository;
import lombok.Data;

/**
 * User entity.
 */
@Data
public class User implements Entity<Long> {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UserStatus status;
    private Address address;
    private Preferences preferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(
        mappedBy = "userId",
        repository = MySQLContactInfoRepository.class
    )
    private List<ContactInfo> contacts;
}
