@AetherStoreProviders(
        filesystem = { MyDto.class },
        singletonFilesystem = { ConfigDto.class },
        memory = { MyDto.class })
package fixtures.server;

import org.dempsay.aether.store.gen.AetherStoreProviders;
import fixtures.ConfigDto;
import fixtures.MyDto;
