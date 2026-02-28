package ovh.heraud.nativsql.domain.data;

import lombok.Data;

@Data
public class DataTypeInteger implements IDataType<Integer> {
    private Long id;
    private Integer data;
}
