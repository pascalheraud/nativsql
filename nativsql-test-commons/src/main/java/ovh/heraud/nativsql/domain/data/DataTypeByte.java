package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeByte implements IDataType<Byte> {
    private Long id;
    private Byte data;
}
