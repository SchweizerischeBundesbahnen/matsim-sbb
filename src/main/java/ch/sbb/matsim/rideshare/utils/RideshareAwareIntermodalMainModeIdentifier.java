package ch.sbb.matsim.rideshare.utils;

import ch.sbb.matsim.analysis.zonebased.IntermodalAwareRouterModeIdentifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripRouter;

public class RideshareAwareIntermodalMainModeIdentifier implements MainModeIdentifier {

	private final IntermodalAwareRouterModeIdentifier delegate;
	private final Map<String, String> drtWalkTypes;

	@Inject
	public RideshareAwareIntermodalMainModeIdentifier(Config config) {
		this.delegate = new IntermodalAwareRouterModeIdentifier(config);
		MultiModeDrtConfigGroup drtCfg = MultiModeDrtConfigGroup.get(config);
		this.drtWalkTypes = drtCfg.getModalElements().stream()
				.map((drtConfigGroup) -> drtConfigGroup.getMode())
				.collect(Collectors.toMap(s -> TripRouter.getFallbackMode(s), s -> s));
	}

	@Override
	public String identifyMainMode(List<? extends PlanElement> tripElements) {
		String delegateMode = delegate.identifyMainMode(tripElements);
		if (drtWalkTypes.containsKey(delegateMode)) {
			return drtWalkTypes.get(delegateMode);
		} else {
			return delegateMode;
		}
	}
}
