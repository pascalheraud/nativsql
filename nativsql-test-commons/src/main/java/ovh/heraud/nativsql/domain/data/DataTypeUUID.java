package ovh.heraud.nativsql.domain.data;

import java.util.UUID;

import lombok.Data;

@Data
public class DataTypeUUID implements IDataType<UUID> {
    private Long id;
    private UUID data;
}
