package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ReflectionUtils, especially for method reference handling.
 */
@DisplayName("ReflectionUtils Tests")
class ReflectionUtilsTest {
    // ============ Tests for extractMethodName ============

    @Test
    @DisplayName("Should extract method name from getter starting with 'get'")
    void testExtractMethodNameWithGetPrefix() {
        String methodName = ReflectionUtils.extractMethodName(TestEntity::getId);
        assertThat(methodName).isEqualTo("getId");
    }

    @Test
    @DisplayName("Should extract method name from getter starting with 'get' (String property)")
    void testExtractMethodNameWithGetPrefixString() {
        String methodName = ReflectionUtils.extractMethodName(TestEntity::getEmail);
        assertThat(methodName).isEqualTo("getEmail");
    }

    @Test
    @DisplayName("Should extract method name from boolean getter starting with 'is'")
    void testExtractMethodNameWithIsPrefix() {
        String methodName = ReflectionUtils.extractMethodName(TestEntity::isActive);
        assertThat(methodName).isEqualTo("isActive");
    }

    // ============ Tests for convertToColumnName ============

    @Test
    @DisplayName("Should convert 'getId' to 'id'")
    void testConvertGetIdToColumnName() {
        String columnName = ReflectionUtils.convertToColumnName("getId");
        assertThat(columnName).isEqualTo("id");
    }

    @Test
    @DisplayName("Should convert 'getEmail' to 'email'")
    void testConvertGetEmailToColumnName() {
        String columnName = ReflectionUtils.convertToColumnName("getEmail");
        assertThat(columnName).isEqualTo("email");
    }

    @Test
    @DisplayName("Should convert 'isActive' to 'active'")
    void testConvertIsActiveToColumnName() {
        String columnName = ReflectionUtils.convertToColumnName("isActive");
        assertThat(columnName).isEqualTo("active");
    }

    @Test
    @DisplayName("Should convert 'getCreatedAt' to 'createdAt'")
    void testConvertGetCreatedAtToColumnName() {
        String columnName = ReflectionUtils.convertToColumnName("getCreatedAt");
        assertThat(columnName).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("Should handle method name without standard prefix")
    void testConvertMethodNameWithoutPrefix() {
        String columnName = ReflectionUtils.convertToColumnName("customMethod");
        assertThat(columnName).isEqualTo("customMethod");
    }

    @Test
    @DisplayName("Should handle empty string")
    void testConvertEmptyString() {
        String columnName = ReflectionUtils.convertToColumnName("");
        assertThat(columnName).isEmpty();
    }

    // ============ Integration Tests ============

    @Test
    @DisplayName("Should get column name directly from getter reference (getEmail)")
    void testGetColumnNameFromGetter() {
        String columnName = ReflectionUtils.getColumnName(TestEntity::getEmail);
        assertThat(columnName).isEqualTo("email");
    }

    @Test
    @DisplayName("Should get column name directly from getter reference (isActive)")
    void testGetColumnNameFromBooleanGetter() {
        String columnName = ReflectionUtils.getColumnName(TestEntity::isActive);
        assertThat(columnName).isEqualTo("active");
    }

    // ============ Tests for isGetter (existing method) ============

    @Test
    @DisplayName("Should identify 'get' methods as getters")
    void testIsGetterWithGetMethod() throws NoSuchMethodException {
        var method = TestEntity.class.getMethod("getId");
        assertThat(ReflectionUtils.isGetter(method)).isTrue();
    }

    @Test
    @DisplayName("Should identify 'is' methods as getters")
    void testIsGetterWithIsMethod() throws NoSuchMethodException {
        var method = TestEntity.class.getMethod("isActive");
        assertThat(ReflectionUtils.isGetter(method)).isTrue();
    }

    @Test
    @DisplayName("Should not identify setter methods as getters")
    void testIsGetterWithSetterMethod() throws NoSuchMethodException {
        var method = TestEntity.class.getMethod("setId", Long.class);
        assertThat(ReflectionUtils.isGetter(method)).isFalse();
    }

    @Test
    @DisplayName("Should not identify 'getClass' as a getter")
    void testIsGetterWithGetClassMethod() throws NoSuchMethodException {
        var method = Object.class.getMethod("getClass");
        assertThat(ReflectionUtils.isGetter(method)).isFalse();
    }
}
