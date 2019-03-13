package ch.sbb.matsim.routing.access;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRouting;
import ch.sbb.matsim.routing.teleportation.SBBTeleportation;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.Collection;

public class AccessEgress extends AbstractModule {

    private final Scenario scenario;
    private final LocateAct locateAct;

    public AccessEgress(Scenario scenario) {
        super(scenario.getConfig());
        this.scenario = scenario;

        SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
        this.locateAct = new LocateAct(accessTimeConfigGroup.getShapefile());
        this.locateAct.fillCache(this.scenario.getPopulation());
    }

    @Override
    public void install() {
        Config config = getConfig();
        SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);

        this.bind(LocateAct.class).toInstance(locateAct);

        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            config.plansCalcRoute().setInsertingAccessEgressWalk(true);

            Collection<String> modes = accessTimeConfigGroup.getModesWithAccessTime();
            final Collection<String> mainModes = config.qsim().getMainModes();

            for (final String mode : modes) {
                if (mainModes.contains(mode) || mode.equals(TransportMode.ride)) {
                    addRoutingModuleBinding(mode).toProvider(
                            new SBBNetworkRouting(mode, locateAct)
                    );
                } else {
                    addRoutingModuleBinding(mode).toProvider(
                            new SBBTeleportation(config.plansCalcRoute().getOrCreateModeRoutingParams(mode), locateAct)
                    );
                }
            }
        }
    }

}
