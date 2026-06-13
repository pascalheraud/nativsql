package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the new Operator constants added in the operators feature.
 */
class OperatorTest {

    @Test
    void less_than_produces_expected_sql_fragment() {
        String result = Operator.LESS_THAN.getExpressionBuilder().buildExpression("age", "age");
        assertThat(result).isEqualTo("age < :age");
    }

    @Test
    void less_or_equal_produces_expected_sql_fragment() {
        String result = Operator.LESS_OR_EQUAL.getExpressionBuilder().buildExpression("age", "age");
        assertThat(result).isEqualTo("age <= :age");
    }

    @Test
    void greater_than_produces_expected_sql_fragment() {
        String result = Operator.GREATER_THAN.getExpressionBuilder().buildExpression("age", "age");
        assertThat(result).isEqualTo("age > :age");
    }

    @Test
    void greater_or_equal_produces_expected_sql_fragment() {
        String result = Operator.GREATER_OR_EQUAL.getExpressionBuilder().buildExpression("age", "age");
        assertThat(result).isEqualTo("age >= :age");
    }

    @Test
    void not_equals_produces_expected_sql_fragment() {
        String result = Operator.NOT_EQUALS.getExpressionBuilder().buildExpression("status", "status");
        assertThat(result).isEqualTo("status <> :status");
    }

    @Test
    void like_produces_expected_sql_fragment() {
        String result = Operator.LIKE.getExpressionBuilder().buildExpression("name", "name");
        assertThat(result).isEqualTo("name LIKE :name");
    }
}
