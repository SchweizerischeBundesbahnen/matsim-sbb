package ch.sbb.matsim.config.variables;

import java.util.HashMap;
import java.util.Map;

public class SBBModes {
    private SBBModes() {}

    public static final String CAR = "car";
    public static final String RIDE = "ride";
    public static final String PT = "pt";
    public static final String WALK = "walk";
    public static final String BIKE = "bike";

    public static final int DEFAULT_MODE_HIERARCHY = 99;
    public final static Map<String, Integer> mode2HierarchalNumber;

    static {
        mode2HierarchalNumber = new HashMap<>();

        mode2HierarchalNumber.put(PT, 0);
        mode2HierarchalNumber.put(CAR, 10);
        mode2HierarchalNumber.put(RIDE, 20);
        mode2HierarchalNumber.put(BIKE, 30);
        mode2HierarchalNumber.put(WALK, 40);
    }
}
