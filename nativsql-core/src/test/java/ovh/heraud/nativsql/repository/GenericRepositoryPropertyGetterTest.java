package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.OrderBy;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

/**
 * Unit tests for the getter-based overloads of byProperty methods in
 * GenericRepository.
 *
 * <p>Verifies that passing a getter method reference as the filter property
 * (e.g., {@code User::getEmail}) is strictly equivalent to passing the
 * corresponding camelCase property name as a {@code String} (e.g.,
 * {@code "email"}).
 *
 * <p>Tests use Mockito to stub the underlying string-based methods and AssertJ
 * to assert both the return value and the exact delegation target.
 */
@ExtendWith(MockitoExtension.class)
class GenericRepositoryPropertyGetterTest {

    // ==================== Test fixtures ====================

    static class TestUser implements IEntity<Long> {
        private Long id;
        private String email;
        private String status;
        private String firstName;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public String getStatus() {
            return status;
        }

        public String getFirstName() {
            return firstName;
        }
    }

    static class TestUserRepository extends GenericRepository<TestUser, Long> {

        TestUserRepository(RowMapperFactory rowMapperFactory, AnnotationManager annotationManager) {
            super(TestUser.class, "test_user", rowMapperFactory, annotationManager, new DbOperationLogger());
        }

        @Override
        protected DataSource getDataSource() {
            throw new UnsupportedOperationException("No DataSource in unit tests");
        }

        @Override
        protected Class<TestUser> getEntityClass() {
            return TestUser.class;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return null;
        }
    }

    @Mock
    private RowMapperFactory rowMapperFactory;

    @Mock
    private AnnotationManager annotationManager;

    private TestUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = spy(new TestUserRepository(rowMapperFactory, annotationManager));
    }

    // ==================== findByProperty ====================

    @Nested
    class FindByPropertyTests {

        @Test
        void withGetterAndStringColumns_delegatesToStringBasedMethod() {
            TestUser expected = new TestUser();
            doReturn(expected).when(repository).findByProperty("email", "alice@example.com", "id", "email");

            TestUser result = repository.findByProperty(TestUser::getEmail, "alice@example.com", "id", "email");

            assertThat(result).isSameAs(expected);
            verify(repository).findByProperty("email", "alice@example.com", "id", "email");
        }

        @Test
        void withGetterAndStringColumns_resolvesMultiWordProperty() {
            TestUser expected = new TestUser();
            doReturn(expected).when(repository).findByProperty("firstName", "Alice", "id", "firstName");

            TestUser result = repository.findByProperty(TestUser::getFirstName, "Alice", "id", "firstName");

            assertThat(result).isSameAs(expected);
            verify(repository).findByProperty("firstName", "Alice", "id", "firstName");
        }

        @Test
        void withGetterAndStringColumns_returnsNullWhenNoEntityFound() {
            doReturn(null).when(repository).findByProperty("email", "unknown@example.com", "id");

            TestUser result = repository.findByProperty(TestUser::getEmail, "unknown@example.com", "id");

            assertThat(result).isNull();
            verify(repository).findByProperty("email", "unknown@example.com", "id");
        }

        @SuppressWarnings("unchecked")
        @Test
        void withGetterAndGetterColumns_delegatesToGetterBasedMethod() {
            TestUser expected = new TestUser();
            Getter<TestUser> idGetter = TestUser::getId;
            Getter<TestUser> emailGetter = TestUser::getEmail;
            doReturn(expected).when(repository).findByProperty("email", "alice@example.com", idGetter, emailGetter);

            TestUser result = repository.findByProperty(TestUser::getEmail, "alice@example.com", idGetter, emailGetter);

            assertThat(result).isSameAs(expected);
            verify(repository).findByProperty("email", "alice@example.com", idGetter, emailGetter);
        }
    }

    // ==================== findAllByProperty (single value) ====================

    @Nested
    class FindAllByPropertySingleValueTests {

        @Test
        void withGetterAndStringColumns_delegatesToStringBasedMethod() {
            List<TestUser> expected = Arrays.asList(new TestUser(), new TestUser());
            doReturn(expected).when(repository).findAllByProperty("status", "ACTIVE", "id", "email", "status");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "ACTIVE", "id", "email", "status");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", "ACTIVE", "id", "email", "status");
        }

        @Test
        void withGetterAndStringColumns_returnsEmptyListWhenNoEntitiesFound() {
            doReturn(List.of()).when(repository).findAllByProperty("status", "INACTIVE", "id");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "INACTIVE", "id");

            assertThat(result).isEmpty();
            verify(repository).findAllByProperty("status", "INACTIVE", "id");
        }

        @Test
        void withGetterAndStringColumns_resolvesMultiWordProperty() {
            List<TestUser> expected = List.of(new TestUser());
            doReturn(expected).when(repository).findAllByProperty("firstName", "Alice", "id", "firstName");

            List<TestUser> result = repository.findAllByProperty(TestUser::getFirstName, "Alice", "id", "firstName");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("firstName", "Alice", "id", "firstName");
        }

        @SuppressWarnings("unchecked")
        @Test
        void withGetterAndGetterColumns_delegatesToGetterBasedMethod() {
            List<TestUser> expected = List.of(new TestUser());
            Getter<TestUser> idGetter = TestUser::getId;
            Getter<TestUser> statusGetter = TestUser::getStatus;
            doReturn(expected).when(repository).findAllByProperty("status", "ACTIVE", idGetter, statusGetter);

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "ACTIVE", idGetter, statusGetter);

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", "ACTIVE", idGetter, statusGetter);
        }
    }

    // ==================== findAllByProperty (single value + OrderBy) ====================

    @Nested
    class FindAllByPropertyWithOrderByTests {

        @Test
        void withGetterAndStringColumns_delegatesToStringBasedMethod() {
            List<TestUser> expected = Arrays.asList(new TestUser(), new TestUser());
            OrderBy orderBy = new OrderBy().asc("email");
            doReturn(expected).when(repository).findAllByProperty("status", "ACTIVE", orderBy, "id", "email");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "ACTIVE", orderBy, "id", "email");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", "ACTIVE", orderBy, "id", "email");
        }

        @Test
        void withGetterAndStringColumns_returnsEmptyListWhenNoEntitiesFound() {
            OrderBy orderBy = new OrderBy().desc("id");
            doReturn(List.of()).when(repository).findAllByProperty("status", "DELETED", orderBy, "id");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "DELETED", orderBy, "id");

            assertThat(result).isEmpty();
            verify(repository).findAllByProperty("status", "DELETED", orderBy, "id");
        }

        @SuppressWarnings("unchecked")
        @Test
        void withGetterAndGetterColumns_delegatesToGetterBasedMethod() {
            List<TestUser> expected = List.of(new TestUser());
            OrderBy orderBy = new OrderBy().asc("id");
            Getter<TestUser> idGetter = TestUser::getId;
            Getter<TestUser> emailGetter = TestUser::getEmail;
            doReturn(expected).when(repository).findAllByProperty("status", "ACTIVE", orderBy, idGetter, emailGetter);

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, "ACTIVE", orderBy, idGetter,
                    emailGetter);

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", "ACTIVE", orderBy, idGetter, emailGetter);
        }
    }

    // ==================== findAllByProperty (list of values / IN clause) ====================

    @Nested
    class FindAllByPropertyListTests {

        @Test
        void withGetterAndStringColumns_delegatesToStringBasedMethod() {
            List<TestUser> expected = Arrays.asList(new TestUser(), new TestUser());
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING");
            doReturn(expected).when(repository).findAllByProperty("status", statuses, "id", "status");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, statuses, "id", "status");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", statuses, "id", "status");
        }

        @Test
        void withGetterAndStringColumns_returnsEmptyListForEmptyValues() {
            List<String> statuses = Arrays.asList("UNKNOWN1", "UNKNOWN2");
            doReturn(List.of()).when(repository).findAllByProperty("status", statuses, "id");

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, statuses, "id");

            assertThat(result).isEmpty();
            verify(repository).findAllByProperty("status", statuses, "id");
        }

        @SuppressWarnings("unchecked")
        @Test
        void withGetterAndGetterColumns_delegatesToGetterBasedMethod() {
            List<TestUser> expected = List.of(new TestUser());
            List<String> statuses = List.of("ACTIVE");
            Getter<TestUser> idGetter = TestUser::getId;
            Getter<TestUser> statusGetter = TestUser::getStatus;
            doReturn(expected).when(repository).findAllByProperty("status", statuses, idGetter, statusGetter);

            List<TestUser> result = repository.findAllByProperty(TestUser::getStatus, statuses, idGetter, statusGetter);

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByProperty("status", statuses, idGetter, statusGetter);
        }
    }

    // ==================== findAllByPropertyIn ====================

    @Nested
    class FindAllByPropertyInTests {

        @Test
        void withGetterAndStringColumns_delegatesToStringBasedMethod() {
            List<TestUser> expected = Arrays.asList(new TestUser(), new TestUser(), new TestUser());
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING", "SUSPENDED");
            doReturn(expected).when(repository).findAllByPropertyIn("status", statuses, "id", "email", "status");

            List<TestUser> result = repository.findAllByPropertyIn(TestUser::getStatus, statuses, "id", "email",
                    "status");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByPropertyIn("status", statuses, "id", "email", "status");
        }

        @Test
        void withGetterAndStringColumns_returnsEmptyListWhenNoMatch() {
            List<Long> ids = Arrays.asList(999L, 1000L);
            doReturn(List.of()).when(repository).findAllByPropertyIn("id", ids, "id");

            List<TestUser> result = repository.findAllByPropertyIn(TestUser::getId, ids, "id");

            assertThat(result).isEmpty();
            verify(repository).findAllByPropertyIn("id", ids, "id");
        }

        @Test
        void withGetterAndStringColumns_resolvesMultiWordProperty() {
            List<TestUser> expected = List.of(new TestUser());
            List<String> names = List.of("Alice", "Bob");
            doReturn(expected).when(repository).findAllByPropertyIn("firstName", names, "id", "firstName");

            List<TestUser> result = repository.findAllByPropertyIn(TestUser::getFirstName, names, "id", "firstName");

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByPropertyIn("firstName", names, "id", "firstName");
        }

        @SuppressWarnings("unchecked")
        @Test
        void withGetterAndGetterColumns_delegatesToGetterBasedMethod() {
            List<TestUser> expected = List.of(new TestUser(), new TestUser());
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING");
            Getter<TestUser> idGetter = TestUser::getId;
            Getter<TestUser> statusGetter = TestUser::getStatus;
            doReturn(expected).when(repository).findAllByPropertyIn("status", statuses, idGetter, statusGetter);

            List<TestUser> result = repository.findAllByPropertyIn(TestUser::getStatus, statuses, idGetter,
                    statusGetter);

            assertThat(result).isSameAs(expected);
            verify(repository).findAllByPropertyIn("status", statuses, idGetter, statusGetter);
        }
    }
}
