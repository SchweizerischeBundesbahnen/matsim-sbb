package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.TripStructureUtils;
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
                attributes.put("originActivity", new ActivityAttributable(trip.getOriginActivity()));
                attributes.put("destinationActivity", new ActivityAttributable(trip.getDestinationActivity()));
                attributes.put("distance", trip.getLegsOnly().stream().mapToDouble(leg -> {
                    Route r = leg.getRoute();
                    Double d = null;
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

        }

        static class ActivityAttributes extends statObjectAttributes {
            Activity activity;

            ActivityAttributes(Activity activity) {
                this.activity = activity;
                attributes = new HashMap<>();
                attributes.put("Zone", zones.findZone(activity.getCoord()));
            }

        }

    }
}