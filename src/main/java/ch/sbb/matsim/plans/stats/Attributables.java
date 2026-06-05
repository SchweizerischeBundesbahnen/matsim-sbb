package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.plans.models.steps.CombinedDestinationAndDurationChoiceModel;
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
            private ActivityAttributable cachedOrigin;
            private ActivityAttributable cachedDestination;

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
                if (cachedOrigin == null) {
                    cachedOrigin = tripAttributes.getOriginActivity();
                }
                return cachedOrigin;
            }

            public ActivityAttributable getDestinationActivity() {
                if (cachedDestination == null) {
                    cachedDestination = tripAttributes.getDestinationActivity();
                }
                return cachedDestination;
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
                attributes = new HashMap<>(16);
                attributes.put("weight", 1);
                String mode = "-";
                for (Leg leg : trip.getLegsOnly()) {
                    String legMode = leg.getMode();
                    if (legMode != null) {
                        mode = legMode;
                        break;
                    }
                }
                attributes.put("mode", mode);

                double modeMIV = 0.0, modePt = 0.0, modeLV = 0.0;
                switch (mode) {
                    case "car":
                    case "ride":
                    case "avtaxi":
                        modeMIV = 1.0;
                        break;
                    case "pt":
                        modePt = 1.0;
                        break;
                    case "walk":
                    case "walk_main":
                    case "bike":
                        modeLV = 1.0;
                        break;
                    default:
                        // keep zeros
                }
                attributes.put("modeMIV", modeMIV);
                attributes.put("modePt", modePt);
                attributes.put("modeLV", modeLV);

                double distance = 0.0;
                double railDistance = 0.0;
                for (Leg leg : trip.getLegsOnly()) {
                    Attributes atts = leg.getAttributes();
                    Double d = (Double) atts.getAttribute("car_net_distance");
                    if (d != null) {
                        distance += d / 1000.0;
                    } else {
                        Route r = leg.getRoute();
                        if (r != null) {
                            distance += r.getDistance() / 1000.0;
                        }
                    }
                    if ("rail".equals(leg.getMode())) {
                        Route r = leg.getRoute();
                        if (r != null) {
                            railDistance += r.getDistance();
                        }
                    }
                }
                attributes.put("distance", distance);
                attributes.put("rail_distance", railDistance);
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
                Zone zone = (Zone) activity.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
                return zone==null?zones.findZone(activity.getCoord()):zone;
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