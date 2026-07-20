package fixtures.invalid;

import org.dempsay.aether.store.gen.AetherStoreProviders;
import fixtures.ConfigDto;

@AetherStoreProviders(filesystem = { ConfigDto.class })
public final class SingletonInFilesystem {
}
