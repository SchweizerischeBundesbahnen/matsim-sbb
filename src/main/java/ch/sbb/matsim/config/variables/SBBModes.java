package ch.sbb.matsim.config.variables;

import org.matsim.api.core.v01.TransportMode;

import java.util.List;
import java.util.Set;

public class SBBModes {

	public static final String CAR = TransportMode.car;
	public static final String RIDE = TransportMode.ride;
	public static final String PT = TransportMode.pt;
	public static final String BIKE = TransportMode.bike;
	public static final String EBIKE = "ebike";
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
	public static final String BUS = "bus";
	public static final String TRAM = "tram";
	public static final String OTHER = "other";
	//in analysis code, all walk mode are set to this mode
	public static final String WALK_FOR_ANALYSIS = TransportMode.walk;
	public static final List<String> TRAIN_STATION_MODES = List.of(WALK_FOR_ANALYSIS, PT, PTSubModes.RAIL, PTSubModes.BUS, PTSubModes.OTHER, PTSubModes.TRAM);
	public static final List<String> TRAIN_STATION_ORIGDEST_MODES = List.of(WALK_FOR_ANALYSIS, PTSubModes.BUS, PTSubModes.OTHER, PTSubModes.TRAM);
	public static final List<String> PT_PASSENGER_MODES = List.of(PT, RAIL, BUS, TRAM, OTHER);
	public static final List<String> PT_ROUTING_MODES = List.of(PTRoutingModes.RAIL_ROUTE, PTRoutingModes.BUS_ROUTE, PTRoutingModes.TRAM_ROUTE, PTRoutingModes.OTHER_ROUTE);
	public static final List<String> PT_FEEDER_MODES = List.of(AVFEEDER, RIDEFEEDER, CARFEEDER, BIKEFEEDER);

	public static boolean isPTMode(String mode) {
		return PT_PASSENGER_MODES.contains(mode);
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
			submodes = Set.of(RAIL, TRAM, BUS, OTHER);
		}

		private PTSubModes() {
		}
	}
	public static class PTRoutingModes {
		public static final String RAIL_ROUTE = "rail_route";
		public static final String TRAM_ROUTE = "tram_route";
		public static final String BUS_ROUTE = "bus_route";
		public static final String OTHER_ROUTE = "other_route"; // example for "other": Seilbahn, Gondelbahn, Schiff, ...
		public final static Set<String> routingModes;

		static {
			routingModes = Set.of(RAIL_ROUTE, TRAM_ROUTE, BUS_ROUTE, OTHER_ROUTE);
		}

		private PTRoutingModes() {
		}
	}
}