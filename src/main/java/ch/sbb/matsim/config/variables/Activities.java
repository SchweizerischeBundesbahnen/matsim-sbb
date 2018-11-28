package ch.sbb.matsim.config.variables;

import java.util.HashMap;
import java.util.Map;

public class Activities {


    public final static Map<String, String> abmActs2matsimActs;

    static {
        abmActs2matsimActs = new HashMap<>();

        abmActs2matsimActs.put("L", "leisure");
        abmActs2matsimActs.put("B", "business");
        abmActs2matsimActs.put("W", "work");
        abmActs2matsimActs.put("H", "home");
        abmActs2matsimActs.put("E", "education");
        abmActs2matsimActs.put("EC", "education_continuous");
        abmActs2matsimActs.put("O", "other");
        abmActs2matsimActs.put("S", "shop");
        abmActs2matsimActs.put("A", "accompany");


    }

}
