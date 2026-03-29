package ovh.heraud.nativsql.domain.data;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DataTypeDouble implements IDataType<Double> {
    private Long id;
    private Double data;
}
