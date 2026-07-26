package ovh.heraud.nativsql.util;

import java.time.LocalDateTime;

/**
 * Test-fixture {@link ComputedValueProvider} used by {@code @OnInsert}/{@code @OnUpdate}
 * in this project's own tests, for {@code LocalDateTime}-typed fields — returns the
 * current local date/time. Not part of the published {@code nativsql-core} artifact
 * (lives in {@code testFixtures}).
 */
public class SystemLocalDateTimeComputedValueProvider implements ComputedValueProvider<LocalDateTime> {

    @Override
    public LocalDateTime getValue() {
        return LocalDateTime.now();
    }
}
