package ch.sbb.matsim.routing.network;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.HashSet;
import java.util.Set;

/**
 * @author jbischoff / SBB
 */
public class SBBNetworkRoutingModule extends AbstractModule {

    private final Set<String> routedModes;

    public SBBNetworkRoutingModule(Scenario scenario) {
        this.routedModes = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBNetworkRoutingConfigGroup.class).getNetworkRoutingModes();
        for (String mode : routedModes) {
            addNetworkMode(scenario.getNetwork(), mode, SBBModes.CAR);
        }
    }

    public static void addNetworkMode(Network network, String transportMode, String routingMode) {
        for (Link l : network.getLinks().values()) {
            if (l.getAllowedModes().contains(routingMode)) {
                Set<String> allowedModes = new HashSet<>(l.getAllowedModes());
                allowedModes.add(transportMode);
                l.setAllowedModes(allowedModes);
            }
        }
    }


    @Override
    public void install() {
        for (String mode : routedModes) {
            addTravelTimeBinding(mode).to(networkTravelTime());
            addTravelDisutilityFactoryBinding(mode).to(carTravelDisutilityFactoryKey());
        }
    }
}

