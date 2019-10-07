package ch.ethz.matsim.discrete_mode_choice.estimators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

public class SBBTourEstimator implements TourEstimator{
	
	private final TourEstimator delegate;
	private final ScoringParametersForPerson scoringParametersForPerson;

	public SBBTourEstimator(TripEstimator tripEstimator,
			ScoringParametersForPerson scoringParametersForPerson) {
		this.delegate = new CumulativeTourEstimator(tripEstimator);
		this.scoringParametersForPerson = scoringParametersForPerson;
	}

	@Override
	public TourCandidate estimateTour(Person person, List<String> modes, List<DiscreteModeChoiceTrip> trips,
			List<TourCandidate> previousTours) {
		ScoringParameters parameters = scoringParametersForPerson.getScoringParameters(person);

		// First, calculate utility from trips. They're simply summed up.
		TourCandidate candidate = delegate.estimateTour(person, modes, trips, previousTours);
		double utility = candidate.getUtility();

		//Add parking costs
		utility += estimateParkingCosts(person, modes, trips, previousTours);
		
		// Add daily constants for trips
		Set<String> uniqueModes = new HashSet<>(modes);

		for (String uniqueMode : uniqueModes) {
			ModeUtilityParameters modeParams = parameters.modeParams.get(uniqueMode);
			utility += modeParams.dailyUtilityConstant;
			utility += parameters.marginalUtilityOfMoney * modeParams.dailyMoneyConstant;
		}

		return new DefaultTourCandidate(utility, candidate.getTripCandidates());
	}

	public double estimateParkingCosts(Person person, List<String> modes, List<DiscreteModeChoiceTrip> trips,
			List<TourCandidate> previousTours) {
		
		double utility = 0.0;

		if(modes.get(0).compareTo("car")==0) {
			List<DiscreteModeChoiceTrip> carTrips = new ArrayList();
			for (int i = 0; i < modes.size(); i++) {
				if(modes.get(i).compareTo("car") == 0) { 
					carTrips.add(trips.get(i));
				} 
			}
			for (int i = 0; i < carTrips.size()-1; i++) {
				utility += (carTrips.get(i+1).getDepartureTime()  - carTrips.get(i).getDestinationActivity().getStartTime());
			}
		}
		
		return 0;
	}
}