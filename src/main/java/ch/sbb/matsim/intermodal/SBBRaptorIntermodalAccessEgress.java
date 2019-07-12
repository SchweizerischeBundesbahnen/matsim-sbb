
/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.misc.Time;

import javax.inject.Inject;
import java.util.List;


//https://github.com/google/guice/wiki/AssistedInject

public class SBBRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {

    private static final Logger log = Logger.getLogger(SBBRaptorIntermodalAccessEgress.class);

    private double constant;
    private double mutt;
    private double waiting;
    private double detourFactor;

    public SBBRaptorIntermodalAccessEgress(double constant, double mutt, double waiting) {
        this.constant = constant;
        this.mutt = mutt;
        this.waiting = waiting;
        this.detourFactor = 1.3;
    }

    private boolean isIntermodalMode(String mode) {
        return mode.equals("ride_feeder") || mode.equals("car");
    }

    private boolean isIntermodalTrip(final List<? extends PlanElement> legs) {

        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                double travelTime = ((Leg) pe).getTravelTime();
                if (travelTime != Time.getUndefinedTime()) {
                    if (this.isIntermodalMode(mode)) {
                        return true;
                    }
                }
            }
        }
        return false;

    }

    private void setIntermodalWaitingTimesAndDetour(final List<? extends PlanElement> legs) {
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                if (mode.equals(TransportMode.access_walk)) {
                    ((Leg) pe).setTravelTime(this.waiting);
                    ((Leg) pe).getRoute().setTravelTime(this.waiting);
                }
                if (mode.equals(TransportMode.egress_walk)) {
                    ((Leg) pe).setTravelTime(3.0 * 60);
                    ((Leg) pe).getRoute().setTravelTime(3.0 * 60);
                }

                if (this.isIntermodalMode(mode)) {
                    double travelTime = ((Leg) pe).getTravelTime();
                    travelTime *= this.detourFactor;
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


    private double computeIntermodalDisutility(final List<? extends PlanElement> legs, RaptorParameters params) {
        double disutility = 0.0;
        for (PlanElement pe : legs) {
            double time;
            if (pe instanceof Leg) {
                time = ((Leg) pe).getTravelTime();

                if (!Time.isUndefinedTime(time)) {

                    disutility += time * this.mutt;

                }
            }
        }
        return this.constant + disutility;

    }


    @Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person) {

        boolean isIntermodal = this.isIntermodalTrip(legs);
        double disutility;

        if (isIntermodal) {
            this.setIntermodalWaitingTimesAndDetour(legs);
            disutility = this.computeIntermodalDisutility(legs, params);
        } else {
            disutility = this.computeDisutility(legs, params);
        }


        return new RIntermodalAccessEgress(legs, disutility, this.getTotalTravelTime(legs));
    }
}