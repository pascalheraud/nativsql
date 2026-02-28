package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeShort implements IDataType<Short> {
    private Long id;
    private Short data;
}
