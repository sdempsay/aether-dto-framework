package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MinLength;

@AetherRecord
public record NamedDto(
        @MinLength(1)
        String name
) implements Named {}
