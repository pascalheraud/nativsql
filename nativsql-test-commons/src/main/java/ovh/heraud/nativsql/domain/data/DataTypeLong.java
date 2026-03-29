package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeLong implements IDataType<Long> {
    private Long id;
    private Long data;
}
