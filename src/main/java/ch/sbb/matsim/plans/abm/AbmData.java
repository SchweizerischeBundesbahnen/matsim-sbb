package ch.sbb.matsim.plans.abm;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AbmData {

        private Map<Id<Person>, AbmPlan> plantable = new HashMap<>();

        public AbmData() {
        }

        public void addPlanIfNotExists(Id<Person> pid)  {
            this.plantable.putIfAbsent(pid, new AbmPlan());
        }

        public AbmPlan getPlan(Id<Person> pid) {
            return this.plantable.get(pid);
        }

        public Set<Id<Person>> getPersonIds()  {
            return this.plantable.keySet();
        }

        public void addTrip(Id<Person> pid, int tid, int seq, int oTZone, int dTZone, String oAct, String dAct, String mode,
                            double deptime, double arrtime, double dActDuration)   {
            addPlanIfNotExists(pid);
            AbmPlan plan = getPlan(pid);
            plan.addTourIfNotExists(tid);
            AbmTour tour = plan.getTour(tid);
            tour.addTrip(seq, oTZone, dTZone, oAct, dAct, mode, deptime, arrtime, dActDuration);
        }
}
