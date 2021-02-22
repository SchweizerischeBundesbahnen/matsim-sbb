package ch.sbb.matsim.config.variables;

import java.util.List;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class Variables {

	public static final String SUBPOPULATION = "subpopulation";
	public static final String REGULAR = "regular";

	public static final String PT_SUBSCRIPTION = "pt_subscr";
	public static final String GA = "GA";
	public static final String VA = "VA";
	public static final String HTA = "HTA";

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
	public static final String NEXT_TRIP_ID_ATTRIBUTE = "next_trip_id";

	public static final String SIMBA_CH_PERIMETER = "08_SIMBA_CH_Perimeter";
	public static final String FQ_RELEVANT = "07_FQ_relevant";

	public static final String ABGRENZGRUPPE = "ABGRENZGRUPPE";
	public static final String BETREIBERAGGRLFP = "BETREIBERAGGRLFP";
	public static final String SPARTE = "Sparte";

	public static final List<Id<TransitStopFacility>> EXCEPTIONAL_CH_STOPS = List.of(Id.create("1618", TransitStopFacility.class));

	public static final Set<String> DEFAULT_PERSON_ATTRIBUTES = Set.of("age_cat", CAR_AVAIL, "current_edu", "level_of_employment_cat", PT_SUBSCRIPTION, "residence_msr_id", "residence_zone_id");
}
