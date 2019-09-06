package ch.sbb.matsim.zones;

import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;

/**
 * @author mrieser
 */
public class ZonesModule extends AbstractModule {

    @Override
    public void install() {
        Config config = getConfig();
        ZonesCollection zonesCollection = new ZonesCollection();
        ZonesLoader.loadAllZones(config, zonesCollection);

        bind(ZonesCollection.class).toInstance(zonesCollection);
    }

}
