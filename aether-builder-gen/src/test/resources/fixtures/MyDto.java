package fixtures;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.MaxLength;
import org.aether.annotations.MinLength;
import org.aether.annotations.RegexMatch;

@AetherRecord
public record MyDto(
    @MinLength(3)
    @MaxLength(50)
    @RegexMatch(pattern = "^[a-zA-Z0-9_]+$")
    String data
) {}