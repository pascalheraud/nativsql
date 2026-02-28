package ovh.heraud.nativsql.domain.data;

import java.time.LocalDate;

import lombok.Data;

@Data
public class DataTypeLocalDate implements IDataType<LocalDate> {
    private Long id;
    private LocalDate data;
}
