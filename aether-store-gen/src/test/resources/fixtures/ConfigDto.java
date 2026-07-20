package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MinLength;
import org.dempsay.aether.annotations.Singleton;

@AetherRecord
@Singleton
public record ConfigDto(@MinLength(1) String env) {
}
