package ch.sbb.matsim.plans.abm;

import java.util.*;

public class AbmPlan {

    private Map<Integer, AbmTour> tourtable = new HashMap<>();
    private List<Integer> tourSequence = new ArrayList<>();

    public AbmPlan() {
    }

    public void addTourIfNotExists(int tid)  {
        if(!this.tourSequence.contains(tid))  {
            this.tourtable.put(tid, new AbmTour());
            this.tourSequence.add(tid);
        }
    }

    public List<Integer> getTourSequence()  {
        return this.tourSequence;
    }

    public AbmTour getTour(int tid) {
        return this.tourtable.get(tid);
    }

    public Collection<AbmTour> getAllTours()    {
        return this.tourtable.values();
    }
}
