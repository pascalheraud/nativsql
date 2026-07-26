package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.exception.NativSQLException;

class ReflectionUtilsTest {

    @Nested
    class extractMethodName {

        @Test
        void returns_method_name_for_get_prefix_long_getter() {
            // Given: a method reference on a get-prefixed getter returning Long
            ReflectionUtils.Getter<TestEntity> getter = TestEntity::getId;

            // When: extracting the method name
            String methodName = ReflectionUtils.extractMethodName(getter);

            // Then: the full getter name is returned
            assertThat(methodName).isEqualTo("getId");
        }

        @Test
        void returns_method_name_for_get_prefix_string_getter() {
            // Given: a method reference on a get-prefixed getter returning String
            ReflectionUtils.Getter<TestEntity> getter = TestEntity::getEmail;

            // When: extracting the method name
            String methodName = ReflectionUtils.extractMethodName(getter);

            // Then: the full getter name is returned
            assertThat(methodName).isEqualTo("getEmail");
        }

        @Test
        void returns_method_name_for_is_prefix_boolean_getter() {
            // Given: a method reference on an is-prefixed boolean getter
            ReflectionUtils.Getter<TestEntity> getter = TestEntity::isActive;

            // When: extracting the method name
            String methodName = ReflectionUtils.extractMethodName(getter);

            // Then: the full getter name is returned
            assertThat(methodName).isEqualTo("isActive");
        }
    }

    @Nested
    class convertToColumnName {

        @Test
        void strips_get_prefix_and_lowercases_first_letter() {
            // Given: a getter method name with get prefix
            // When: converting to column name
            String columnName = ReflectionUtils.convertToColumnName("getId");

            // Then: prefix is removed and first letter is lowercased
            assertThat(columnName).isEqualTo("id");
        }

        @Test
        void strips_get_prefix_from_email_getter() {
            // Given / When
            String columnName = ReflectionUtils.convertToColumnName("getEmail");

            // Then
            assertThat(columnName).isEqualTo("email");
        }

        @Test
        void strips_is_prefix_from_boolean_getter() {
            // Given / When
            String columnName = ReflectionUtils.convertToColumnName("isActive");

            // Then
            assertThat(columnName).isEqualTo("active");
        }

        @Test
        void preserves_camel_case_after_prefix_removal() {
            // Given / When
            String columnName = ReflectionUtils.convertToColumnName("getCreatedAt");

            // Then
            assertThat(columnName).isEqualTo("createdAt");
        }

        @Test
        void returns_name_unchanged_when_no_standard_prefix() {
            // Given / When
            String columnName = ReflectionUtils.convertToColumnName("customMethod");

            // Then
            assertThat(columnName).isEqualTo("customMethod");
        }

        @Test
        void returns_empty_string_for_empty_input() {
            // Given / When
            String columnName = ReflectionUtils.convertToColumnName("");

            // Then
            assertThat(columnName).isEmpty();
        }
    }

    @Nested
    class getColumnName {

        @Test
        void returns_column_name_from_string_getter_reference() {
            // Given: a method reference on getEmail
            ReflectionUtils.Getter<TestEntity> getter = TestEntity::getEmail;

            // When: resolving the column name directly
            String columnName = ReflectionUtils.getColumnName(getter);

            // Then
            assertThat(columnName).isEqualTo("email");
        }

        @Test
        void returns_column_name_from_boolean_getter_reference() {
            // Given: a method reference on isActive
            ReflectionUtils.Getter<TestEntity> getter = TestEntity::isActive;

            // When
            String columnName = ReflectionUtils.getColumnName(getter);

            // Then
            assertThat(columnName).isEqualTo("active");
        }
    }

    @Nested
    class isGetter {

        @Test
        void returns_true_for_get_prefixed_method() throws NoSuchMethodException {
            // Given: a method starting with "get" returning a value
            var method = TestEntity.class.getMethod("getId");

            // When / Then
            assertThat(ReflectionUtils.isGetter(method)).isTrue();
        }

        @Test
        void returns_true_for_is_prefixed_method() throws NoSuchMethodException {
            // Given: a method starting with "is" returning boolean
            var method = TestEntity.class.getMethod("isActive");

            // When / Then
            assertThat(ReflectionUtils.isGetter(method)).isTrue();
        }

        @Test
        void returns_false_for_setter_method() throws NoSuchMethodException {
            // Given: a setter method
            var method = TestEntity.class.getMethod("setId", Long.class);

            // When / Then
            assertThat(ReflectionUtils.isGetter(method)).isFalse();
        }

        @Test
        void returns_false_for_getClass() throws NoSuchMethodException {
            // Given: Object.getClass() which is excluded by convention
            var method = Object.class.getMethod("getClass");

            // When / Then
            assertThat(ReflectionUtils.isGetter(method)).isFalse();
        }
    }

    @Nested
    class readAnnotationValue {

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        @interface SampleAnnotation {
            String value() default "hello";
        }

        @SampleAnnotation("world")
        static class AnnotatedClass {
        }

        @Test
        void returns_the_value_attribute_of_the_annotation() {
            // Given: an annotation instance with value "world"
            SampleAnnotation ann = AnnotatedClass.class.getAnnotation(SampleAnnotation.class);

            // When
            Object result = ReflectionUtils.readAnnotationValue(ann);

            // Then
            assertThat(result).isEqualTo("world");
        }
    }

    @Nested
    class instantiate {

        static class WithNoArgConstructor {
            WithNoArgConstructor() {
            }
        }

        static class WithoutNoArgConstructor {
            WithoutNoArgConstructor(String required) {
            }
        }

        @Test
        void returns_new_instance_when_no_arg_constructor_exists() {
            // Given: a class with a no-arg constructor
            // When
            Object result = ReflectionUtils.instantiate(WithNoArgConstructor.class, "myField");

            // Then
            assertThat(result).isInstanceOf(WithNoArgConstructor.class);
        }

        @Test
        void throws_nativsql_exception_when_no_arg_constructor_is_missing() {
            // Given: a class without a no-arg constructor
            // When / Then
            assertThatThrownBy(() -> ReflectionUtils.instantiate(WithoutNoArgConstructor.class, "myField"))
                    .isInstanceOf(NativSQLException.class)
                    .hasMessageContaining("myField")
                    .hasMessageContaining("no-arg constructor");
        }
    }
}
