package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.HashMap;
import java.util.Map;

public class Attributables {

    public static class AttributableProvider {
        public static Zones zones = null;

        public AttributableProvider(Zones zones) {
            this.zones = zones;
        }

        public static class TripAttributable implements Attributable {
            TripAttributes tripAttributes;

            public TripAttributable(TripStructureUtils.Trip trip, Person person) {
                this.tripAttributes = new TripAttributes(trip);
                this.tripAttributes.putAttribute("person", person);
            }

            public void addLookupAttributes(Map<String, Object> lookupAttributes) {
                for (String attr : lookupAttributes.keySet()) {
                    this.tripAttributes.putAttribute(attr, lookupAttributes.get(attr));
                }
            }

            @Override
            public Attributes getAttributes() {
                return tripAttributes;
            }

            public ActivityAttributable getOriginActivity() {
                return tripAttributes.getOriginActivity();
            }

            public ActivityAttributable getDestinationActivity() {
                return tripAttributes.getDestinationActivity();
            }
        }

        public static class ActivityAttributable implements Attributable {
            ActivityAttributes activityAttributes;

            public ActivityAttributable(Activity activity) {
                this.activityAttributes = new ActivityAttributes(activity);
            }

            @Override
            public Attributes getAttributes() {
                return activityAttributes;
            }

            public Id<ActivityFacility>  getFacilityId() {
                return activityAttributes.getFacilityId();
            }

            public String getType() {
                return activityAttributes.getType();
            }

            public Zone getZone() {
                return activityAttributes.getZone();
            }
        }

        public static class statObjectAttributes implements Attributes {
            Map<String, Object> attributes;

            @Override
            public Object putAttribute(String s, Object o) {
                attributes.put(s, o);
                return null;
            }

            @Override
            public Object getAttribute(String s) {
                return attributes.get(s);
            }

            @Override
            public Object removeAttribute(String s) {
                return null;
            }

            @Override
            public void clear() {
                attributes = new HashMap<>();
            }

            @Override
            public Map<String, Object> getAsMap() {
                return attributes;
            }

            @Override
            public int size() {
                return attributes.size();
            }

            @Override
            public boolean isEmpty() {
                return attributes.isEmpty();
            }
        }

        static class TripAttributes extends statObjectAttributes {
            TripStructureUtils.Trip trip;

            TripAttributes(TripStructureUtils.Trip trip) {
                this.trip = trip;
                attributes = new HashMap<>();
                attributes.put("weight", 1);
                attributes.put("mode", "-");
                for (Leg leg: trip.getLegsOnly()) {
                    if (leg.getMode() != null) {
                        attributes.put("mode", leg.getMode());
                        break;
                    }
                }
                switch (attributes.get("mode").toString()) {
                    case "car":
                    case "ride":
                    case "avtaxi": {
                        attributes.put("modeMIV", 1.0);
                        break;
                    }
                    default: attributes.put("modeMIV", 0.0);
                }

                switch (attributes.get("mode").toString()) {
                    case "pt": {
                        attributes.put("modePt", 1.0);
                        break;
                    }
                    default: attributes.put("modePt", 0.0);
                }

                switch (attributes.get("mode").toString()) {
                    case "walk":
                    case "walk_main":
                    case "bike":
                        attributes.put("modeLV", 1.0);
                        break;
                    default:
                        attributes.put("modeLV", 0.0);
                }

                attributes.put("distance", trip.getLegsOnly().stream().mapToDouble(leg -> {
                    Attributes atts = leg.getAttributes();
                    Double d = null;
                    d = (Double) atts.getAttribute("car_net_distance");
                    if (d != null) return d;
                    Route r = leg.getRoute();
                    if (r != null) {
                        d = r.getDistance();
                    }
                    return d == null ? 0.0 : d;
                }).sum());
                attributes.put("rail_distance", trip.getLegsOnly().stream().filter(leg -> leg.getMode().equals("rail")).mapToDouble(leg -> {
                    Route r = leg.getRoute();
                    Double d = null;
                    if (r != null) {
                        d = r.getDistance();
                    }
                    return d == null ? 0.0 : d;
                }).sum());
            }

            public ActivityAttributable getOriginActivity() {
                return new ActivityAttributable(trip.getOriginActivity());
            }

            public ActivityAttributable getDestinationActivity() {
                return new ActivityAttributable(trip.getDestinationActivity());
            }

            public Double getModeMIV() {
                switch (attributes.get("mode").toString()) {
                    case "car": return 1.0;
                    case "ride": return 1.0;
                    case "avtaxi": return 1.0;
                    default: return 0.0;
                }
            }

            public Double getModePT() {
                switch (attributes.get("mode").toString()) {
                    case "pt": return 1.0;
                    default: return 0.0;
                }
            }

            public Double getModeLV() {
                switch (attributes.get("mode").toString()) {
                    case "walk": return 1.0;
                    case "walk_main": return 1.0;
                    case "bike": return 1.0;
                    default: return 0.0;
                }
            }
        }

        static class ActivityAttributes extends statObjectAttributes {
            Activity activity;

            ActivityAttributes(Activity activity) {
                this.activity = activity;
                attributes = new HashMap<>();
            }

            Zone getZone() {
                return zones.findZone(activity.getCoord());
            }
            Id<ActivityFacility> getFacilityId() {
                return activity.getFacilityId();
            }

            String getType() {
                return activity.getType();
            }
        }

    }
}