package ch.sbb.matsim.routing.access;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRouting;
import ch.sbb.matsim.routing.teleportation.SBBTeleportation;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.Collection;

public class AccessEgress extends AbstractModule {

    public AccessEgress(Scenario scenario) {
        super(scenario.getConfig());
    }

    @Override
    public void install() {
        Config config = getConfig();
        SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
        Id<Zones> zonesId = accessTimeConfigGroup.getZonesId();

        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            config.plansCalcRoute().setInsertingAccessEgressWalk(true);

            Collection<String> modes = accessTimeConfigGroup.getModesWithAccessTime();
            final Collection<String> mainModes = config.qsim().getMainModes();

            for (final String mode : modes) {
                if (mainModes.contains(mode) || mode.equals(TransportMode.ride)) {
                    addRoutingModuleBinding(mode).toProvider(
                            new SBBNetworkRouting(mode, zonesId)
                    );
                } else {
                    addRoutingModuleBinding(mode).toProvider(
                            new SBBTeleportation(config.plansCalcRoute().getOrCreateModeRoutingParams(mode), zonesId)
                    );
                }
            }
        }
    }

}
