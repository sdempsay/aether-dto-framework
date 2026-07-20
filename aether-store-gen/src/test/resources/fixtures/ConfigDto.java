package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MinLength;
import org.dempsay.aether.api.annotations.Singleton;

@AetherRecord
@Singleton
public record ConfigDto(@MinLength(1) String env) {
}
