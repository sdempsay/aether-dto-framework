package fixtures;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.MinLength;
import org.aether.annotations.Singleton;

@AetherRecord
@Singleton
public record ConfigDto(
        @MinLength(1) String theme
) {}
