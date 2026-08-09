package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NullableParamTest {

    @Test
    void ofWithNullValue_hasTypeOnly() {
        NullableParam param = NullableParam.of(Boolean.class, null);

        assertThat(param.getType()).isEqualTo(Boolean.class);
        assertThat(param.getValue()).isNull();
    }

    @Test
    void ofWithNonNullValue_keepsTypeAndValue() {
        NullableParam param = NullableParam.of(Boolean.class, true);

        assertThat(param.getType()).isEqualTo(Boolean.class);
        assertThat(param.getValue()).isEqualTo(true);
    }
}
