package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.repository.GenericRepository;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a JOIN clause in a query.
 * Specifies which association to join, which columns to retrieve, and the type of join.
 */
@Data
@NoArgsConstructor
public class Join {
    private String name;
    private List<String> columns;
    private boolean isLeftJoin;
    private GenericRepository<?, ?> repository;

    /**
     * Creates a new Join with specified join type.
     * @param name the association name (property name)
     * @param columns the columns to retrieve from the joined entity
     * @param isLeftJoin true for LEFT JOIN, false for INNER JOIN
     * @param repository the repository of the joined entity
     */
    public Join(String name, List<String> columns, boolean isLeftJoin, GenericRepository<?, ?> repository) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
        this.isLeftJoin = isLeftJoin;
        this.repository = repository;
    }
}

