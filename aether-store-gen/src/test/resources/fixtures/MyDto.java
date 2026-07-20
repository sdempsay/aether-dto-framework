package fixtures;

import org.dempsay.aether.api.annotations.AetherRecord;
import org.dempsay.aether.api.annotations.MinLength;

@AetherRecord
public record MyDto(@MinLength(1) String name) {
}
