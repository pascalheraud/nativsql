package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.CryptAlgo;
import ovh.heraud.nativsql.annotation.type.CryptCost;
import ovh.heraud.nativsql.annotation.type.CryptPrefix;
import ovh.heraud.nativsql.annotation.type.Encrypted;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;

/**
 * Unit tests for WHERE encryption guards in {@link FindQuery}.
 *
 * <p>
 * GCM is non-deterministic, so {@code whereAndEquals} and {@code whereAndIn}
 * must reject GCM-encrypted columns.
 * BCRYPT is one-way, so they must also be rejected.
 */
class FindQueryCryptTest {

    /** A key provider with a known key for testing. */
    public static class TestKeyProvider implements ovh.heraud.nativsql.crypt.CryptKeyProvider {
        @Override
        public byte[] getKey() {
            return "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static class CryptEntity implements IEntity<Long> {
        private Long id;

        @Encrypted
        @CryptAlgo(CryptAlgorithm.GCM)
        @ovh.heraud.nativsql.annotation.type.CryptKeyProvider(TestKeyProvider.class)
        @CryptPrefix("{ENC}")
        private String encryptedGcm;

        @Encrypted
        @CryptAlgo(CryptAlgorithm.BCRYPT)
        @CryptCost(4)
        private String encryptedBcrypt;

        private String plainField;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public String getEncryptedGcm() {
            return encryptedGcm;
        }

        public String getEncryptedBcrypt() {
            return encryptedBcrypt;
        }

        public String getPlainField() {
            return plainField;
        }
    }

    @Mock
    private GenericRepository<CryptEntity, Long> mockRepository;

    private AnnotationManager annotationManager;
    private FindQuery<CryptEntity, Long> findQuery;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        annotationManager = new AnnotationManager();
        Fields fields = ReflectionUtils.getFields(CryptEntity.class);

        when(mockRepository.getAnnotationManager()).thenReturn(annotationManager);
        when(mockRepository.getTableName()).thenReturn("crypt_entity");
        when(mockRepository.getEntityFields()).thenReturn(fields);

        findQuery = FindQuery.of(mockRepository);
    }

    @Test
    void gcmField_whereAndEquals_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndEquals("encryptedGcm", "value"))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("non-deterministic");
    }

    @Test
    void bcryptField_whereAndEquals_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndEquals("encryptedBcrypt", "password"))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("one-way");
    }

    @Test
    void plainField_whereAndEquals_allowedAndPassesValueThrough() {
        findQuery.select("id").whereAndEquals("plainField", "active");

        Map<String, Object> params = findQuery.getParameters();
        assertThat(params).containsEntry("plainField", "active");
    }

    @Test
    void unknownColumn_whereAndEquals_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndEquals("unknownColumn", "someValue"))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void gcmField_whereAndIn_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndIn("encryptedGcm", List.of("a", "b")))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("non-deterministic");
    }

    @Test
    void bcryptField_whereAndIn_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndIn("encryptedBcrypt", List.of("pass1", "pass2")))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("one-way");
    }

    @Test
    void plainField_whereAndIn_allowedAndPassesValueThrough() {
        List<String> values = List.of("ACTIVE", "INACTIVE");
        findQuery.select("id").whereAndIn("plainField", values);

        Map<String, Object> params = findQuery.getParameters();
        assertThat(params).containsEntry("plainField", values);
    }

    @Test
    void unknownColumn_whereAndIn_throwsNativSQLException() {
        findQuery.select("id");
        assertThatThrownBy(() -> findQuery.whereAndIn("unknownColumn", List.of("x", "y", "z")))
                .isInstanceOf(NativSQLException.class);
    }
}
