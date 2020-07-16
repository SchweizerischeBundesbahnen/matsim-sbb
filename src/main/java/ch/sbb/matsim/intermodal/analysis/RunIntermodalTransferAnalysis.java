package ch.sbb.matsim.intermodal.analysis;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
 * @author jbischoff / SBB
 */
public class RunIntermodalTransferAnalysis {

	public static void main(String[] args) {
		String eventsFile = args[0];
		Set<String> intermodalModes = Arrays.stream(args[1].split(",")).collect(Collectors.toSet());
		Set<String> ptModes = Arrays.stream(args[2].split(",")).collect(Collectors.toSet());

		EventsManager eventsManager = EventsUtils.createEventsManager();
		IntermodalTransferTimeAnalyser analyser = new IntermodalTransferTimeAnalyser(intermodalModes, ptModes);

		eventsManager.addHandler(analyser);
		new MatsimEventsReader(eventsManager).readFile(eventsFile);

		analyser.writeIterationStats(eventsFile + ".intermodalStats");

	}
}
