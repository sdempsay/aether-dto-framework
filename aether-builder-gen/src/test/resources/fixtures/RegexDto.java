package fixtures;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.RegexMatch;

@AetherRecord
public record RegexDto(
    @RegexMatch(pattern = "\\d{3}")
    String code
) {}