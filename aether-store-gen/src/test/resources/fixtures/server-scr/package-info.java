@AetherStoreProviders(
        filesystem = { MyDto.class },
        singletonFilesystem = { ConfigDto.class },
        memory = { MyDto.class },
        scr = true)
package fixtures.server.scr;

import org.dempsay.aether.store.gen.AetherStoreProviders;
import fixtures.ConfigDto;
import fixtures.MyDto;
