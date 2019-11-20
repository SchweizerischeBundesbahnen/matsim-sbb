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
    public static final String TRANSIT_WALK = TransportMode.transit_walk;
    public static final String BIKE = TransportMode.bike;

    public static final int DEFAULT_MODE_HIERARCHY = 99;
    public final static Map<String, Integer> mode2HierarchalNumber;

    static {
        mode2HierarchalNumber = new HashMap<>();

        mode2HierarchalNumber.put(PT, 0);
        mode2HierarchalNumber.put(CAR, 10);
        mode2HierarchalNumber.put(RIDE, 20);
        mode2HierarchalNumber.put(BIKE, 30);
        mode2HierarchalNumber.put(WALK, 40);
        mode2HierarchalNumber.put(TRANSIT_WALK, 41);
    }

    public static class PTSubModes {
        private PTSubModes()    {}

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
        }
    }
}
