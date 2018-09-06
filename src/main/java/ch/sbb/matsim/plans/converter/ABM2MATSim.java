package ch.sbb.matsim.plans.converter;

import ch.sbb.matsim.plans.abm.AbmData;
import ch.sbb.matsim.plans.abm.AbmPlan;
import ch.sbb.matsim.plans.abm.AbmTour;
import ch.sbb.matsim.plans.abm.AbmTrip;
import ch.sbb.matsim.plans.discretizer.FacilityDiscretizer;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.facilities.ActivityFacility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ABM2MATSim {

    private final Scenario scenario;
    private final Population population;

    public ABM2MATSim(Scenario scenario)   {
        this.scenario = scenario;
        this.population = scenario.getPopulation();
    }

    public void processAbmData(FacilityDiscretizer discretizer, AbmData data, HashMap<String, String> abmActs2matsimActs)   {

        Network network = getCarNetwork(this.scenario.getNetwork());

        for(Person person: this.population.getPersons().values())   {
            Plan plan = person.getSelectedPlan();
            AbmPlan abmPlan = data.getPlan(person.getId());

            Id<ActivityFacility> homeFacilityId = Id.create(person.getAttributes().getAttribute("household_id").toString(), ActivityFacility.class);
            ActivityFacility homeFacility = this.scenario.getActivityFacilities().getFacilities().get(homeFacilityId);
            Link homeLink = NetworkUtils.getNearestLink(network, homeFacility.getCoord());

            double time = 0.0;

            Activity homeAct = this.population.getFactory().createActivityFromCoord(DefaultActivityTypes.home, homeFacility.getCoord());
            //homeAct.setFacilityId(homeFacilityId);
            homeAct.setLinkId(homeLink.getId());

            for(int tid: abmPlan.getTourSequence()) {
                AbmTour abmTour = abmPlan.getTour(tid);
                for(int seq: abmTour.getTripSequence()) {
                    AbmTrip abmTrip = abmTour.getTrip(seq);
                    String destAct = abmActs2matsimActs.get(abmTrip.getDestAct());

                    if(seq == 1)    {
                        // it's the first trip of the tour -> add
                        homeAct.setStartTime(time);
                        homeAct.setEndTime(abmTrip.getDepTime());
                        plan.addActivity(homeAct);
                        time = abmTrip.getDepTime();
                    }

                    // add leg
                    String mode = abmTrip.getMode();
                    // TODO: resolve bug in ABM
                    if(mode.equals("none"))
                        mode = TransportMode.ride;
                    Leg leg = this.population.getFactory().createLeg(mode);
                    leg.setDepartureTime(time);
                    leg.setTravelTime(abmTrip.getTravelTime());
                    plan.addLeg(leg);
                    time += abmTrip.getTravelTime();

                    if(!destAct.equals(DefaultActivityTypes.home))    {
                        // it's an intermediate activity
                        int destZone = abmTrip.getDestTZone();
                        Coord coord = discretizer.getRandomCoord(destZone, destAct);
                        Activity act = this.population.getFactory().createActivityFromCoord(destAct, coord);
                        Link link = NetworkUtils.getNearestLink(network, coord);
                        act.setLinkId(link.getId());
                        act.setStartTime(time);
                        time += abmTrip.getDestActDuration();
                        act.setEndTime(time);
                        plan.addActivity(act);
                    }
                    else    {
                        // it's the last trip of the tour
                        homeAct = this.population.getFactory().createActivityFromCoord(destAct, homeFacility.getCoord());
                        //homeAct.setFacilityId(homeFacilityId);
                        homeAct.setLinkId(homeLink.getId());
                    }
                }
            }

            homeAct.setStartTime(time);
            plan.addActivity(homeAct);
        }
    }

    public static Network getCarNetwork(Network network) {
        Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(carNetwork, Stream.of(TransportMode.car).collect(Collectors.toCollection(HashSet::new)));
        return carNetwork;
    }
}
