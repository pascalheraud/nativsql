package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeString implements IDataType<String> {
    private Long id;
    private String data;
}
