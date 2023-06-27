package ch.sbb.matsim.rideshare.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.intermodal.IntermodalAwareRouterModeIdentifier;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.router.MainModeIdentifier;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RideshareAwareIntermodalMainModeIdentifier implements MainModeIdentifier {

	private final IntermodalAwareRouterModeIdentifier delegate;
	private final Map<String, String> drtWalkTypes;

	@Inject
	public RideshareAwareIntermodalMainModeIdentifier(Config config) {
		this.delegate = new IntermodalAwareRouterModeIdentifier(config);
		MultiModeDrtConfigGroup drtCfg = MultiModeDrtConfigGroup.get(config);
        this.drtWalkTypes = drtCfg.getModalElements().stream()
				.map(DrtConfigGroup::getMode)
                .collect(Collectors.toMap(s -> SBBModes.WALK_FOR_ANALYSIS, s -> s));
	}

	@Override
	public String identifyMainMode(List<? extends PlanElement> tripElements) {
		String delegateMode = delegate.identifyMainMode(tripElements);
        return drtWalkTypes.getOrDefault(delegateMode, delegateMode);
	}
}
