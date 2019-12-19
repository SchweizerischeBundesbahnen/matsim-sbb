package ch.sbb.matsim.config.variables;

import org.matsim.api.core.v01.TransportMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SBBModes {
    private SBBModes() {}

    public static final String CAR = TransportMode.car;
    public static final String RIDE = TransportMode.ride;
    public static final String PT = TransportMode.pt;
    public static final String WALK = TransportMode.walk;
    public static final String BIKE = TransportMode.bike;
    public static final String AVTAXI = "avtaxi";
    public static final String AVFEEDER = "avfeeder";
    public static final String DRT = TransportMode.drt;
    public static final String PT_FALLBACK_MODE = TransportMode.transit_walk;
    public static final String NON_NETWORK_WALK = TransportMode.non_network_walk;

    public static final int DEFAULT_MODE_HIERARCHY = 99;
    public final static Map<String, Integer> mode2HierarchalNumber;

    static {
        mode2HierarchalNumber = new HashMap<>();

        mode2HierarchalNumber.put(PT, 0);
        mode2HierarchalNumber.put(CAR, 10);
        mode2HierarchalNumber.put(RIDE, 20);
        mode2HierarchalNumber.put(BIKE, 30);
        mode2HierarchalNumber.put(WALK, 40);
        mode2HierarchalNumber.put(PT_FALLBACK_MODE, 41);
        mode2HierarchalNumber.put(NON_NETWORK_WALK, 50);
    }

    public static class PTSubModes {
        public static final String RAIL = "rail";
        public static final String TRAM = "tram";
        public static final String BUS = "bus";
        public static final String OTHER = "other"; // example for "other": Seilbahn, Gondelbahn, Schiff, ...
        public final static List<String> subModeList;

        static {
            subModeList = new ArrayList<>();
            subModeList.add(RAIL);
            subModeList.add(TRAM);
            subModeList.add(BUS);
            subModeList.add(OTHER);
            // TODO: remove detPt as soon as we merged the pt-submodes
            subModeList.add("detPt");
        }

        private PTSubModes() {
        }
    }
}