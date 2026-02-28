package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeByteArray implements IDataType<byte[]> {
    private Long id;
    private byte[] data;
}
