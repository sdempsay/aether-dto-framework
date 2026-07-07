package fixtures;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.MinLength;

@AetherRecord
public record NamedDto(
        @MinLength(1)
        String name
) implements Named {}