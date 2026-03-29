package ovh.heraud.nativsql.domain.data;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class DataTypeBigDecimal implements IDataType<BigDecimal> {
    private Long id;
    private BigDecimal data;
}
