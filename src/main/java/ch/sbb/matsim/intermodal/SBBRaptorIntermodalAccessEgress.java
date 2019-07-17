
/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.misc.Time;

import java.util.List;


//https://github.com/google/guice/wiki/AssistedInject

public class SBBRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {

    private static final Logger log = Logger.getLogger(SBBRaptorIntermodalAccessEgress.class);

    private final List<SBBIntermodalModeParameterSet> intermodalModeParams;

    public SBBRaptorIntermodalAccessEgress(List<SBBIntermodalModeParameterSet> intermodalModeParams) {
        this.intermodalModeParams = intermodalModeParams;
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
                String mode = ((Leg) pe).getMode();
                if (mode.equals(TransportMode.access_walk)) {
                    ((Leg) pe).setTravelTime(modeParams.getWaitingTime());
                    ((Leg) pe).getRoute().setTravelTime(modeParams.getWaitingTime());
                }
                if (mode.equals(TransportMode.egress_walk)) {
                    ((Leg) pe).setTravelTime(3.0 * 60);
                    ((Leg) pe).getRoute().setTravelTime(3.0 * 60);
                }

                if (this.isIntermodalMode(mode)) {
                    double travelTime = ((Leg) pe).getTravelTime();
                    travelTime *= modeParams.getDetourFactor();
                    ((Leg) pe).setTravelTime(travelTime);
                    ((Leg) pe).getRoute().setTravelTime(travelTime);
                }

            }
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
        double disutility = 0.0;
        for (PlanElement pe : legs) {
            double time;
            if (pe instanceof Leg) {
                time = ((Leg) pe).getTravelTime();
                if (!Time.isUndefinedTime(time)) {
                    disutility += time * modeParams.getMUTT();
                }
            }
        }
        return modeParams.getConstant() + disutility;

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