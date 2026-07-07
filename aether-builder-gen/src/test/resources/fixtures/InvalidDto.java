package fixtures;

import java.util.List;

import org.aether.annotations.AetherRecord;

@AetherRecord
public record InvalidDto(List<String> items) {}