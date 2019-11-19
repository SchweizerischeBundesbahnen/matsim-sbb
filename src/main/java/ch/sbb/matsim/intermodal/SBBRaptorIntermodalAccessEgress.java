
/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;

import javax.inject.Inject;
import java.util.List;



public class SBBRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {

    private static final Logger log = Logger.getLogger(SBBRaptorIntermodalAccessEgress.class);

    private final List<SBBIntermodalModeParameterSet> intermodalModeParams;
    private final Zones zones;
    private final Network network;

    @Inject
    SBBRaptorIntermodalAccessEgress(Config config, ZonesCollection zonesCollection, Network network) {
        SBBIntermodalConfigGroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfigGroup.class);
        intermodalModeParams = intermodalConfigGroup.getModeParameterSets();
        this.zones = new ZonesQueryCache(zonesCollection.getZones(ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getZonesId()));
        this.network = network;

    }


    public SBBRaptorIntermodalAccessEgress(List<SBBIntermodalModeParameterSet> intermodalModeParams) {
        this.intermodalModeParams = intermodalModeParams;
        this.zones = null;
        this.network = null;
    }

    private boolean isIntermodalMode(String mode) {
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
                double travelTime = ((Leg) pe).getTravelTime();
                if (travelTime != Time.getUndefinedTime()) {
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

    private void setIntermodalWaitingTimesAndDetour(final List<? extends PlanElement> legs, SBBIntermodalModeParameterSet modeParams) {
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                String mode = leg.getMode();
                if (mode.equals(TransportMode.access_walk)) {

                    leg.setTravelTime(0.0);
                    leg.getRoute().setTravelTime(0.0);
                }
                if (mode.equals(TransportMode.egress_walk)) {
                    leg.setTravelTime(.0);
                    leg.getRoute().setTravelTime(0.0);
                }
                if (this.isIntermodalMode(mode)) {
                    double travelTime = leg.getTravelTime();
                    travelTime *= getDetourFactor(leg.getRoute().getStartLinkId(), mode);
                    travelTime += getAccessTime(leg.getRoute().getStartLinkId(), mode);
                    travelTime += getEgressTime(leg.getRoute().getEndLinkId(), mode);
                    leg.setTravelTime(travelTime);
                    leg.getRoute().setTravelTime(travelTime);
                }

            }
        }
    }

    private double getEgressTime(Id<Link> endLinkId, String mode) {
        SBBIntermodalModeParameterSet parameterSet = getIntermodalModeParameters(mode);
        if (parameterSet.getEgressTimeZoneId() != null) {
            Zone zone = zones.findZone(network.getLinks().get(endLinkId).getCoord());
            return zone != null ? (double) zone.getAttribute(parameterSet.getEgressTimeZoneId()) : 0.0;
        } else {
            return 0.0;
        }
    }

    private double getAccessTime(Id<Link> startLinkId, String mode) {
        SBBIntermodalModeParameterSet parameterSet = getIntermodalModeParameters(mode);
        if (parameterSet.getWaitingTime() != null) {
            return parameterSet.getWaitingTime();
        } else {
            Zone zone = zones.findZone(network.getLinks().get(startLinkId).getCoord());
            return zone != null ? (int) zone.getAttribute(parameterSet.getAccessTimeZoneId()) : 0.0;
        }

    }

    private double getDetourFactor(Id<Link> startLinkId, String mode) {
        SBBIntermodalModeParameterSet parameterSet = getIntermodalModeParameters(mode);
        if (parameterSet.getDetourFactor() != null) {
            return parameterSet.getDetourFactor();
        } else {
            Zone zone = zones.findZone(network.getLinks().get(startLinkId).getCoord());
            return zone != null ? (Double) zone.getAttribute(parameterSet.getDetourFactorZoneId()) : 1.0;
        }
    }

    private double getTotalTravelTime(final List<? extends PlanElement> legs) {
        double tTime = 0.0;
        for (PlanElement pe : legs) {
            double time = 0.0;
            if (pe instanceof Leg) {

                time = ((Leg) pe).getTravelTime();
            }

            if (!Time.isUndefinedTime(time)) {
                tTime += time;
            }

        }
        return tTime;

    }

    private double computeDisutility(final List<? extends PlanElement> legs, RaptorParameters params) {
        double disutility = 0.0;
        for (PlanElement pe : legs) {
            double time;
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                time = ((Leg) pe).getTravelTime();
                if (!Time.isUndefinedTime(time)) {
                    disutility += time * -params.getMarginalUtilityOfTravelTime_utl_s(mode);
                }
            }
        }
        return disutility;

    }


    private double computeIntermodalDisutility(final List<? extends PlanElement> legs, RaptorParameters params, SBBIntermodalModeParameterSet modeParams) {
        double utility = 0.0;
        for (PlanElement pe : legs) {
            double time;
            if (pe instanceof Leg) {
                time = ((Leg) pe).getTravelTime();
                if (!Time.isUndefinedTime(time)) {
                    utility += time * modeParams.getMUTT_perSecond();
                }
            }
        }
        utility += modeParams.getConstant();
        //return the *mostly positive* disutility, as required by the router
        return (-utility);

    }


    @Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person) {
        String intermodalTripMode = this.getIntermodalTripMode(legs);
        boolean isIntermodal = intermodalTripMode != null;
        double disutility;

        if (isIntermodal) {
            SBBIntermodalModeParameterSet modeParams = getIntermodalModeParameters(intermodalTripMode);
            this.setIntermodalWaitingTimesAndDetour(legs, modeParams);
            disutility = this.computeIntermodalDisutility(legs, params, modeParams);
        } else {
            disutility = this.computeDisutility(legs, params);
        }

        return new RIntermodalAccessEgress(legs, disutility, this.getTotalTravelTime(legs));
    }
}