package ovh.heraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ovh.heraud.nativsql.domain.postgres.ContactInfo;
import ovh.heraud.nativsql.domain.postgres.ContactType;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for ContactInfoRepository using Testcontainers.
 */
@Import({ PGUserRepository.class, PGContactInfoRepository.class })
class PGContactInfoRepositoryTest  extends PGRepositoryTest{
    @Autowired
    private PGContactInfoRepository contactInfoRepository;

    @Autowired
    private PGUserRepository userRepository;

    private Long createTestUser() {
        User user = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("john@example.com", "id");
        return found.getId();
    }

    @Test
    void testInsertContactInfo() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo contact = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .isPrimary(true)
                .build();

        // When
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        List<ContactInfo> contacts = contactInfoRepository.findByUserId(testUserId, "id", "userId", "contactType",
                "contactValue", "isPrimary");
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getContactType()).isEqualTo(ContactType.EMAIL);
        assertThat(contacts.get(0).getContactValue()).isEqualTo("john@work.com");
        assertThat(contacts.get(0).getIsPrimary()).isTrue();
    }

    @Test
    void testInsertMultipleContacts() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .isPrimary(true)
                .build();

        ContactInfo phone = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.PHONE)
                .contactValue("+33612345678")
                .isPrimary(false)
                .build();

        ContactInfo linkedin = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.LINKEDIN)
                .contactValue("linkedin.com/in/johndoe")
                .isPrimary(false)
                .build();

        // When
        contactInfoRepository.insert(email, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(linkedin, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        List<ContactInfo> allContacts = contactInfoRepository.findByUserId(testUserId, "id", "contactType",
                "contactValue", "isPrimary");
        assertThat(allContacts).hasSize(3);
    }

    @Test
    void testFindByUserIdAndType() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email1 = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .isPrimary(true)
                .build();

        ContactInfo email2 = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@personal.com")
                .isPrimary(false)
                .build();

        ContactInfo phone = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.PHONE)
                .contactValue("+33612345678")
                .isPrimary(false)
                .build();

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");

        // When
        List<ContactInfo> emails = contactInfoRepository.findByUserIdAndType(testUserId, ContactType.EMAIL, "id",
                "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(emails).hasSize(2);
        assertThat(emails).allMatch(c -> c.getContactType() == ContactType.EMAIL);
    }

    @Test
    void testFindPrimaryByUserIdAndType() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email1 = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .isPrimary(true)
                .build();

        ContactInfo email2 = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@personal.com")
                .isPrimary(false)
                .build();

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");

        // When
        ContactInfo primaryEmail = contactInfoRepository.findPrimaryByUserIdAndType(testUserId, ContactType.EMAIL, "id",
                "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(primaryEmail).isNotNull();
        assertThat(primaryEmail.getContactValue()).isEqualTo("john@work.com");
        assertThat(primaryEmail.getIsPrimary()).isTrue();
    }

    @Test
    void testDeleteContact() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo contact = ContactInfo.builder()
                .userId(testUserId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .build();
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue");

        List<ContactInfo> contacts = contactInfoRepository.findByUserId(testUserId, "id");
        assertThat(contacts).hasSize(1);

        // When
        int rows = contactInfoRepository.delete(contacts.get(0));

        // Then
        assertThat(rows).isEqualTo(1);
        List<ContactInfo> afterDelete = contactInfoRepository.findByUserId(testUserId, "id");
        assertThat(afterDelete).isEmpty();
    }
}
