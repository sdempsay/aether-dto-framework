package fixtures;

import java.util.List;

import org.dempsay.aether.api.annotations.AetherRecord;

@AetherRecord
public record InvalidDto(List<String> items) {}
