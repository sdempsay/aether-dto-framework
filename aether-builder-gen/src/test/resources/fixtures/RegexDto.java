package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.RegexMatch;

@AetherRecord
public record RegexDto(
    @RegexMatch(pattern = "\\d{3}")
    String code
) {}