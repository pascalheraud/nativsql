package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Unit tests for SqlUtils.columnPathToParamName.
 */
class SqlUtilsColumnPathTest {

    @Test
    void plain_column_is_returned_unchanged() {
        // Given: a plain column name with no dot
        // When: converting to param name
        String result = SqlUtils.columnPathToParamName("status");

        // Then: the name is returned unchanged
        assertThat(result).isEqualTo("status");
    }

    @Test
    void one_dot_produces_camelCase_param_name() {
        // Given: a dot-notation path with a single-char column
        // When: converting to param name
        String result = SqlUtils.columnPathToParamName("group.name");

        // Then: the result is camelCase combining assoc + capitalised column
        assertThat(result).isEqualTo("groupName");
    }

    @Test
    void one_dot_with_multi_char_column_capitalises_first_letter_only() {
        // Given: a dot-notation path with a multi-char column
        // When: converting to param name
        String result = SqlUtils.columnPathToParamName("group.active");

        // Then: only the first letter of the column is uppercased
        assertThat(result).isEqualTo("groupActive");
    }

    @Test
    void two_dots_throws_NativSQLException() {
        // Given: a path with two dots (nested)
        // When / Then: NativSQLException is thrown mentioning "Nested column paths"
        assertThatThrownBy(() -> SqlUtils.columnPathToParamName("a.b.value"))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("Nested column paths are not supported");
    }

    @Test
    void empty_column_segment_throws_NativSQLException() {
        // Given: a path where the column part is empty
        // When / Then: NativSQLException is thrown mentioning "column name cannot be empty"
        assertThatThrownBy(() -> SqlUtils.columnPathToParamName("group."))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("column name cannot be empty");
    }
}
