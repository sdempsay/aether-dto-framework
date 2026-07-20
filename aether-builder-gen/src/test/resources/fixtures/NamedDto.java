package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MinLength;

@AetherRecord
public record NamedDto(
        @MinLength(1)
        String name
) implements Named {}
