package ch.sbb.matsim.config.variables;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.matsim.api.core.v01.TransportMode;

public class SBBModes {

	private SBBModes() {
	}

	public static final String CAR = TransportMode.car;
	public static final String RIDE = TransportMode.ride;
	public static final String PT = TransportMode.pt;
	public static final String BIKE = TransportMode.bike;
	public static final String AVTAXI = "avtaxi";
	public static final String AVFEEDER = "avfeeder";
	public static final String DRT = TransportMode.drt;

	public static final String PT_FALLBACK_MODE = TransportMode.transit_walk;
	public static final String ACCESS_EGRESS_WALK = TransportMode.walk;
	public static final String WALK_MAIN_MAINMODE = "walk_main";
	//in analysis code, all walk mode are set to this mode
	public static final String WALK_FOR_ANALYSIS = TransportMode.walk;

	public static final int DEFAULT_MODE_HIERARCHY = 99;
	public final static Map<String, Integer> mode2HierarchalNumber;

	static {
		mode2HierarchalNumber = new HashMap<>();

		mode2HierarchalNumber.put(PT, 0);
		mode2HierarchalNumber.put(CAR, 10);
		mode2HierarchalNumber.put(AVTAXI, 11);
		mode2HierarchalNumber.put(DRT, 12);
		mode2HierarchalNumber.put(RIDE, 20);
		mode2HierarchalNumber.put(BIKE, 30);
		mode2HierarchalNumber.put(WALK_FOR_ANALYSIS, 40);
		mode2HierarchalNumber.put(PT_FALLBACK_MODE, 41);
		mode2HierarchalNumber.put(ACCESS_EGRESS_WALK, 50);
	}

    public static class PTSubModes {
        public static final String RAIL = "rail";
        public static final String TRAM = "tram";
        public static final String BUS = "bus";
        public static final String OTHER = "other"; // example for "other": Seilbahn, Gondelbahn, Schiff, ...
        public final static Set<String> submodes;

        static {
            Set submodeset = new HashSet<>();
            submodeset.add(RAIL);
            submodeset.add(TRAM);
            submodeset.add(BUS);
            submodeset.add(OTHER);
            // TODO: remove detPt as soon as we merged the pt-submodeset
            submodeset.add("detPt");
            submodes = Collections.unmodifiableSet(submodeset);
        }

        private PTSubModes() {
        }
    }
}