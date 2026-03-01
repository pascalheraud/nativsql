package ovh.heraud.nativsql.repository;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.domain.data.DataTypeBigDecimal;
import ovh.heraud.nativsql.domain.data.DataTypeBigInteger;
import ovh.heraud.nativsql.domain.data.DataTypeBoolean;
import ovh.heraud.nativsql.domain.data.DataTypeByte;
import ovh.heraud.nativsql.domain.data.DataTypeByteArray;
import ovh.heraud.nativsql.domain.data.DataTypeDouble;
import ovh.heraud.nativsql.domain.data.DataTypeFloat;
import ovh.heraud.nativsql.domain.data.DataTypeInteger;
import ovh.heraud.nativsql.domain.data.DataTypeLocalDate;
import ovh.heraud.nativsql.domain.data.DataTypeLocalDateTime;
import ovh.heraud.nativsql.domain.data.DataTypeLong;
import ovh.heraud.nativsql.domain.data.DataTypeShort;
import ovh.heraud.nativsql.domain.data.DataTypeString;
import ovh.heraud.nativsql.domain.data.DataTypeUUID;
import ovh.heraud.nativsql.domain.data.IData;
import ovh.heraud.nativsql.domain.data.IDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.RowMapperFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("unchecked")
public interface IDataTypeTests {
        String[] BOOLEAN_FALSE_STRINGS = { "false", "f", "no", "n" };
        String[] BOOLEAN_TRUE_STRINGS = { "true", "t", "yes", "y" };

        AnnotationManager getAnnotationManager();

        DataSource getDataSource();

        DatabaseDialect getDatabaseDialect();

        RowMapperFactory getRowMapperFactory();

        @BeforeEach
        default void setUp() {
                // Reset annotation manager mappings before each test
                getAnnotationManager().clearCache();
        }

        default <I, T> T testIt(Class<?> valueToInsertDataTypeClass, I valueToInsert, Class<?> expectedDataTypeClass,
                        String tableName, T expectedValue, DbDataType writingDataType)
                        throws TestError {
                try {
                        // Create repo for insertion
                        DataTypeRepository<IEntity<Long>> insertRepo = getRepo(valueToInsertDataTypeClass, tableName);
                        // Insert the value
                        IDataType<I> data = (IDataType<I>) valueToInsertDataTypeClass.getDeclaredConstructor()
                                        .newInstance();
                        // Convert the input value to the expected type for the domain class
                        Object convertedValue = convertToExpectedType(valueToInsert, valueToInsertDataTypeClass);
                        data.setData((I) convertedValue);
                        // Register the dataType for writing conversion if specified
                        if (writingDataType != null) {
                                getAnnotationManager().setTypeInfo(valueToInsertDataTypeClass, "data", writingDataType);
                        }

                        insertRepo.insertData(data);

                        // Read with conversion: create repo with expectedDataTypeClass type to handle
                        // type conversion
                        // Use the same table name where we inserted and the same ID
                        DataTypeRepository<IEntity<Long>> readRepo = new DataTypeRepository<>(
                                        (Class<IEntity<Long>>) expectedDataTypeClass,
                                        tableName,
                                        getDataSource(),
                                        getDatabaseDialect(),
                                        getRowMapperFactory(),
                                        getAnnotationManager());
                        IEntity<Long> retrieved = readRepo.findById(data.getId(), "data");
                        return ((IData<T>) retrieved).getData();
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                        throw new TestError("Error in testIt: " + e.getMessage(), e);
                }
        }

        default DataTypeRepository<IEntity<Long>> getRepo(Class<?> valueToInsertDataTypeClass,
                        String tableName) {
                DataTypeRepository<IEntity<Long>> insertRepo = new DataTypeRepository<>(
                                (Class<IEntity<Long>>) valueToInsertDataTypeClass,
                                tableName,
                                getDataSource(),
                                getDatabaseDialect(),
                                getRowMapperFactory(),
                                getAnnotationManager());
                return insertRepo;
        }

        /**
         * Converts a value to the expected Java type based on the domain class.
         * Handles conversion between different date/time representations.
         */
        default Object convertToExpectedType(Object value, Class<?> domainClass) {
                if (value == null) {
                        return null;
                }

                // Handle LocalDateTime domain class
                if (DataTypeLocalDateTime.class.isAssignableFrom(domainClass)) {
                        if (value instanceof LocalDateTime) {
                                return value;
                        } else if (value instanceof java.sql.Timestamp) {
                                return ((java.sql.Timestamp) value).toLocalDateTime();
                        } else if (value instanceof Date) {
                                // Convert java.util.Date to LocalDateTime
                                return new java.sql.Timestamp(((Date) value).getTime()).toLocalDateTime();
                        }
                }

                // Handle LocalDate domain class
                if (DataTypeLocalDate.class.isAssignableFrom(domainClass)) {
                        if (value instanceof LocalDate) {
                                return value;
                        } else if (value instanceof java.sql.Date) {
                                return ((java.sql.Date) value).toLocalDate();
                        } else if (value instanceof Date) {
                                // Convert java.util.Date to LocalDate
                                return new java.sql.Date(((Date) value).getTime()).toLocalDate();
                        }
                }

                return value;
        }

        default Class<?> getDataTypeClassForValue(Object value) {
                if (value instanceof Long) {
                        return DataTypeLong.class;
                } else if (value instanceof Integer) {
                        return DataTypeInteger.class;
                } else if (value instanceof Double) {
                        return DataTypeDouble.class;
                } else if (value instanceof Float) {
                        return DataTypeFloat.class;
                } else if (value instanceof Short) {
                        return DataTypeShort.class;
                } else if (value instanceof Byte) {
                        return DataTypeByte.class;
                } else if (value instanceof BigDecimal) {
                        return DataTypeBigDecimal.class;
                } else if (value instanceof BigInteger) {
                        return DataTypeBigInteger.class;
                } else if (value instanceof Boolean) {
                        return DataTypeBoolean.class;
                } else if (value instanceof String) {
                        return DataTypeString.class;
                } else if (value instanceof UUID) {
                        return DataTypeUUID.class;
                } else if (value instanceof LocalDate) {
                        return DataTypeLocalDate.class;
                } else if (value instanceof LocalDateTime) {
                        return DataTypeLocalDateTime.class;
                } else if (value instanceof java.sql.Date) {
                        return DataTypeLocalDate.class;
                } else if (value instanceof java.sql.Timestamp) {
                        return DataTypeLocalDateTime.class;
                } else if (value instanceof byte[]) {
                        return DataTypeByteArray.class;
                } else if (value instanceof Date) {
                        // java.util.Date: convert to LocalDateTime for mapping
                        return DataTypeLocalDateTime.class;
                }
                return null;
        }

        default DbDataType getDbDataTypeForValue(Object value) {
                if (value instanceof Long) {
                        return DbDataType.LONG;
                } else if (value instanceof Integer) {
                        return DbDataType.INTEGER;
                } else if (value instanceof Double) {
                        return DbDataType.DOUBLE;
                } else if (value instanceof Float) {
                        return DbDataType.FLOAT;
                } else if (value instanceof Short) {
                        return DbDataType.SHORT;
                } else if (value instanceof Byte) {
                        return DbDataType.BYTE;
                } else if (value instanceof BigDecimal) {
                        return DbDataType.DECIMAL;
                } else if (value instanceof BigInteger) {
                        return DbDataType.BIG_INTEGER;
                } else if (value instanceof Boolean) {
                        return DbDataType.BOOLEAN;
                } else if (value instanceof String) {
                        return DbDataType.STRING;
                } else if (value instanceof UUID) {
                        return DbDataType.UUID;
                } else if (value instanceof LocalDate) {
                        return DbDataType.DATE;
                } else if (value instanceof LocalDateTime) {
                        return DbDataType.LOCAL_DATE_TIME;
                } else if (value instanceof java.sql.Date) {
                        return DbDataType.DATE;
                } else if (value instanceof java.sql.Timestamp) {
                        return DbDataType.LOCAL_DATE_TIME;
                } else if (value instanceof byte[]) {
                        return DbDataType.BYTE_ARRAY;
                } else if (value instanceof Date) {
                        return DbDataType.DATE_TIME;
                }
                return null;
        }

        default String getTableNameForDataType(Class<?> clazz) {
                String simpleName = clazz.getSimpleName();
                String className = simpleName.substring("DataType".length());

                // Handle special cases where multiple capitals are consecutive
                // e.g., LocalDateTime should map to local_datetime, not local_date_time
                if ("LocalDateTime".equals(className)) {
                        return "data_type_local_datetime";
                }
                if ("BigDecimal".equals(className)) {
                        return "data_type_big_decimal";
                }
                if ("BigInteger".equals(className)) {
                        return "data_type_big_integer";
                }

                // General case: insert underscore before capital letters preceded by lowercase
                return "data_type_" + className
                                .replaceAll("([a-z])([A-Z])", "$1_$2")
                                .toLowerCase();
        }

        default <I, O> void testReadingFromDB(I input, O output) throws TestError {
                Class<?> inputClass = getDataTypeClassForValue(input);
                Class<?> outputClass = getDataTypeClassForValue(output);
                String tableName = getTableNameForDataType((Class<? extends IEntity<Long>>) inputClass);
                O result = testIt(inputClass, input, outputClass, tableName, output, null);

                // Special handling for BigDecimal to ignore trailing zeros
                if (output instanceof java.math.BigDecimal expectedBd
                                && result instanceof java.math.BigDecimal resultBd) {
                        assertThat(resultBd.stripTrailingZeros()).isEqualTo(expectedBd.stripTrailingZeros());
                } else {
                        assertThat(result).isEqualTo(output);
                }
        }

        default <I, O> void testWritingToDB(I input, O output) throws TestError {
                Class<?> inputClass = getDataTypeClassForValue(input);
                Class<?> outputClass = getDataTypeClassForValue(output);
                String tableName = getTableNameForDataType((Class<? extends IEntity<Long>>) outputClass);
                O result = testIt(inputClass, input, outputClass, tableName, output, getDbDataTypeForValue(output));

                // Special handling for BigDecimal to ignore trailing zeros
                if (output instanceof java.math.BigDecimal expectedBd
                                && result instanceof java.math.BigDecimal resultBd) {
                        assertThat(resultBd.stripTrailingZeros()).isEqualTo(expectedBd.stripTrailingZeros());
                } else {
                        assertThat(result).isEqualTo(output);
                }
        }

        default <I, O> void testWritingToDBError(I input, O output) throws TestError {
                try {
                        testWritingToDB(input, output);
                        fail("Expected an exception to be thrown for input: " + input + " with output type: "
                                        + output.getClass().getName());
                } catch (NativSQLException e) {
                        // Expected exception, test passes
                }
        }

        default <I, O> void testReadingFromDBError(I input, O output) throws TestError {
                try {
                        testReadingFromDB(input, output);
                        fail("Expected an exception to be thrown for input: " + input + " with output type: "
                                        + output.getClass().getName());
                } catch (NativSQLException e) {
                        // Expected exception, test passes
                }
        }

        @Test
        default void testWritingStringToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB("123", 123);
        }

        @Test
        default void testWritingLongToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(456L, 456);
        }

        @Test
        default void testWritingDoubleToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(789.5d, 789);
        }

        @Test
        default void testWritingFloatToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(123.7f, 123);
        }

        @Test
        default void testWritingBooleanToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(true, 1);
                testWritingToDB(false, 0);
        }

        @Test
        default void testWritingShortToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((short) 100, 100);
        }

        @Test
        default void testWritingByteToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((byte) 50, 50);
        }

        @Test
        default void testWritingBigDecimalToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(new BigDecimal("123.45"), 123);
        }

        @Test
        default void testWritingBigIntegerToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(new BigInteger("789"), 789);
        }

        @Test
        default void testWritingUUIDToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123);
        }

        @Test
        default void testWritingLocalDateToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123);
        }

        @Test
        default void testWritingLocalDateTimeToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123);
        }

        @Test
        default void testWritingByteArrayToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(new byte[] { 1, 2, 3 }, 123);
        }

        @Test
        default void testWritingLocalDateToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                LocalDate date2 = LocalDate.of(2025, 12, 25);
                testWritingToDB(date1, date1);
                testWritingToDB(date2, date2);
        }

        @Test
        default void testWritingLocalDateTimeToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                LocalDate date2 = LocalDate.of(2025, 12, 25);
                testWritingToDB(LocalDateTime.of(2024, 1, 15, 10, 30, 45), date1);
                testWritingToDB(LocalDateTime.of(2025, 12, 25, 23, 59, 59), date2);
        }

        @Test
        default void testWritingLongToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(123L, date1);
        }

        @Test
        default void testWritingIntegerToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(456, date1);
        }

        @Test
        default void testWritingFloatToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(123.5f, date1);
        }

        @Test
        default void testWritingDoubleToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(789.5d, date1);
        }

        @Test
        default void testWritingBooleanToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(true, date1);
        }

        @Test
        default void testWritingShortToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError((short) 100, date1);
        }

        @Test
        default void testWritingByteToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError((byte) 50, date1);
        }

        @Test
        default void testWritingBigDecimalToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(new BigDecimal("123"), date1);
        }

        @Test
        default void testWritingBigIntegerToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(new BigInteger("789"), date1);
        }

        @Test
        default void testWritingUUIDToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), date1);
        }

        @Test
        default void testWritingByteArrayToLocalDate()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testWritingToDBError(new byte[] { 1, 2, 3 }, date1);
        }

        @Test
        default void testReadingLongToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(9223372036854775807L, 9223372036854775807L);
        }

        @Test
        default void testReadingFloatToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(111.3f, 111L);
                testReadingFromDB(333.5f, 333L);
                testReadingFromDB(555.7f, 555L);
                testReadingFromDB(671.0f, 671L);
        }

        @Test
        default void testReadingIntegerToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(456, 456L);
                testReadingFromDB(321, 321L);
                testReadingFromDB(42, 42L);
                testReadingFromDB(999, 999L);
        }

        @Test
        default void testReadingDoubleToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(222.2d, 222L);
                testReadingFromDB(444.5d, 444L);
                testReadingFromDB(789.9d, 789L);
                testReadingFromDB(888.0d, 888L);
                testReadingFromDB(555.5, 555L);
        }

        @Test
        default void testReadingBooleanToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(true, 1L);
                testReadingFromDB(false, 0L);
        }

        @Test
        default void testReadingStringToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB("654", 654L);
        }

        @Test
        default void testReadingUUIDToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123L);
        }

        @Test
        default void testReadingDateToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new Date(), 123L);
        }

        @Test
        default void testReadingByteArrayToLong()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, 123L);
        }

        @Test
        default void testReadingLongToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123L, 123.0f);
        }

        @Test
        default void testReadingDoubleToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123.1d, 123.1f);
                testReadingFromDB(123.1d, 123.1f);
                testReadingFromDB(123.1, 123.1f);
        }

        @Test
        default void testReadingIntegerToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123, 123.0f);
                testReadingFromDB(123, 123.0f);
                testReadingFromDB(123, 123.0f);
                testReadingFromDB(123, 123f);
        }

        @Test
        default void testReadingBooleanToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(true, 1f);
                testReadingFromDB(false, 0f);
        }

        @Test
        default void testReadingStringToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB("123.1", 123.1f);
        }

        @Test
        default void testReadingUUIDToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123.1f);
        }

        @Test
        default void testReadingDateToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new Date(), 123.1f);
        }

        @Test
        default void testReadingByteArrayToFloat()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, 123.1f);
        }

        @Test
        default void testReadingLongToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123L, 123);
        }

        @Test
        default void testReadingFloatToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(111.3f, 111);
                testReadingFromDB(333.5f, 333);
                testReadingFromDB(555.7f, 555);
                testReadingFromDB(671.0f, 671);
        }

        @Test
        default void testReadingIntegerToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(456, 456);
                testReadingFromDB(321, 321);
                testReadingFromDB(42, 42);
                testReadingFromDB(999, 999);
        }

        @Test
        default void testReadingDoubleToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(222.2d, 222);
                testReadingFromDB(444.5d, 444);
                testReadingFromDB(666.9d, 666);
                testReadingFromDB(777.0d, 777);
                testReadingFromDB(555.5, 555);
        }

        @Test
        default void testReadingBooleanToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(true, 1);
                testReadingFromDB(false, 0);
        }

        @Test
        default void testReadingStringToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB("654", 654);
        }

        @Test
        default void testReadingUUIDToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123);
        }

        @Test
        default void testReadingDateToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new Date(), 123);
        }

        @Test
        default void testReadingByteArrayToInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, 123);
        }

        @Test
        default void testReadingLongToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123L, 123.0d);
        }

        @Test
        default void testReadingFloatToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123.5f, 123.5d);
        }

        @Test
        default void testReadingIntegerToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123, 123.0d);
                testReadingFromDB(124, 124.0d);
                testReadingFromDB(125, 125.0d);
                testReadingFromDB(789, 789.0d);
        }

        @Test
        default void testReadingDoubleToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123.5d, 123.5d);
                testReadingFromDB(456.5, 456.5d);
        }

        @Test
        default void testReadingBooleanToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(true, 1.0d);
                testReadingFromDB(false, 0d);
        }

        @Test
        default void testReadingStringToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB("456.5", 456.5d);
        }

        @Test
        default void testReadingUUIDToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123.5d);
        }

        @Test
        default void testReadingDateToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new Date(), 123.5d);
        }

        @Test
        default void testReadingByteArrayToDouble()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, 123.5d);
        }

        @Test
        default void testReadingLongToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123L, (short) 123);
        }

        @Test
        default void testReadingFloatToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(123.3f, (short) 123);
                testReadingFromDB(789.5f, (short) 789);
                testReadingFromDB(654.7f, (short) 654);
                testReadingFromDB(512.0f, (short) 512);
        }

        @Test
        default void testReadingIntegerToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(456, (short) 456);
                testReadingFromDB(321, (short) 321);
                testReadingFromDB(42, (short) 42);
                testReadingFromDB(999, (short) 999);
        }

        @Test
        default void testReadingDoubleToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(456.2d, (short) 456);
                testReadingFromDB(321.5d, (short) 321);
                testReadingFromDB(987.9d, (short) 987);
                testReadingFromDB(888.0d, (short) 888);
                testReadingFromDB(555.5, (short) 555);
        }

        @Test
        default void testReadingBooleanToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB(true, (short) 1);
                testReadingFromDB(false, (short) 0);
        }

        @Test
        default void testReadingStringToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDB("654", (short) 654);
        }

        @Test
        default void testReadingUUIDToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (short) 123);
        }

        @Test
        default void testReadingDateToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new Date(), (short) 123);
        }

        @Test
        default void testReadingByteArrayToShort()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, (short) 123);
        }

        @Test
        default void testReadingBigDecimalFromLong() {
                testReadingFromDB(123L, new BigDecimal("123.0"));
        }

        @Test
        default void testReadingBigDecimalFromFloat() {
                testReadingFromDB(123.0f, new BigDecimal("123.0"));
        }

        @Test
        default void testReadingBigDecimalFromInteger() {
                testReadingFromDB(456, new BigDecimal("456.0"));
        }

        @Test
        default void testReadingBigDecimalFromDouble() {
                testReadingFromDB(789.5d, new BigDecimal("789.5"));
        }

        @Test
        default void testReadingBigDecimalFromInteger2() {
                testReadingFromDB(321, new BigDecimal("321.0"));
        }

        @Test
        default void testReadingBigDecimalFromInteger3() {
                testReadingFromDB(42, new BigDecimal("42.0"));
        }

        @Test
        default void testReadingBigDecimalFromDouble2() {
                testReadingFromDB(555.5, new BigDecimal("555.5"));
        }

        @Test
        default void testReadingBigDecimalFromInteger4() {
                testReadingFromDB(999, new BigDecimal("999.0"));
        }

        @Test
        default void testReadingBigDecimalFromBoolean() {
                testReadingFromDB(true, new BigDecimal("1"));
        }

        @Test
        default void testReadingBigDecimalFromBooleanFalse() {
                testReadingFromDB(false, new BigDecimal("0"));
        }

        @Test
        default void testReadingBigDecimalFromString() {
                testReadingFromDB("654.321", new BigDecimal("654.321"));
        }

        @Test
        default void testReadingBigDecimalFromUUID() {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), new BigDecimal("123"));
        }

        @Test
        default void testReadingBigDecimalFromDate() {
                testReadingFromDBError(new Date(), new BigDecimal("123"));
        }

        @Test
        default void testReadingBigDecimalFromDate2() {
                testReadingFromDBError(new Date(), new BigDecimal("123"));
        }

        @Test
        default void testReadingBigDecimalFromByteArray() {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, new BigDecimal("123"));
        }

        @Test
        default void testReadingBigIntegerFromLong() {
                testReadingFromDB(123L, new BigInteger("123"));
        }

        @Test
        default void testReadingBigIntegerFromFloat() {
                testReadingFromDB(111.3f, new BigInteger("111"));
                testReadingFromDB(333.5f, new BigInteger("333"));
                testReadingFromDB(555.7f, new BigInteger("555"));
                testReadingFromDB(671.0f, new BigInteger("671"));
        }

        @Test
        default void testReadingBigIntegerFromInteger() {
                testReadingFromDB(456, new BigInteger("456"));
                testReadingFromDB(321, new BigInteger("321"));
                testReadingFromDB(42, new BigInteger("42"));
                testReadingFromDB(999, new BigInteger("999"));
        }

        @Test
        default void testReadingBigIntegerFromDouble() {
                testReadingFromDB(222.2d, new BigInteger("222"));
                testReadingFromDB(444.5d, new BigInteger("444"));
                testReadingFromDB(789.9d, new BigInteger("789"));
                testReadingFromDB(888.0d, new BigInteger("888"));
                testReadingFromDB(555.5, new BigInteger("555"));
        }

        @Test
        default void testReadingBigIntegerFromBoolean() {
                testReadingFromDB(true, new BigInteger("1"));
                testReadingFromDB(false, new BigInteger("0"));
        }

        @Test
        default void testReadingBigIntegerFromString() {
                testReadingFromDB("654", new BigInteger("654"));
        }

        @Test
        default void testReadingBigIntegerFromUUID() {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), new BigInteger("123"));
        }

        @Test
        default void testReadingBigIntegerFromDate() {
                testReadingFromDBError(new Date(), new BigInteger("123"));
        }

        @Test
        default void testReadingBigIntegerFromDate2() {
                testReadingFromDBError(new Date(), new BigInteger("123"));
        }

        @Test
        default void testReadingBigIntegerFromByteArray() {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, new BigInteger("123"));
        }

        @Test
        default void testReadingByteFromLong() {
                testReadingFromDB(123L, (byte) 123);
        }

        @Test
        default void testReadingByteFromFloat() {
                testReadingFromDB(88.3f, (byte) 88);
                testReadingFromDB(66.5f, (byte) 66);
                testReadingFromDB(44.7f, (byte) 44);
                testReadingFromDB(99.0f, (byte) 99);
        }

        @Test
        default void testReadingByteFromInteger() {
                testReadingFromDB(100, (byte) 100);
                testReadingFromDB(50, (byte) 50);
                testReadingFromDB(42, (byte) 42);
                testReadingFromDB(77, (byte) 77);
        }

        @Test
        default void testReadingByteFromDouble() {
                testReadingFromDB(77.2d, (byte) 77);
                testReadingFromDB(55.5d, (byte) 55);
                testReadingFromDB(33.9d, (byte) 33);
                testReadingFromDB(121.0d, (byte) 121);
                testReadingFromDB(99.5, (byte) 99);
        }

        @Test
        default void testReadingByteFromBoolean() {
                testReadingFromDB(true, (byte) 1);
                testReadingFromDB(false, (byte) 0);
        }

        @Test
        default void testReadingByteFromString() {
                testReadingFromDB("54", (byte) 54);
        }

        @Test
        default void testReadingByteFromUUID() {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (byte) 123);
        }

        @Test
        default void testReadingByteFromDate() {
                testReadingFromDBError(new Date(), (byte) 123);
        }

        @Test
        default void testReadingByteFromDate2() {
                testReadingFromDBError(new Date(), (byte) 123);
        }

        @Test
        default void testReadingByteFromByteArray() {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, (byte) 123);
        }

        @Test
        default void testReadingBooleanFromBoolean() {
                testReadingFromDB(true, true);
                testReadingFromDB(false, false);
        }

        @Test
        default void testReadingBooleanFromLong() {
                testReadingFromDB(1L, true);
                testReadingFromDB(0L, false);
        }

        @Test
        default void testReadingBooleanFromInteger() {
                testReadingFromDB(1, true);
                testReadingFromDB(0, false);
        }

        @Test
        default void testReadingBooleanFromFloat() {
                testReadingFromDB(1.5f, true);
                testReadingFromDB(0.0f, false);
        }

        @Test
        default void testReadingBooleanFromDouble() {
                testReadingFromDB(1.5d, true);
                testReadingFromDB(0.0d, false);
        }

        @Test
        default void testReadingBooleanFromString() {
                // Test all true string representations
                for (String trueStr : BOOLEAN_TRUE_STRINGS) {
                        testReadingFromDB(trueStr, true);
                }
                // Test all false string representations
                for (String falseStr : BOOLEAN_FALSE_STRINGS) {
                        testReadingFromDB(falseStr, false);
                }
        }

        @Test
        default void testReadingBooleanFromUUID() {
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), true);
        }

        @Test
        default void testReadingBooleanFromDate() {
                testReadingFromDBError(new Date(), true);
                testReadingFromDBError(new Date(), true);
        }

        @Test
        default void testReadingBooleanFromByteArray() {
                testReadingFromDBError(new byte[] { 1, 2, 3 }, true);
        }

        @Test
        default void testReadingStringFromString() {
                testReadingFromDB("hello", "hello");
        }

        @Test
        default void testReadingStringFromLong() {
                testReadingFromDB(123L, "123");
        }

        @Test
        default void testReadingStringFromInteger() {
                testReadingFromDB(456, "456");
                testReadingFromDB(321, "321");
                testReadingFromDB(42, "42");
                testReadingFromDB(999, "999");
        }

        @Test
        default void testReadingStringFromFloat() {
                testReadingFromDB(123.5f, "123.5");
        }

        @Test
        default void testReadingStringFromDouble() {
                testReadingFromDB(789.5d, "789.5");
                testReadingFromDB(555.5, "555.5");
        }

        @Test
        default void testReadingStringFromBoolean() {
                testReadingFromDB(true, "true");
                testReadingFromDB(false, "false");
        }

        @Test
        default void testReadingStringFromUUID() {
                UUID testUUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDB(testUUID, testUUID.toString());
        }

        @Test
        default void testReadingStringFromDate() {
                testReadingFromDB(java.sql.Date.valueOf(LocalDate.of(2024, 1, 15)), "2024-01-15");
        }

        @Test
        default void testReadingStringFromLocalDateTime() {
                testReadingFromDB(java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 1, 15, 10, 30, 45)),
                                "2024-01-15T10:30:45");
        }

        @Test
        default void testReadingStringFromByteArray() {
                testReadingFromDB(new byte[] { 72, 101, 108, 108, 111 }, "[72, 101, 108, 108, 111]");
        }

        @Test
        default void testReadingUUIDFromUUID() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDB(uuid, uuid);
        }

        @Test
        default void testReadingUUIDFromString() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDB(uuid.toString(), uuid);
        }

        @Test
        default void testReadingUUIDFromByteArray() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                byte[] uuidBytes = new byte[] { (byte) 0x55, (byte) 0x0e, (byte) 0x84, (byte) 0x00, (byte) 0xe2,
                                (byte) 0x9b, (byte) 0x41, (byte) 0xd4, (byte) 0xa7, (byte) 0x16, (byte) 0x44,
                                (byte) 0x66, (byte) 0x55, (byte) 0x44, (byte) 0x00, (byte) 0x00 };
                testReadingFromDB(uuidBytes, uuid);
        }

        @Test
        default void testReadingUUIDFromLong() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(123L, uuid);
        }

        @Test
        default void testReadingUUIDFromInteger() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(456, uuid);
        }

        @Test
        default void testReadingUUIDFromInvalidString() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError("not-a-uuid", uuid);
        }

        @Test
        default void testReadingUUIDFromBoolean() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(true, uuid);
        }

        @Test
        default void testReadingUUIDFromFloat() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(123.5f, uuid);
        }

        @Test
        default void testReadingUUIDFromDouble() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(789.5d, uuid);
        }

        @Test
        default void testReadingUUIDFromShort() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(321, uuid);
        }

        @Test
        default void testReadingUUIDFromByte() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(42, uuid);
        }

        @Test
        default void testReadingUUIDFromDate() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(new Date(), uuid);
                testReadingFromDBError(new Date(), uuid);
        }

        @Test
        default void testReadingLocalDateFromLocalDate() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDB(date1, date1);
        }

        @Test
        default void testReadingLocalDateFromLong() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(123L, date1);
        }

        @Test
        default void testReadingLocalDateFromInteger() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(456, date1);
        }

        @Test
        default void testReadingLocalDateFromString() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError("2024-01-15", date1);
        }

        @Test
        default void testReadingLocalDateFromBoolean() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(true, date1);
        }

        @Test
        default void testReadingLocalDateFromUUID() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), date1);
        }

        @Test
        default void testReadingLocalDateFromDate() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(new Date(), date1);
        }

        @Test
        default void testReadingLocalDateFromByteArray() {
                LocalDate date1 = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(new byte[] { 1, 2, 3 }, date1);
        }

        @Test
        default void testReadingLocalDateTimeFromLocalDateTime() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDB(dateTime1, dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromLong() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(123L, dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromInteger() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(456, dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromString() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError("2024-01-15T10:30:45", dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromBoolean() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(true, dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromUUID() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), dateTime1);
        }

        @Test
        default void testReadingLocalDateTimeFromByteArray() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(new byte[] { 1, 2, 3 }, dateTime1);
        }

        @Test
        default void testReadingByteArrayFromByteArray() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDB(bytes1, bytes1);
        }

        @Test
        default void testReadingByteArrayFromLong() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(23L, bytes1);
        }

        @Test
        default void testReadingByteArrayFromInteger() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(456, bytes1);
        }

        @Test
        default void testReadingByteArrayFromString() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDB("ABC", bytes1);
        }

        @Test
        default void testReadingByteArrayFromBoolean() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(true, bytes1);
        }

        @Test
        default void testReadingByteArrayFromFloat() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(123.5f, bytes1);
        }

        @Test
        default void testReadingByteArrayFromDouble() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(789.5d, bytes1);
        }

        @Test
        default void testReadingByteArrayFromShort() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError((short) 321, bytes1);
        }

        @Test
        default void testReadingByteArrayFromByte() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError((byte) 42, bytes1);
        }

        @Test
        default void testReadingByteArrayFromUUID() {
                // This conversion will not behave as expected.
                // The UUID will be converted to byte array, but byte array will be read from
                // string and will provide the toString() of the original UUID
                testReadingFromDB(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                                "550e8400-e29b-41d4-a716-446655440000".getBytes());
        }

        @Test
        default void testReadingByteArrayFromDate() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testReadingFromDBError(new Date(), bytes1);
                testReadingFromDBError(new Date(), bytes1);
        }

        @Test
        default void testConversionStringToFloat() {
                testWritingToDB("123.5", 123.5f);
        }

        @Test
        default void testConversionLongToFloat() {
                testWritingToDB(456L, 456.0f);
        }

        @Test
        default void testConversionIntegerToFloat() {
                testWritingToDB(789, 789.0f);
        }

        @Test
        default void testConversionDoubleToFloat() {
                testWritingToDB(123.45d, 123.45f);
        }

        @Test
        default void testConversionBooleanToFloat() {
                testWritingToDB(true, 1.0f);
                testWritingToDB(false, 0.0f);
        }

        @Test
        default void testConversionBigDecimalToFloat() {
                testWritingToDB(new BigDecimal("123.45"), 123.45f);
        }

        @Test
        default void testConversionBigIntegerToFloat() {
                testWritingToDB(new BigInteger("789"), 789.0f);
        }

        @Test
        default void testConversionFloatToFloat() {
                testWritingToDB(123.5f, 123.5f);
        }

        @Test
        default void testConversionUUIDToFloatError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123.5f);
        }

        @Test
        default void testConversionLocalDateToFloatError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123.5f);
        }

        @Test
        default void testConversionLocalDateTimeToFloatError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123.5f);
        }

        @Test
        default void testConversionByteArrayToFloatError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, 123.5f);
        }

        @Test
        default void testConversionStringToLong() {
                testWritingToDB("123", 123L);
        }

        @Test
        default void testConversionIntegerToLong() {
                testWritingToDB(456, 456L);
        }

        @Test
        default void testConversionDoubleToLong() {
                testWritingToDB(789.5d, 789L);
        }

        @Test
        default void testConversionFloatToLong() {
                testWritingToDB(123.7f, 123L);
        }

        @Test
        default void testConversionBooleanToLong() {
                testWritingToDB(true, 1L);
                testWritingToDB(false, 0L);
        }

        @Test
        default void testConversionShortToLong() {
                testWritingToDB((short) 100, 100L);
        }

        @Test
        default void testConversionByteToLong() {
                testWritingToDB((byte) 50, 50L);
        }

        @Test
        default void testConversionBigDecimalToLong() {
                testWritingToDB(new BigDecimal("123.45"), 123L);
        }

        @Test
        default void testConversionBigIntegerToLong() {
                testWritingToDB(new BigInteger("789"), 789L);
        }

        @Test
        default void testConversionUUIDToLongError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123L);
        }

        @Test
        default void testConversionLocalDateToLongError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123L);
        }

        @Test
        default void testConversionLocalDateTimeToLongError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123L);
        }

        @Test
        default void testConversionByteArrayToLongError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, 123L);
        }

        @Test
        default void testConversionLongToDouble() {
                testWritingToDB(456L, 456.0d);
        }

        @Test
        default void testConversionIntegerToDouble() {
                testWritingToDB(789, 789.0d);
        }

        @Test
        default void testConversionFloatToDouble() {
                testWritingToDB(123.5f, 123.5d);
        }

        @Test
        default void testConversionBooleanToDouble() {
                testWritingToDB(true, 1.0d);
                testWritingToDB(false, 0.0d);
        }

        @Test
        default void testConversionShortToDouble() {
                testWritingToDB((short) 100, 100.0d);
        }

        @Test
        default void testConversionByteToDouble() {
                testWritingToDB((byte) 50, 50.0d);
        }

        @Test
        default void testConversionBigDecimalToDouble() {
                testWritingToDB(new BigDecimal("123.45"), 123.45d);
        }

        @Test
        default void testConversionBigIntegerToDouble() {
                testWritingToDB(new BigInteger("789"), 789.0d);
        }

        @Test
        default void testConversionUUIDToDoubleError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123.45d);
        }

        @Test
        default void testConversionLocalDateToDoubleError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123.45d);
        }

        @Test
        default void testConversionLocalDateTimeToDoubleError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123.45d);
        }

        @Test
        default void testConversionByteArrayToDoubleError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, 123.45d);
        }

        @Test
        default void testConversionIntegerToShort() {
                testWritingToDB(789, (short) 789);
        }

        @Test
        default void testConversionBooleanToShort() {
                testWritingToDB(true, (short) 1);
                testWritingToDB(false, (short) 0);
        }

        @Test
        default void testConversionByteToShort() {
                testWritingToDB((byte) 50, (short) 50);
        }

        @Test
        default void testConversionUUIDToShortError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (short) 100);
        }

        @Test
        default void testConversionLocalDateToShortError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), (short) 100);
        }

        @Test
        default void testConversionLocalDateTimeToShortError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (short) 100);
        }

        @Test
        default void testConversionByteArrayToShortError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, (short) 100);
        }

        @Test
        default void testConversionIntegerToByte() {
                testWritingToDB(456, (byte) 456);
        }

        @Test
        default void testConversionBooleanToByte() {
                testWritingToDB(true, (byte) 1);
                testWritingToDB(false, (byte) 0);
        }

        @Test
        default void testConversionShortToByte() {
                testWritingToDB((short) 100, (byte) 100);
        }

        @Test
        default void testConversionUUIDToByteError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (byte) 50);
        }

        @Test
        default void testConversionLocalDateToByteError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), (byte) 50);
        }

        @Test
        default void testConversionLocalDateTimeToByteError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (byte) 50);
        }

        @Test
        default void testConversionByteArrayToByteError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, (byte) 50);
        }

        @Test
        default void testConversionStringToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB("123.45", new BigDecimal("123.45"));
        }

        @Test
        default void testConversionLongToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(456L, new BigDecimal("456"));
        }

        @Test
        default void testConversionIntegerToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(789, new BigDecimal("789.00"));
        }

        @Test
        default void testConversionFloatToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(123.5f, new BigDecimal("123.50"));
        }

        @Test
        default void testConversionDoubleToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(789.5d, new BigDecimal("789.50"));
        }

        @Test
        default void testConversionBooleanToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(true, new BigDecimal("1.00"));
                testWritingToDB(false, new BigDecimal("0.00"));
        }

        @Test
        default void testConversionShortToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((short) 100, new BigDecimal("100.00"));
        }

        @Test
        default void testConversionByteToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((byte) 50, new BigDecimal("50.00"));
        }

        @Test
        default void testConversionBigIntegerToBigDecimal()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(new BigInteger("789"), new BigDecimal("789.00"));
        }

        @Test
        default void testConversionUUIDToBigDecimalError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), new BigDecimal("123.00"));
        }

        @Test
        default void testConversionLocalDateToBigDecimalError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDate.of(2024, 1, 15), new BigDecimal("123.00"));
        }

        @Test
        default void testConversionLocalDateTimeToBigDecimalError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), new BigDecimal("123.00"));
        }

        @Test
        default void testConversionByteArrayToBigDecimalError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(new byte[] { 1, 2, 3 }, new BigDecimal("123.00"));
        }

        @Test
        default void testConversionStringToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB("789", new BigInteger("789"));
        }

        @Test
        default void testConversionLongToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(456L, new BigInteger("456"));
        }

        @Test
        default void testConversionIntegerToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(789, new BigInteger("789"));
        }

        @Test
        default void testConversionFloatToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(123.7f, new BigInteger("123"));
        }

        @Test
        default void testConversionDoubleToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(789.5d, new BigInteger("789"));
        }

        @Test
        default void testConversionBooleanToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(true, new BigInteger("1"));
                testWritingToDB(false, new BigInteger("0"));
        }

        @Test
        default void testConversionShortToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((short) 100, new BigInteger("100"));
        }

        @Test
        default void testConversionByteToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB((byte) 50, new BigInteger("50"));
        }

        @Test
        default void testConversionBigDecimalToBigInteger()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDB(new BigDecimal("123.45"), new BigInteger("123"));
        }

        @Test
        default void testConversionUUIDToBigIntegerError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), new BigInteger("789"));
        }

        @Test
        default void testConversionLocalDateToBigIntegerError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDate.of(2024, 1, 15), new BigInteger("789"));
        }

        @Test
        default void testConversionLocalDateTimeToBigIntegerError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), new BigInteger("789"));
        }

        @Test
        default void testConversionByteArrayToBigIntegerError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                testWritingToDBError(new byte[] { 1, 2, 3 }, new BigInteger("789"));
        }

        @Test
        default void testConversionStringTrueToBoolean() {
                testWritingToDB("true", true);
        }

        @Test
        default void testConversionStringFalseToBoolean() {
                testWritingToDB("false", false);
        }

        @Test
        default void testConversionString1ToBoolean() {
                testWritingToDB("1", true);
        }

        @Test
        default void testConversionString0ToBoolean() {
                testWritingToDB("0", false);
        }

        @Test
        default void testConversionLongTrueToBoolean() {
                testWritingToDB(1L, true);
        }

        @Test
        default void testConversionLongFalseToBoolean() {
                testWritingToDB(0L, false);
        }

        @Test
        default void testConversionIntegerTrueToBoolean() {
                testWritingToDB(1, true);
        }

        @Test
        default void testConversionIntegerFalseToBoolean() {
                testWritingToDB(0, false);
        }

        @Test
        default void testConversionFloatTrueToBoolean() {
                testWritingToDB(1.5f, true);
        }

        @Test
        default void testConversionFloatFalseToBoolean() {
                testWritingToDB(0.0f, false);
        }

        @Test
        default void testConversionDoubleTrueToBoolean() {
                testWritingToDB(1.5d, true);
        }

        @Test
        default void testConversionDoubleFalseToBoolean() {
                testWritingToDB(0.0d, false);
        }

        @Test
        default void testConversionShortTrueToBoolean() {
                testWritingToDB((short) 1, true);
        }

        @Test
        default void testConversionShortFalseToBoolean() {
                testWritingToDB((short) 0, false);
        }

        @Test
        default void testConversionByteTrueToBoolean() {
                testWritingToDB((byte) 1, true);
        }

        @Test
        default void testConversionByteFalseToBoolean() {
                testWritingToDB((byte) 0, false);
        }

        @Test
        default void testConversionBigDecimalTrueToBoolean() {
                testWritingToDB(new BigDecimal("1"), true);
        }

        @Test
        default void testConversionBigDecimalFalseToBoolean() {
                testWritingToDB(new BigDecimal("0"), false);
        }

        @Test
        default void testConversionBigIntegerTrueToBoolean() {
                testWritingToDB(new BigInteger("1"), true);
        }

        @Test
        default void testConversionBigIntegerFalseToBoolean() {
                testWritingToDB(new BigInteger("0"), false);
        }

        @Test
        default void testConversionUUIDToBooleanError() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), true);
        }

        @Test
        default void testConversionLocalDateToBooleanError() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), true);
        }

        @Test
        default void testConversionLocalDateTimeToBooleanError() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), true);
        }

        @Test
        default void testConversionByteArrayToBooleanError() {
                testWritingToDBError(new byte[] { 1, 2, 3 }, true);
        }

        @Test
        default void testConversionLongToString() {
                testWritingToDB(123L, "123");
        }

        @Test
        default void testConversionIntegerToString() {
                testWritingToDB(456, "456");
        }

        @Test
        default void testConversionFloatToString() {
                testWritingToDB(123.5f, "123.5");
        }

        @Test
        default void testConversionDoubleToString() {
                testWritingToDB(789.5d, "789.5");
        }

        @Test
        default void testConversionBooleanTrueToString() {
                testWritingToDB(true, "true");
        }

        @Test
        default void testConversionBooleanFalseToString() {
                testWritingToDB(false, "false");
        }

        @Test
        default void testConversionShortToString() {
                testWritingToDB((short) 100, "100");
        }

        @Test
        default void testConversionByteToString() {
                testWritingToDB((byte) 50, "50");
        }

        @Test
        default void testConversionByteArrayToString() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testWritingToDB(bytes1, "ABC");
        }

        @Test
        default void testConversionUUIDStringToUUID() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDB(uuid.toString(), uuid);
        }

        @Test
        default void testConversionLongToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(123L, uuid);
        }

        @Test
        default void testConversionIntegerToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(456, uuid);
        }

        @Test
        default void testConversionFloatToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(123.5f, uuid);
        }

        @Test
        default void testConversionBooleanToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(true, uuid);
        }

        @Test
        default void testConversionShortToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError((short) 100, uuid);
        }

        @Test
        default void testConversionByteToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError((byte) 50, uuid);
        }

        @Test
        default void testConversionBigDecimalToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(new BigDecimal("123"), uuid);
        }

        @Test
        default void testConversionBigIntegerToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(new BigInteger("789"), uuid);
        }

        @Test
        default void testConversionLocalDateToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(LocalDate.of(2024, 1, 15), uuid);
        }

        @Test
        default void testConversionLocalDateTimeToUUIDError() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), uuid);
        }

        @Test
        default void testConversionLocalDateTimeToLocalDateTime1() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDB(dateTime1, dateTime1);
        }

        @Test
        default void testConversionLocalDateTimeToLocalDateTime2() {
                LocalDateTime dateTime2 = LocalDateTime.of(2025, 12, 25, 23, 59, 59);
                testWritingToDB(dateTime2, dateTime2);
        }

        @Test
        default void testConversionLocalDateToLocalDateTime1() {
                testWritingToDB(LocalDate.of(2024, 1, 15), LocalDateTime.of(2024, 1, 15, 0, 0, 0));
        }

        @Test
        default void testConversionLocalDateToLocalDateTime2() {
                testWritingToDB(LocalDate.of(2025, 12, 25), LocalDateTime.of(2025, 12, 25, 0, 0, 0));
        }

        @Test
        default void testConversionLongToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(123L, dateTime1);
        }

        @Test
        default void testConversionIntegerToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(456, dateTime1);
        }

        @Test
        default void testConversionFloatToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(123.5f, dateTime1);
        }

        @Test
        default void testConversionDoubleToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(789.5d, dateTime1);
        }

        @Test
        default void testConversionBooleanToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(true, dateTime1);
        }

        @Test
        default void testConversionShortToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError((short) 100, dateTime1);
        }

        @Test
        default void testConversionByteToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError((byte) 50, dateTime1);
        }

        @Test
        default void testConversionBigDecimalToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(new BigDecimal("123"), dateTime1);
        }

        @Test
        default void testConversionBigIntegerToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(new BigInteger("789"), dateTime1);
        }

        @Test
        default void testConversionUUIDToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), dateTime1);
        }

        @Test
        default void testConversionByteArrayToLocalDateTimeError() {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDBError(new byte[] { 1, 2, 3 }, dateTime1);
        }

        @Test
        default void testLocalDateTimeWithLocalDateTimeDbType1()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDateTime dateTime1 = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                Class<?> dataTypeClass = DataTypeLocalDateTime.class;
                String tableName = "data_type_local_datetime";
                LocalDateTime result1 = testIt(dataTypeClass, dateTime1, dataTypeClass, tableName, dateTime1,
                                DbDataType.LOCAL_DATE_TIME);
                assertThat(result1).isEqualTo(dateTime1);
        }

        @Test
        default void testLocalDateTimeWithLocalDateTimeDbType2()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                LocalDateTime dateTime2 = LocalDateTime.of(2025, 12, 25, 23, 59, 59);
                Class<?> dataTypeClass = DataTypeLocalDateTime.class;
                String tableName = "data_type_local_datetime";
                LocalDateTime result2 = testIt(dataTypeClass, dateTime2, dataTypeClass, tableName, dateTime2,
                                DbDataType.LOCAL_DATE_TIME);
                assertThat(result2).isEqualTo(dateTime2);
        }

        @Test
        default void testConversionLongToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(123L, bytes1);
        }

        @Test
        default void testConversionIntegerToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(456, bytes1);
        }

        @Test
        default void testConversionFloatToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(123.5f, bytes1);
        }

        @Test
        default void testConversionDoubleToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(789.5d, bytes1);
        }

        @Test
        default void testConversionBooleanToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(true, bytes1);
        }

        @Test
        default void testConversionShortToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError((short) 100, bytes1);
        }

        @Test
        default void testConversionByteToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError((byte) 50, bytes1);
        }

        @Test
        default void testConversionBigDecimalToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(new BigDecimal("123"), bytes1);
        }

        @Test
        default void testConversionBigIntegerToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(new BigInteger("789"), bytes1);
        }

        @Test
        default void testConversionLocalDateToByteArrayError() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(LocalDate.of(2024, 1, 15), bytes1);
        }

        @Test
        default void testConversionLocalDateTimeToByteArrayError()
                        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                        InvocationTargetException, NoSuchMethodException, SecurityException {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), bytes1);
        }

        @Test
        default void testConversionStringToByteArray() {
                byte[] bytes1 = new byte[] { 65, 66, 67, 68, 69 };
                testWritingToDB("ABCDE", bytes1);
        }

        @Test
        default void testConversionUUIDToByteArray() {
                byte[] bytes1 = new byte[] { (byte) 0x55, (byte) 0x0e, (byte) 0x84, (byte) 0x00, (byte) 0xe2,
                                (byte) 0x9b, (byte) 0x41, (byte) 0xd4, (byte) 0xa7, (byte) 0x16, (byte) 0x44,
                                (byte) 0x66, (byte) 0x55, (byte) 0x44, (byte) 0x00, (byte) 0x00 };
                testWritingToDB(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), bytes1);
        }

        @Test
        default void testConversionByteArrayToByteArray() {
                byte[] bytes1 = new byte[] { 65, 66, 67 };
                testWritingToDB(bytes1, bytes1);
        }

        @Test
        default void testReadingFloatFromLocalDate() {
                testReadingFromDBError(LocalDate.of(2024, 1, 15), 123.5f);
        }

        @Test
        default void testReadingDoubleFromLocalDate() {
                testReadingFromDBError(LocalDate.of(2024, 1, 15), 123.5d);
        }

        @Test
        default void testReadingShortFromLocalDate() {
                testReadingFromDBError(LocalDate.of(2024, 1, 15), (short) 100);
        }

        @Test
        default void testReadingByteFromLocalDate() {
                testReadingFromDBError(LocalDate.of(2024, 1, 15), (byte) 50);
        }

        @Test
        default void testReadingFloatFromLocalDateTime() {
                testReadingFromDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123.5f);
        }

        @Test
        default void testReadingDoubleFromLocalDateTime() {
                testReadingFromDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123.5d);
        }

        @Test
        default void testReadingShortFromLocalDateTime() {
                testReadingFromDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (short) 100);
        }

        @Test
        default void testReadingByteFromLocalDateTime() {
                testReadingFromDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (byte) 50);
        }

        @Test
        default void testConversionUUIDToString() {
                UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testWritingToDB(uuid, uuid.toString());
        }

        @Test
        default void testConversionLocalDateToString() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testWritingToDB(date, date.toString());
        }

        @Test
        default void testConversionLocalDateTimeToString() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testWritingToDB(dateTime, dateTime.toString());
        }

        @Test
        default void testConversionBigDecimalToString() {
                testWritingToDB(new BigDecimal("123.45"), "123.45");
        }

        @Test
        default void testConversionBigIntegerToString() {
                testWritingToDB(new BigInteger("789"), "789");
        }

        @Test
        default void testConversionStringToString() {
                testWritingToDB("hello", "hello");
        }

        @Test
        default void testConversionByteArrayToBoolean() {
                byte[] bytes1 = new byte[] { 1, 2, 3 };
                testWritingToDBError(bytes1, true);
        }

        @Test
        default void testReadingBigDecimalFromLocalDate() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(date, new BigDecimal("123"));
        }

        @Test
        default void testReadingBigIntegerFromLocalDate() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(date, new BigInteger("123"));
        }

        @Test
        default void testReadingBigDecimalFromLocalDateTime() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(dateTime, new BigDecimal("123"));
        }

        @Test
        default void testReadingBigIntegerFromLocalDateTime() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(dateTime, new BigInteger("123"));
        }

        @Test
        default void testReadingLocalDateFromFloat() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(123.5f, date);
        }

        @Test
        default void testReadingLocalDateFromDouble() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError(123.5d, date);
        }

        @Test
        default void testReadingLocalDateFromShort() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError((short) 100, date);
        }

        @Test
        default void testReadingLocalDateFromByte() {
                LocalDate date = LocalDate.of(2024, 1, 15);
                testReadingFromDBError((byte) 50, date);
        }

        @Test
        default void testReadingLocalDateTimeFromFloat() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(123.5f, dateTime);
        }

        @Test
        default void testReadingLocalDateTimeFromDouble() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError(123.5d, dateTime);
        }

        @Test
        default void testReadingLocalDateTimeFromShort() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError((short) 100, dateTime);
        }

        @Test
        default void testReadingLocalDateTimeFromByte() {
                LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
                testReadingFromDBError((byte) 50, dateTime);
        }

        @Test
        default void testConversionFloatToShort() {
                testWritingToDB(123.7f, (short) 123);
        }

        @Test
        default void testConversionFloatToByte() {
                testWritingToDB(123.7f, (byte) 123);
        }

        @Test
        default void testConversionDoubleToByte() {
                testWritingToDB(789.5d, (byte) 789);
        }

        @Test
        default void testConversionDoubleToShort() {
                testWritingToDB(789.5d, (short) 789);
        }

        @Test
        default void testConversionStringToShort() {
                testWritingToDB("100", (short) 100);
        }

        @Test
        default void testConversionStringToByte() {
                testWritingToDB("50", (byte) 50);
        }

        @Test
        default void testConversionStringToBoolean() {
                testWritingToDB("true", true);
                testWritingToDB("false", false);
        }

        @Test
        default void testConversionStringToDouble() {
                testWritingToDB("123.45", 123.45d);
        }

        @Test
        default void testConversionStringToInteger() {
                testWritingToDB("654", 654);
        }

        @Test
        default void testConversionLongToShort() {
                testWritingToDB(456L, (short) 456);
        }

        @Test
        default void testConversionLongToByte() {
                testWritingToDB(123L, (byte) 123);
        }

        @Test
        default void testConversionBigDecimalToShort() {
                testWritingToDB(new BigDecimal("123.45"), (short) 123);
        }

        @Test
        default void testConversionBigDecimalToByte() {
                testWritingToDB(new BigDecimal("123.45"), (byte) 123);
        }

        @Test
        default void testConversionBigIntegerToShort() {
                testWritingToDB(new BigInteger("789"), (short) 789);
        }

        @Test
        default void testConversionBigIntegerToByte() {
                // This conversion truncates data
                testWritingToDB(new BigInteger("789"), (byte) 21);
        }

        @Test
        default void testConversionShortToFloat() {
                testWritingToDB((short) 100, 100.0f);
        }

        @Test
        default void testConversionByteToFloat() {
                testWritingToDB((byte) 50, 50.0f);
        }

        @Test
        default void testReadingStringFromShort() {
                testReadingFromDB((short) 654, "654");
        }

        @Test
        default void testReadingStringFromByte() {
                testReadingFromDB((byte) 54, "54");
        }

        @Test
        default void testReadingStringFromBigDecimal() {
                testReadingFromDB(new BigDecimal("654.321"), "654.321");
        }

        @Test
        default void testReadingStringFromBigInteger() {
                testReadingFromDB(new BigInteger("654"), "654");
        }

        @Test
        default void testReadingIntegerFromShort() {
                testReadingFromDB((short) 654, 654);
        }

        @Test
        default void testReadingIntegerFromByte() {
                testReadingFromDB((byte) 42, 42);
        }

        @Test
        default void testReadingLongFromShort() {
                testReadingFromDB((short) 654, 654L);
        }

        @Test
        default void testReadingLongFromByte() {
                testReadingFromDB((byte) 123, 123L);
        }

        @Test
        default void testReadingFloatFromShort() {
                testReadingFromDB((short) 654, 654.0f);
        }

        @Test
        default void testReadingFloatFromByte() {
                testReadingFromDB((byte) 123, 123.0f);
        }

        @Test
        default void testReadingDoubleFromShort() {
                testReadingFromDB((short) 654, 654.0d);
        }

        @Test
        default void testReadingDoubleFromByte() {
                testReadingFromDB((byte) 123, 123.0d);
        }

        @Test
        default void testReadingBooleanFromShort() {
                testReadingFromDB((short) 1, true);
                testReadingFromDB((short) 0, false);
        }

        @Test
        default void testReadingBooleanFromByte() {
                testReadingFromDB((byte) 1, true);
                testReadingFromDB((byte) 0, false);
        }

        @Test
        default void testReadingBigDecimalFromShort() {
                testReadingFromDB((short) 654, new BigDecimal("654.0"));
        }

        @Test
        default void testReadingBigDecimalFromByte() {
                testReadingFromDB((byte) 123, new BigDecimal("123.0"));
        }

        @Test
        default void testReadingBigIntegerFromShort() {
                testReadingFromDB((short) 654, new BigInteger("654"));
        }

        @Test
        default void testReadingBigIntegerFromByte() {
                testReadingFromDB((byte) 123, new BigInteger("123"));
        }

        @Test
        default void testConversionBooleanToString() {
                testWritingToDB(true, "true");
                testWritingToDB(false, "false");
        }

        @Test
        default void testReadingUUIDFromBigDecimal() {
                UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(new BigDecimal("123"), uuid1);
        }

        @Test
        default void testReadingUUIDFromBigInteger() {
                UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
                testReadingFromDBError(new BigInteger("123"), uuid1);
        }

        @Test
        default void testConversionUUIDToBoolean() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), true);
        }

        @Test
        default void testConversionUUIDToLong() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123L);
        }

        @Test
        default void testConversionUUIDToDouble() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 123.45d);
        }

        @Test
        default void testConversionUUIDToShort() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (short) 100);
        }

        @Test
        default void testConversionUUIDToByte() {
                testWritingToDBError(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), (byte) 50);
        }

        @Test
        default void testConversionLocalDateToBoolean() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), true);
        }

        @Test
        default void testConversionLocalDateToLong() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123L);
        }

        @Test
        default void testConversionLocalDateToDouble() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), 123.5d);
        }

        @Test
        default void testConversionLocalDateToShort() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), (short) 100);
        }

        @Test
        default void testConversionLocalDateToByte() {
                testWritingToDBError(LocalDate.of(2024, 1, 15), (byte) 50);
        }

        @Test
        default void testConversionLocalDateTimeToBoolean() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), true);
        }

        @Test
        default void testConversionLocalDateTimeToLong() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123L);
        }

        @Test
        default void testConversionLocalDateTimeToDouble() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), 123.5d);
        }

        @Test
        default void testConversionLocalDateTimeToShort() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (short) 100);
        }

        @Test
        default void testConversionLocalDateTimeToByte() {
                testWritingToDBError(LocalDateTime.of(2024, 1, 15, 10, 30, 45), (byte) 50);
        }
}
