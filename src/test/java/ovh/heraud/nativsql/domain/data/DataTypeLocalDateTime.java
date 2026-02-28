package ovh.heraud.nativsql.domain.data;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DataTypeLocalDateTime implements IDataType<LocalDateTime> {
    private Long id;
    private LocalDateTime data;
}
