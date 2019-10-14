package ch.sbb.matsim.config.variables;

import java.util.HashMap;
import java.util.Map;

public class SBBModes {
    private SBBModes() {}

    public static final String car = "car";
    public static final String ride = "ride";
    public static final String pt = "pt";
    public static final String pt_rail = "pt_rail";
    public static final String walk = "walk";
    public static final String bike = "bike";

    public final static Map<String, Integer> mode2HierarchalNumber;
    public final static Map<Integer, String> hierarchalNumber2Mode;

    static {
        mode2HierarchalNumber = new HashMap<>();

        mode2HierarchalNumber.put(pt_rail, 1);
        mode2HierarchalNumber.put(pt, 2);
        mode2HierarchalNumber.put(car, 3);
        mode2HierarchalNumber.put(ride, 4);
        mode2HierarchalNumber.put(bike, 5);
        mode2HierarchalNumber.put(walk, 6);
    }

    static {
        hierarchalNumber2Mode = new HashMap<>();

        for(String key: mode2HierarchalNumber.keySet())  {
            hierarchalNumber2Mode.put(mode2HierarchalNumber.get(key), key);
        }
    }
}
