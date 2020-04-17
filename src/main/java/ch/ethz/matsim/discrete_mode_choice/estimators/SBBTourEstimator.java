package ch.ethz.matsim.discrete_mode_choice.estimators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

import ch.ethz.matsim.discrete_mode_choice.components.estimators.CumulativeTourEstimator;
import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.DefaultTourCandidate;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.TourCandidate;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.TourEstimator;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.TripEstimator;
import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.scoring.SBBCharyparNagelScoringParametersForPerson;
import ch.sbb.matsim.scoring.SBBScoringParameters;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;

public class SBBTourEstimator implements TourEstimator{
	
	private final static Logger log = Logger.getLogger(SBBTourEstimator.class);
	
	private final TourEstimator delegate;
	private final ZonesQueryCache zonesQuery;
	private final String parkingCostAttributeName;
	private final Scenario scenario;
	private final SBBCharyparNagelScoringParametersForPerson paramsForPerson;

	public SBBTourEstimator(TripEstimator tripEstimator,ParkingCostConfigGroup parkCostConfig,ZonesCollection zones,Scenario scenario) {
		this.delegate = new CumulativeTourEstimator(tripEstimator);
		this.zonesQuery = new ZonesQueryCache(zones.getZones(parkCostConfig.getZonesId()));
		this.parkingCostAttributeName = parkCostConfig.getZonesParkingCostAttributeName();
		this.scenario = scenario;
		this.paramsForPerson = new SBBCharyparNagelScoringParametersForPerson(this.scenario);
	}

	@Override
	public TourCandidate estimateTour(Person person, List<String> modes, List<DiscreteModeChoiceTrip> trips,
			List<TourCandidate> previousTours) {
		
		
		final SBBScoringParameters parameters = this.paramsForPerson.getSBBScoringParameters(person);
		//ScoringParameters parameters = scoringParametersForPerson.getScoringParameters(person);

		// First, calculate utility from trips. They're simply summed up.
		TourCandidate candidate = delegate.estimateTour(person, modes, trips, previousTours);
		double utility = candidate.getUtility();

		//Add parking costs
		utility += estimateParkingCosts(parameters, modes, trips, previousTours);
		
		// Add daily constants for trips
		Set<String> uniqueModes = new HashSet<>(modes);

		for (String uniqueMode : uniqueModes) {
			ModeUtilityParameters modeParams = parameters.getMatsimScoringParameters().modeParams.get(uniqueMode);
			utility += modeParams.dailyUtilityConstant;
			utility += parameters.getMatsimScoringParameters().marginalUtilityOfMoney * modeParams.dailyMoneyConstant;
		}

		//log.log(Level.DEBUG,"DMC person id " + person.getId().toString() + " Utility : "+  Double.toString(utility) + " Trips "+Arrays.toString(modes.toArray()) );
		
		return new DefaultTourCandidate(utility, candidate.getTripCandidates());
	}

	public double estimateParkingCosts(SBBScoringParameters parameters, List<String> modes, List<DiscreteModeChoiceTrip> trips,
			List<TourCandidate> previousTours) {
		double utility = 0.0;
		
		if(modes.get(0).compareTo("car")==0) {
			List<DiscreteModeChoiceTrip> carTrips = new ArrayList<DiscreteModeChoiceTrip>();
			for (int i = 0; i < modes.size(); i++) {
				if(modes.get(i).compareTo("car") == 0) { 
					carTrips.add(trips.get(i));
				} 
			}
			for (int i = 0; i < carTrips.size()-1; i++) {
				Zone zone = this.zonesQuery.findZone(carTrips.get(i).getDestinationActivity().getCoord().getX(),
						carTrips.get(i).getDestinationActivity().getCoord().getY());
		        if (zone != null) {
		        	Object value = zone.getAttribute(this.parkingCostAttributeName);
		        	double hourlyParkingCost = ((Number) value).doubleValue();
		        	utility += (hourlyParkingCost*((carTrips.get(i+1).getDepartureTime()  - carTrips.get(i).getDestinationActivity().getStartTime())/3600))* parameters.getMarginalUtilityOfParkingPrice();
		        }
				
			}
		}
		
		return utility;
	}
}