package ch.sbb.matsim.config.variables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SBBActivities {
    private SBBActivities() {}

    public static final String home = "home";
    public static final String education = "education";
    public static final String work = "work";
    public static final String business = "business";
    public static final String leisure = "leisure";
    public static final String shopping = "shopping";
    public static final String accompany = "accompany";
    public static final String other = "other";

    public final static Map<String, String> abmActs2matsimActs;
    public final static Map<String, String> matsimActs2abmActs;

    public final static Set<String> stageActivityTypeList;

    static {
        abmActs2matsimActs = new HashMap<>();

        abmActs2matsimActs.put("L", leisure);
        abmActs2matsimActs.put("B", business);
        abmActs2matsimActs.put("W", work);
        abmActs2matsimActs.put("H", home);
        abmActs2matsimActs.put("E", education);
        abmActs2matsimActs.put("O", other);
        abmActs2matsimActs.put("S", shopping);
        abmActs2matsimActs.put("A", accompany);
    }

    static {
        matsimActs2abmActs = new HashMap<>();

        for(String key: abmActs2matsimActs.keySet())  {
            matsimActs2abmActs.put(abmActs2matsimActs.get(key), key);
        }
    }

    static  {
        stageActivityTypeList = new HashSet<>();

        stageActivityTypeList.add("pt interaction");
        stageActivityTypeList.add("ride interaction");
        stageActivityTypeList.add("car interaction");
        stageActivityTypeList.add("bike interaction");
    }
}
