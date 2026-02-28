package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeFloat implements IDataType<Float> {
    private Long id;
    private Float data;
}
