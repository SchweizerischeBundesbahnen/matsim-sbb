package ch.sbb.matsim.plans.abm;

import org.apache.log4j.Logger;

import java.util.*;

public class AbmTour {

    private static final Logger log = Logger.getLogger(AbmTour.class);

    private Map<Integer, AbmTrip> triptable = new HashMap<>();
    private List<Integer> tripSequence = new ArrayList<>();

    public AbmTour() {
    }

    public void addTrip(int seq, int oTZone, int dTZone, String oAct, String dAct, String mode,
                        double deptime, double arrtime, double dActEndTime)  {
        if(this.tripSequence.contains(seq))  {
            log.error("Trip sequence must be unique!");
        }
        else    {
            AbmTrip trip = new AbmTrip(oTZone, dTZone, oAct, dAct, mode, deptime, arrtime, dActEndTime);
            this.triptable.put(seq, trip);
            this.tripSequence.add(seq);
        }
    }

    public List<Integer> getTripSequence()  {
        return this.tripSequence;
    }

    public AbmTrip getTrip(int seq) {
        return this.triptable.get(seq);
    }

    public Collection<AbmTrip> getAllTrips()  {
        return this.triptable.values();
    }
}
