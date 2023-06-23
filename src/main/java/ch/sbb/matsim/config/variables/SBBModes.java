package ch.sbb.matsim.config.variables;

import org.matsim.api.core.v01.TransportMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SBBModes {

	public static final String CAR = TransportMode.car;
	public static final String RIDE = TransportMode.ride;
	public static final String PT = TransportMode.pt;
	public static final String BIKE = TransportMode.bike;
	public static final String AVTAXI = "avtaxi";
	public static final String AVFEEDER = "av_feeder";
	public static final String BIKEFEEDER = "bike_feeder";
	public static final String RIDEFEEDER = "ride_feeder";
	public static final String CARFEEDER = "car_feeder";
	public static final String DRT = TransportMode.drt;
	public static final String RAIL = "rail";
	public static final String FQRAIL = "fqrail";
	public static final String PT_FALLBACK_MODE = TransportMode.transit_walk;
	public static final String ACCESS_EGRESS_WALK = TransportMode.walk;
	public static final String WALK_MAIN_MAINMODE = "walk_main";
	//in analysis code, all walk mode are set to this mode
	public static final String WALK_FOR_ANALYSIS = TransportMode.walk;
	public static final int DEFAULT_MODE_HIERARCHY = 99;
	public final static Map<String, Integer> mode2HierarchalNumber;
	public final static Map<Integer, String> hierarchalNumber2Mode;

	public static final List<String> MAIN_MODES = List.of(RIDE, CAR, PT, BIKE, AVTAXI, WALK_FOR_ANALYSIS, DRT);
	public static final List<String> TRAIN_STATION_MODES = List.of(AVFEEDER, CARFEEDER, RIDEFEEDER, BIKEFEEDER, WALK_FOR_ANALYSIS, PT_FALLBACK_MODE, PT, PTSubModes.RAIL, PTSubModes.BUS, PTSubModes.OTHER, PTSubModes.TRAM, DRT);
	public static final List<String> TRAIN_STATION_ORIGDEST_MODES = List.of(AVFEEDER, CARFEEDER, RIDEFEEDER, BIKEFEEDER, WALK_FOR_ANALYSIS, PT_FALLBACK_MODE, PTSubModes.BUS, PTSubModes.OTHER, PTSubModes.TRAM, DRT);
	public static final List<String> TRAIN_FEEDER_MODES = List.of(WALK_FOR_ANALYSIS, AVFEEDER, CARFEEDER, RIDEFEEDER, BIKEFEEDER, PTSubModes.RAIL, PTSubModes.BUS, PTSubModes.OTHER, PTSubModes.TRAM);

	static {
		mode2HierarchalNumber = new HashMap<>();
		hierarchalNumber2Mode = new HashMap<>();
		//main modes
		mode2HierarchalNumber.put(PT, 0);
		mode2HierarchalNumber.put(CAR, 10);
		mode2HierarchalNumber.put(AVTAXI, 11);
		mode2HierarchalNumber.put(DRT, 12);
		mode2HierarchalNumber.put(RIDE, 20);
		mode2HierarchalNumber.put(BIKE, 30);
		mode2HierarchalNumber.put(WALK_MAIN_MAINMODE, 40);
		mode2HierarchalNumber.put(WALK_FOR_ANALYSIS, 41);

		//purely pt access-egress
		mode2HierarchalNumber.put(AVFEEDER, 90);
		mode2HierarchalNumber.put(BIKEFEEDER, 91);
		mode2HierarchalNumber.put(RIDEFEEDER, 92);
		mode2HierarchalNumber.put(CARFEEDER, 93);
		mode2HierarchalNumber.put(PT_FALLBACK_MODE, 94);

		//access-egress for all kind of modes
		mode2HierarchalNumber.put(ACCESS_EGRESS_WALK, 99);

		mode2HierarchalNumber.forEach((k, v) -> hierarchalNumber2Mode.put(v, k));
	}

	private SBBModes() {
	}

	public static class PTSubModes {

		public static final String RAIL = "rail";
		public static final String TRAM = "tram";
		public static final String BUS = "bus";
		public static final String OTHER = "other"; // example for "other": Seilbahn, Gondelbahn, Schiff, ...
		public final static Set<String> submodes;

		static {
			// TODO: remove detPt as soon as we merged the pt-submodeset
			submodes = Set.of(RAIL, TRAM, BUS, OTHER, "detPt");
		}

		private PTSubModes() {
		}
	}
}