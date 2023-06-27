
/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder.Direction;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.utils.misc.OptionalTime;

import jakarta.inject.Inject;

import java.util.List;

public class SBBRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {

	private static final Logger log = LogManager.getLogger(SBBRaptorIntermodalAccessEgress.class);

	private final List<SBBIntermodalModeParameterSet> intermodalModeParams;
	private final Zones zones;
	private final Network network;
	private final PlanCalcScoreConfigGroup scoreConfigGroup;

	@Inject
	SBBRaptorIntermodalAccessEgress(Config config, ZonesCollection zonesCollection, Network network) {
		SBBIntermodalConfiggroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfiggroup.class);
		scoreConfigGroup = config.planCalcScore();
		intermodalModeParams = intermodalConfigGroup.getModeParameterSets();
		Id<Zones> zonesId = intermodalConfigGroup.getZonesId();
		this.zones = zonesId != null ? new ZonesQueryCache(zonesCollection.getZones(intermodalConfigGroup.getZonesId())) : null;
		this.network = network;
	}

	private boolean isIntermodalMode(String mode) {
		if (mode.equals(SBBModes.ACCESS_EGRESS_WALK)) {
			return false;
		}
		for (SBBIntermodalModeParameterSet modeParams : this.intermodalModeParams) {
			if (mode.equals(modeParams.getMode())) {
				return true;
			}
		}
		return false;
	}

	private String getIntermodalTripMode(final List<? extends PlanElement> legs) {
		for (PlanElement pe : legs) {
			if (pe instanceof Leg) {
				String mode = ((Leg) pe).getMode();
				OptionalTime travelTime = ((Leg) pe).getTravelTime();
				if (travelTime.isDefined()) {
					if (this.isIntermodalMode(mode)) {
						return mode;
					}
				}
			}
		}
		return null;
	}

	private SBBIntermodalModeParameterSet getIntermodalModeParameters(String mode) {
		for (SBBIntermodalModeParameterSet modeParams : this.intermodalModeParams) {
			if (mode.equals(modeParams.getMode())) {
				return modeParams;
			}
		}
		return null;
	}

	private void setIntermodalDetour(final List<? extends PlanElement> legs) {
		for (PlanElement pe : legs) {
			if (pe instanceof Leg) {
				Leg leg = (Leg) pe;
				String mode = leg.getMode();
				if (this.isIntermodalMode(mode)) {
					double travelTime = leg.getTravelTime().seconds();
					travelTime *= getDetourFactor(leg.getRoute().getStartLinkId(), mode);
					leg.setTravelTime(travelTime);
					leg.getRoute().setTravelTime(travelTime);
				}
			}
		}

	}

	private double getDetourFactor(Id<Link> startLinkId, String mode) {
		SBBIntermodalModeParameterSet parameterSet = getIntermodalModeParameters(mode);
		if (parameterSet.getDetourFactorZoneId() != null) {
			return ((Number) network.getLinks().get(startLinkId).getAttributes().getAttribute(parameterSet.getDetourFactorZoneId())).doubleValue();
		}
		return 1.0;
	}

	private double getTotalTravelTime(final List<? extends PlanElement> legs) {
		double tTime = 0.0;
		for (PlanElement pe : legs) {
			OptionalTime time = OptionalTime.undefined();
			if (pe instanceof Leg) {
				time = ((Leg) pe).getTravelTime();
			}
			if (time.isDefined()) {
				tTime += time.seconds();
			}

		}
		return tTime;

	}

	private double computeDisutility(final List<? extends PlanElement> legs, RaptorParameters params) {
		double disutility = 0.0;
		for (PlanElement pe : legs) {
			if (pe instanceof Leg) {
				String mode = ((Leg) pe).getMode();
				OptionalTime time = ((Leg) pe).getTravelTime();
				if (time.isDefined()) {
					disutility += time.seconds() * -params.getMarginalUtilityOfTravelTime_utl_s(mode);
				}
			}
		}
		return disutility;

	}

	private double computeIntermodalDisutility(final List<? extends PlanElement> legs, RaptorParameters params, PlanCalcScoreConfigGroup.ModeParams modeParams) {
		double utility = 0.0;
		for (PlanElement pe : legs) {
			if (pe instanceof Leg) {
				OptionalTime time = ((Leg) pe).getTravelTime();
				if (time.isDefined()) {
					utility += time.seconds() * (modeParams.getMarginalUtilityOfTraveling() / 3600.0);
				}
			}
		}
		utility += modeParams.getConstant();
		//return the *mostly positive* disutility, as required by the router
		return (-utility);

	}

	@Override
	public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person, Direction direction) {
		String intermodalTripMode = this.getIntermodalTripMode(legs);
		boolean isIntermodal = intermodalTripMode != null;
		double disutility;

		if (isIntermodal) {
			PlanCalcScoreConfigGroup.ModeParams modeParams = this.scoreConfigGroup.getModes().get(intermodalTripMode);
			this.setIntermodalDetour(legs);
			//better move this directly into routing module?
			disutility = this.computeIntermodalDisutility(legs, params, modeParams);
		} else {
			disutility = this.computeDisutility(legs, params);
		}

		return new RIntermodalAccessEgress(legs, disutility, this.getTotalTravelTime(legs), direction);
	}
}