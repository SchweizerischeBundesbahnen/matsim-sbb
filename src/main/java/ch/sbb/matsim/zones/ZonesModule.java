package ch.sbb.matsim.zones;

import ch.sbb.matsim.config.ZonesListConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

/**
 * @author mrieser
 */
public class ZonesModule extends AbstractModule {

    @Override
    public void install() {
        Config config = getConfig();
        ZonesCollections allZonesCollections = new ZonesCollections();
        ZonesLoader.loadAllZones(ConfigUtils.addOrGetModule(config, ZonesListConfigGroup.class), allZonesCollections);

        bind(ZonesCollections.class).toInstance(allZonesCollections);
    }

}
