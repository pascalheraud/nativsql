package ovh.heraud.nativsql.repository;

import javax.sql.DataSource;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.mapper.RowMapperFactory;

/**
 * Generic data type repository that can be instantiated with any entity class and table name.
 * This allows creating a matrix of repositories for testing type mappings.
 *
 * Usage:
 *   DataTypeRepository<DataTypeLong> longRepo = new DataTypeRepository<>(
 *       DataTypeLong.class, "data_type_long", dataSource, dialect, rowMapperFactory
 *   );
 *   longRepo.initialize(); // Must be called to initialize JDBC template
 *
 * @param <T> the entity type implementing IEntity with Long ID
 */
public class DataTypeRepository<T extends IEntity<Long>> extends GenericRepository<T, Long> {

    private final Class<T> entityClass;
    private final DataSource dataSource;
    private final DatabaseDialect databaseDialect;

    public DataTypeRepository(Class<T> entityClass, String tableName, DataSource dataSource,
                            DatabaseDialect databaseDialect, RowMapperFactory rowMapperFactory,
                            AnnotationManager annotationManager) {
        super(entityClass, tableName, rowMapperFactory, annotationManager);
        this.entityClass = entityClass;
        this.dataSource = dataSource;
        this.databaseDialect = databaseDialect;
        initJdbcTemplate();
    }

    @Override
    protected DataSource getDataSource() {
        return dataSource;
    }

    @Override
    protected Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return databaseDialect;
    }

    /**
     * Inserts a value into the 'data' column of this repository's table.
     *
     * @param entity the entity containing the data value
     */
    public void insertData(T entity) {
        insert(entity, "data");
    }

    /**
     * Retrieves the first row from this repository's table as the entity type.
     *
     * @return the entity with the data field populated
     */
    public T getData() {
        return findById(1L, "data");
    }

    /**
     * Deletes all rows from this repository's table.
     */
    public void deleteAll() {
        executeUpdate("DELETE FROM " + tableName, java.util.Map.of());
    }
}
