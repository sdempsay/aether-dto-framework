package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MaxLength;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.RegexMatch;

@AetherRecord
public record MyDto(
    @MinLength(3)
    @MaxLength(50)
    @RegexMatch(pattern = "^[a-zA-Z0-9_]+$")
    String data
) {}
