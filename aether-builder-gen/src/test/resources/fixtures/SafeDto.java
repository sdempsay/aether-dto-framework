package fixtures;

import org.aether.annotations.AetherRecord;
import org.aether.annotations.Nullable;

@AetherRecord
public record SafeDto(@Nullable String notes) {}