package ovh.heraud.nativsql.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.type.CryptAlgo;
import ovh.heraud.nativsql.annotation.type.CryptCost;
import ovh.heraud.nativsql.annotation.type.CryptPrefix;
import ovh.heraud.nativsql.annotation.type.Type;
import ovh.heraud.nativsql.annotation.type.Encrypted;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Unit tests for {@link AnnotationManager#getTypeInfo(FieldAccessor)} with
 * {@code @Type(ENCRYPTED)} and crypt annotations.
 */
class AnnotationManagerCryptTest {

    private AnnotationManager annotationManager;

    @BeforeEach
    void setUp() {
        annotationManager = new AnnotationManager();
    }

    // ---- Test fixtures ----

    /** A key provider with a known key for testing. */
    public static class TestKeyProvider implements ovh.heraud.nativsql.crypt.CryptKeyProvider {
        @Override
        public byte[] getKey() {
            return "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static class EncryptedEntity {
        @Encrypted
        @CryptAlgo(CryptAlgorithm.GCM)
        @ovh.heraud.nativsql.annotation.type.CryptKeyProvider(TestKeyProvider.class)
        @CryptPrefix("{ENC}")
        String emailGcm;

        @Encrypted
        @CryptAlgo(CryptAlgorithm.BCRYPT)
        @CryptCost(4)
        String passwordBcrypt;

        @Encrypted
        @CryptAlgo({ CryptAlgorithm.GCM, CryptAlgorithm.GCM })
        @ovh.heraud.nativsql.annotation.type.CryptKeyProvider(TestKeyProvider.class)
        @CryptPrefix("{ENC}")
        String cascadeField;

        @Encrypted
        @CryptAlgo(CryptAlgorithm.GCM)
        @ovh.heraud.nativsql.annotation.type.CryptKeyProvider(TestKeyProvider.class)
        @Type(DbDataType.BYTE_ARRAY)
        String binaryField;
    }

    private FieldAccessor<?> fieldAccessor(String fieldName) throws NoSuchFieldException {
        return new FieldAccessor<>(EncryptedEntity.class.getDeclaredField(fieldName));
    }

    // ---- Tests ----

    @Test
    void gcmField_typeInfoHasCorrectDataType() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("emailGcm"));

        assertThat(typeInfo).isNotNull();
        assertThat(typeInfo.getParams()).containsKey(TypeParamKey.ENCRYPTED);
    }

    @Test
    void gcmField_typeInfoHasAlgoParam() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("emailGcm"));

        Map<ParamKey, Object> params = typeInfo.getParams();
        assertThat(params).containsKey(TypeParamKey.ALGO);
        CryptAlgorithm[] algos = (CryptAlgorithm[]) params.get(TypeParamKey.ALGO);
        assertThat(algos).hasSize(1).containsExactly(CryptAlgorithm.GCM);
    }

    @Test
    void gcmField_typeInfoHasKeyProviderResolvedToInstance() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("emailGcm"));

        Map<ParamKey, Object> params = typeInfo.getParams();
        assertThat(params).containsKey(TypeParamKey.KEY_PROVIDER);
        Object keyProvider = params.get(TypeParamKey.KEY_PROVIDER);
        assertThat(keyProvider).isInstanceOf(ovh.heraud.nativsql.crypt.CryptKeyProvider.class);

        // Verify the key provider returns the expected key
        byte[] key = ((ovh.heraud.nativsql.crypt.CryptKeyProvider) keyProvider).getKey();
        assertThat(key).hasSize(16);
    }

    @Test
    void gcmField_typeInfoHasPrefixParam() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("emailGcm"));

        Map<ParamKey, Object> params = typeInfo.getParams();
        assertThat(params).containsEntry(TypeParamKey.PREFIX, "{ENC}");
    }

    @Test
    void bcryptField_typeInfoHasAlgoAndCostParams() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("passwordBcrypt"));

        assertThat(typeInfo).isNotNull();
        assertThat(typeInfo.getParams()).containsKey(TypeParamKey.ENCRYPTED);

        Map<ParamKey, Object> params = typeInfo.getParams();
        CryptAlgorithm[] algos = (CryptAlgorithm[]) params.get(TypeParamKey.ALGO);
        assertThat(algos).containsExactly(CryptAlgorithm.BCRYPT);
        assertThat(params.get(TypeParamKey.COST)).isEqualTo(4);
    }

    @Test
    void cascadeField_typeInfoHasMultipleAlgos() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("cascadeField"));

        CryptAlgorithm[] algos = (CryptAlgorithm[]) typeInfo.getParams().get(TypeParamKey.ALGO);
        assertThat(algos).hasSize(2);
    }

    @Test
    void binaryField_typeInfoHasByteArrayDataType() throws Exception {
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor("binaryField"));

        assertThat(typeInfo).isNotNull();
        Map<ParamKey, Object> params = typeInfo.getParams();
        assertThat(params).containsEntry(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);
    }

    @Test
    void fieldWithoutAnnotations_returnsEmptyTypeInfo() throws Exception {
        class PlainEntity {
            @SuppressWarnings("unused")
            String name;
        }
        FieldAccessor<?> fa = new FieldAccessor<>(PlainEntity.class.getDeclaredField("name"));
        TypeInfo typeInfo = annotationManager.getTypeInfo(fa);
        assertThat(typeInfo).isNotNull();
        assertThat(typeInfo.getParams()).isEmpty();
    }
}
