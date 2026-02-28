package ovh.heraud.nativsql.domain.data;

import java.math.BigInteger;

import lombok.Data;

@Data
public class DataTypeBigInteger implements IDataType<BigInteger> {
    private Long id;
    private BigInteger data;
}
