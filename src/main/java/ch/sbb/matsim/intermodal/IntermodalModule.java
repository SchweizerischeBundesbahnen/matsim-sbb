package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.routing.pt.raptor.*;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.HashSet;
import java.util.Set;

public class IntermodalModule extends AbstractModule {

	public static void prepareIntermodalScenario(Scenario scenario) {
        SBBIntermodalConfiggroup configGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBIntermodalConfiggroup.class);
        for (SBBIntermodalModeParameterSet mode : configGroup.getModeParameterSets()) {
            if (mode.isRoutedOnNetwork()) {
                SBBNetworkRoutingModule.addNetworkMode(scenario.getNetwork(), mode.getMode(), SBBModes.CAR);
                Set<String> routedModes = new HashSet<>(scenario.getConfig().plansCalcRoute().getNetworkModes());
                routedModes.add(mode.getMode());
                scenario.getConfig().plansCalcRoute().setNetworkModes(routedModes);
			}
			if (mode.isSimulatedOnNetwork()) {
				Set<String> mainModes = new HashSet<>(scenario.getConfig().qsim().getMainModes());
				mainModes.add(mode.getMode());
				scenario.getConfig().qsim().setMainModes(mainModes);
			}
		}
	}

	@Override
	public void install() {
		SBBIntermodalConfiggroup configGroup = ConfigUtils.addOrGetModule(this.getConfig(), SBBIntermodalConfiggroup.class);
		SwissRailRaptorConfigGroup swissRailRaptorConfigGroup = ConfigUtils.addOrGetModule(this.getConfig(), SwissRailRaptorConfigGroup.class);
		if (swissRailRaptorConfigGroup.isUseIntermodalAccessEgress()) {
			for (SBBIntermodalModeParameterSet mode : configGroup.getModeParameterSets()) {
				if (mode.isRoutedOnNetwork() && !mode.getMode().equals(SBBModes.CAR)) {
					addTravelTimeBinding(mode.getMode()).to(networkTravelTime());
					addTravelDisutilityFactoryBinding(mode.getMode()).to(carTravelDisutilityFactoryKey());
				}
			}
			bind(RaptorIntermodalAccessEgress.class).to(SBBRaptorIntermodalAccessEgress.class).asEagerSingleton();
			bind(AccessEgressRouteCache.class).to(GridbasedAccessEgressCache.class).asEagerSingleton();
			bind(RaptorStopFinder.class).to(SBBIntermodalRaptorStopFinder.class);
		}
	}

}


