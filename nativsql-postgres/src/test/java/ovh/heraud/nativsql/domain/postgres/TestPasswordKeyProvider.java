package ovh.heraud.nativsql.domain.postgres;

import ovh.heraud.nativsql.crypt.CryptKeyProvider;

public class TestPasswordKeyProvider implements CryptKeyProvider {

    // 32-byte key for AES-256 — test only, never use hardcoded keys in production
    private static final byte[] KEY = "NativSQLTestKey_32BytesForAES256".getBytes();

    @Override
    public byte[] getKey() {
        return KEY;
    }
}
