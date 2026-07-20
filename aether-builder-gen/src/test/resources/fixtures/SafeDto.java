package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.Nullable;

@AetherRecord
public record SafeDto(@Nullable String notes) {}
