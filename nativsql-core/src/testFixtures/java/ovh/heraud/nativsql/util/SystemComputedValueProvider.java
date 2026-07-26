package ovh.heraud.nativsql.util;

import java.time.Instant;

/**
 * Test-fixture {@link ComputedValueProvider} used by {@code @OnUpdate}/{@code @OnInsert}
 * in this project's own tests — returns the current timestamp. Covers the
 * common {@code updateDate}/{@code creationDate} case. Not part of the
 * published {@code nativsql-core} artifact (lives in {@code testFixtures}).
 */
public class SystemComputedValueProvider implements ComputedValueProvider<Instant> {

    @Override
    public Instant getValue() {
        return Instant.now();
    }
}
