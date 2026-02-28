package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeBoolean implements IDataType<Boolean> {
    private Long id;
    private Boolean data;
}
