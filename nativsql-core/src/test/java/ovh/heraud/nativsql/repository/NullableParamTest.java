package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NullableParamTest {

    @Test
    void ofClass_hasNoValue() {
        NullableParam param = NullableParam.of(Boolean.class);

        assertThat(param.getType()).isEqualTo(Boolean.class);
        assertThat(param.hasValue()).isFalse();
        assertThat(param.getValue()).isNull();
    }

    @Test
    void ofValue_infersTypeAndKeepsValue() {
        NullableParam param = NullableParam.of(true);

        assertThat(param.getType()).isEqualTo(Boolean.class);
        assertThat(param.hasValue()).isTrue();
        assertThat(param.getValue()).isEqualTo(true);
    }

    @Test
    void ofValue_rejectsNull() {
        assertThatThrownBy(() -> NullableParam.of((Object) null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
