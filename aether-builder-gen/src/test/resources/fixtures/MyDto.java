package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MaxLength;
import org.dempsay.aether.annotations.MinLength;
import org.dempsay.aether.annotations.RegexMatch;

@AetherRecord
public record MyDto(
    @MinLength(3)
    @MaxLength(50)
    @RegexMatch(pattern = "^[a-zA-Z0-9_]+$")
    String data
) {}
