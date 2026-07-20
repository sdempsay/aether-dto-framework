package fixtures;

import org.dempsay.aether.annotations.AetherRecord;
import org.dempsay.aether.annotations.MinLength;

@AetherRecord
public record MyDto(@MinLength(1) String name) {
}
