package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.Nullable;

@AetherRecord
public record SafeDto(@Nullable String notes) {}
