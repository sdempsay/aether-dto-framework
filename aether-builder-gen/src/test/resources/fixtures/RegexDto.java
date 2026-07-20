package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.RegexMatch;

@AetherRecord
public record RegexDto(
    @RegexMatch(pattern = "\\d{3}")
    String code
) {}