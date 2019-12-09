package ch.sbb.matsim.routing.access;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRouting;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.routing.teleportation.SBBTeleportation;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
            final Set<String> routedModes = new HashSet<>(ConfigUtils.addOrGetModule(config, SBBNetworkRoutingConfigGroup.class).getNetworkRoutingModes());
            routedModes.addAll(config.qsim().getMainModes());

            for (final String mode : modes) {
                if (routedModes.contains(mode)) {
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
