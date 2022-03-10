package ch.sbb.matsim.config.variables;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Variables {

    public static final String SUBPOPULATION = "subpopulation";
    public static final String REGULAR = "regular";
    public static final String NO_REPLANNING = "no_replanning";
    public static final String AIRPORT_RAIL = "airport_rail";
    public static final String AIRPORT_ROAD = "airport_road";
    public static final String CB_ROAD = "cb_road";
    public static final String CB_RAIL = "cb_rail";
    public static final String TOURISM_RAIL = "tourism_rail";
    public static final String FREIGHT_ROAD = "freight_road";
    public static final List<String> EXOGENEOUS_DEMAND = List.of(AIRPORT_RAIL, AIRPORT_ROAD, CB_RAIL, CB_ROAD, TOURISM_RAIL, FREIGHT_ROAD);

    public static final String PT_SUBSCRIPTION = "pt_subscr";
    public static final String GA = "GA";
    public static final String VA = "VA";
    public static final String HTA = "HTA";

    public static final String ZONE_ID = "zone_id";
    public static final String MS_REGION = "ms_region";
    public static final String T_ZONE = "tZone";

    public static final String INIT_END_TIMES = "initialActivityEndTimes";
    public static final String NO_INIT_END_TIME = "NONE";

    public static final String DEFAULT_ZONE = "999999999";

    public static final String INTERMODAL_ACCESS_LINK_ID = "accessLinkId";
    public static final String PERSONID = "personId";

    public static final String CAR_AVAIL = "car_available";
    public static final String CAR_AVAL_TRUE = "1";

    public static final String OUTSIDE = "outside";

    public static final String SIMBA_CH_PERIMETER = "08_SIMBA_CH_Perimeter";
    public static final String FQ_RELEVANT = "07_FQ_relevant";

    public static final String ABGRENZGRUPPE = "ABGRENZGRUPPE";
    public static final String BETREIBERAGGRLFP = "BETREIBERAGGRLFP";
    public static final String SPARTE = "Sparte";

    public static final List<Id<TransitStopFacility>> EXCEPTIONAL_CH_STOPS = List.of(Id.create("1618", TransitStopFacility.class));

    public static final Set<String> DEFAULT_PERSON_ATTRIBUTES = Set.of("age_cat", CAR_AVAIL, "current_edu", "level_of_employment_cat", PT_SUBSCRIPTION, "residence_msr_id", "residence_zone_id");
    public static final String MUN_NAME = "mun_name";
    public static final String ACCESS_CONTROLLED = "accessControlled";


    public static class MOBiTripAttributes {
        public static final String TOUR_ID = "next_trip_tour_id";
        public static final String TRIP_ID = "next_trip_id";
        public static final String PURPOSE = "next_trip_purpose";
        public static final String DIRECTION = "next_trip_direction";

        private final Integer tourId;
        private final Integer tripId;
        private final String tripPurpose;
        private final String tripDirection;

        public MOBiTripAttributes(Integer tourId, Integer tripId, String tripPurpose, String tripDirection) {
            this.tourId = tourId;
            this.tripId = tripId;
            this.tripPurpose = tripPurpose;
            this.tripDirection = tripDirection;
        }

        public static IdMap<Person, LinkedList<MOBiTripAttributes>> extractTripAttributes(Population population) {
            IdMap<Person, LinkedList<MOBiTripAttributes>> tripAttributes = new IdMap<>(Person.class, population.getPersons().size());
            for (var p : population.getPersons().values()) {
                LinkedList<MOBiTripAttributes> personTripAttributes = TripStructureUtils.getActivities(p.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream()
                        .map(activity -> {
                            Integer tourId = (Integer) activity.getAttributes().getAttribute(Variables.MOBiTripAttributes.TOUR_ID);
                            String purpose = (String) activity.getAttributes().getAttribute(Variables.MOBiTripAttributes.PURPOSE);
                            String direction = (String) activity.getAttributes().getAttribute(Variables.MOBiTripAttributes.DIRECTION);
                            var tripId = activity.getAttributes().getAttribute(Variables.MOBiTripAttributes.TRIP_ID);
                            if (tripId instanceof Integer) {
                                return new Variables.MOBiTripAttributes(tourId, (Integer) tripId, purpose, direction);
                            } else if (tripId instanceof String) {
                                //backwards compatibility
                                var split = ((String) tripId).split("_");
                                tripId = Integer.parseInt(split[0]);
                                tourId = Integer.parseInt(split[1].split("\"")[0]);
                                return new Variables.MOBiTripAttributes(tourId, (Integer) tripId, "", "");
                            } else return null;

                        }).filter(
                                Objects::nonNull).collect(Collectors.toCollection(LinkedList::new));
                tripAttributes.put(p.getId(), personTripAttributes);
            }
            return tripAttributes;
        }

        public int getTourId() {
            return tourId;
        }

        public int getTripId() {
            return tripId;
        }

        public String getTripPurpose() {
            return tripPurpose;
        }

        public String getTripDirection() {
            return tripDirection;
        }
    }

}
