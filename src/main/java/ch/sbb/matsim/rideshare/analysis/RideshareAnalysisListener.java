package ch.sbb.matsim.rideshare.analysis;

import jakarta.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

/**
 * @author jbischoff / SBB
 */
public class RideshareAnalysisListener implements IterationEndsListener {

	@Inject
	private Config config;

	@Inject
	private ZonebasedRideshareAnalysis rideshareAnalysis;

	@Inject
	private MatsimServices services;

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		rideshareAnalysis.writeStats(services.getControlerIO().getIterationFilename(event.getIteration(), "drtZoneStats"));
	}
}
