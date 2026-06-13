package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ColumnOperator.
 */
class ColumnOperatorTest {

    @Test
    void is_null_produces_expected_sql_fragment() {
        assertThat(ColumnOperator.IS_NULL.getExpressionBuilder().buildExpression("deleted_at"))
                .isEqualTo("deleted_at IS NULL");
    }

    @Test
    void is_not_null_produces_expected_sql_fragment() {
        assertThat(ColumnOperator.IS_NOT_NULL.getExpressionBuilder().buildExpression("deleted_at"))
                .isEqualTo("deleted_at IS NOT NULL");
    }
}
