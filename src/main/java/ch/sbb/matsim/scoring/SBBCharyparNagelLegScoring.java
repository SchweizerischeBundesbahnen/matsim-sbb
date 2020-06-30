/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.variables.SBBModes;
import java.util.Set;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scoring.functions.ModeUtilityParameters;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.pt.PtConstants;

/**
 * THIS IS A COPY of the default CharyparNagelLegScoring with the following modifications: - replace hard-coded <code>SBBModes.PT</code> with the set of pt modes configured in the transit config
 * group. - don't apply lineSwitch-utility, as this will be handled in {@link SBBTransferScoring}.
 *
 * @author mrieser / SBB
 * 		<p>
 * 		<p>
 * 		This is a re-implementation of the original CharyparNagel function, based on a modular approach.
 * @author rashid_waraich
 */
public class SBBCharyparNagelLegScoring implements org.matsim.core.scoring.SumScoringFunction.LegScoring, org.matsim.core.scoring.SumScoringFunction.ArbitraryEventScoring {

	protected double score;

	/** The parameters used for scoring */
	protected final ScoringParameters params;
	protected Network network;
	private boolean nextStartPtLegIsFirstOfTrip = true;
	private boolean currentLegIsPtLeg = false;
	private OptionalTime lastActivityEndTime = OptionalTime.undefined();
	private final Set<String> ptModes;

	public SBBCharyparNagelLegScoring(final ScoringParameters params, Network network, Set<String> ptModes) {
		this.params = params;
		this.network = network;
		this.nextStartPtLegIsFirstOfTrip = true;
		this.currentLegIsPtLeg = false;
		this.ptModes = ptModes;
	}

	@Override
	public void finish() {

	}

	@Override
	public double getScore() {
		return this.score;
	}

	private static int ccc=0 ;
	
	protected double calcLegScore(final double departureTime, final double arrivalTime, final Leg leg) {
		double tmpScore = 0.0;
		double travelTime = arrivalTime - departureTime; // travel time in seconds
		ModeUtilityParameters modeParams = this.params.modeParams.get(leg.getMode());
		if (modeParams == null) {
            if (leg.getMode().equals(SBBModes.PT_FALLBACK_MODE) || leg.getMode().equals(SBBModes.ACCESS_EGRESS_WALK)) {
				modeParams = this.params.modeParams.get(SBBModes.WALK_FOR_ANALYSIS);
			} else {
				//				modeParams = this.params.modeParams.get(TransportMode.other);
				throw new RuntimeException("just encountered mode for which no scoring parameters are defined: " + leg.getMode());
			}
		}
		tmpScore += travelTime * modeParams.marginalUtilityOfTraveling_s;
		if (modeParams.marginalUtilityOfDistance_m != 0.0
				|| modeParams.monetaryDistanceCostRate != 0.0) {
			Route route = leg.getRoute();
			double dist = route.getDistance(); // distance in meters
			if ( Double.isNaN(dist) ) {
				if ( ccc<10 ) {
					ccc++ ;
					Logger.getLogger(this.getClass()).warn("distance is NaN. Will make score of this plan NaN. Possible reason: Simulation does not report " +
							"a distance for this trip. Possible reason for that: mode is teleported and router does not " +
							"write distance into plan.  Needs to be fixed or these plans will die out.") ;
					if ( ccc==10 ) {
						Logger.getLogger(this.getClass()).warn(Gbl.FUTURE_SUPPRESSED) ;
					}
				}
			}
			tmpScore += modeParams.marginalUtilityOfDistance_m * dist;
			tmpScore += modeParams.monetaryDistanceCostRate * this.params.marginalUtilityOfMoney * dist;
		}
		tmpScore += modeParams.constant;
		// (yyyy once we have multiple legs without "real" activities in between, this will produce wrong results.  kai, dec'12)
		// (yy NOTE: the constant is added for _every_ pt leg.  This is not how such models are estimated.  kai, nov'12)
		return tmpScore;
	}
	
	@Override
	public void handleEvent(Event event) {
		if ( event instanceof ActivityEndEvent ) {
			// When there is a "real" activity, flags are reset:
			if (!PtConstants.TRANSIT_ACTIVITY_TYPE.equals(((ActivityEndEvent) event).getActType())) {
				this.nextStartPtLegIsFirstOfTrip = true;
			}
			this.lastActivityEndTime = OptionalTime.defined(event.getTime());
		}

		if (event instanceof PersonEntersVehicleEvent && currentLegIsPtLeg ) {
			// add score of waiting, _minus_ score of travelling (since it is added in the legscoring above):
			this.score +=
					(event.getTime() - this.lastActivityEndTime.seconds()) * (this.params.marginalUtilityOfWaitingPt_s - this.params.modeParams.get(SBBModes.PT).marginalUtilityOfTraveling_s);
		}

		if (event instanceof PersonDepartureEvent ) {
			String mode = ((PersonDepartureEvent) event).getLegMode();
			this.currentLegIsPtLeg = this.ptModes.contains(mode);
			if ( currentLegIsPtLeg ) {
				if ( !this.nextStartPtLegIsFirstOfTrip ) {
					this.score -= params.modeParams.get(mode).constant;
					// (yyyy deducting this again, since is it wrongly added above.  should be consolidated; this is so the code
					// modification is minimally invasive.  kai, dec'12)
				}
				this.nextStartPtLegIsFirstOfTrip = false ;
			}
		}
	}

	@Override
	public void handleLeg(Leg leg) {
		double legScore = calcLegScore(leg.getDepartureTime().seconds(), leg.getDepartureTime().seconds() + leg.getTravelTime().seconds(), leg);
		this.score += legScore;
	}

}
