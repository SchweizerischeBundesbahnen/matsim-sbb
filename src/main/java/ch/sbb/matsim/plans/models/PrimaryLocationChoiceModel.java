package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;

import java.util.Set;
import java.util.stream.Collectors;

public class PrimaryLocationChoiceModel {

    private final Scenario scenario;
    private final Set<DecisionStrata> longTermStrataWork = Set.of(
            DecisionStrata.PRIMLOC_EMPL_NOABO_CAR,
            DecisionStrata.PRIMLOC_EMPL_GA_CAR,
            DecisionStrata.PRIMLOC_EMPL_VA_CAR,
            DecisionStrata.PRIMLOC_EMPL_NOABO_NOCAR,
            DecisionStrata.PRIMLOC_EMPL_GA_NOCAR,
            DecisionStrata.PRIMLOC_EMPL_VA_NOCAR
    );
    private final Zones zones;

    public PrimaryLocationChoiceModel(Scenario scenario) {
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
    }

    public void run() {
        StrataPredicateFactory strataPredicateFactory = new StrataPredicateFactory();
        //1 sort persons by strata
        for (DecisionStrata strata : longTermStrataWork) {
            var strataPredicate = strataPredicateFactory.getPredicate(strata);
            //2 sort persons by zone
            var personsPerZone = scenario.getPopulation().getPersons().values().stream()
                    .filter(strataPredicate)
                    .collect(Collectors.groupingBy(
                            person -> Id.create(person.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString(), Zone.class))
                    );

            //3 for each strata and zone, calculate the primary location choice probabilities
            for (var entry : personsPerZone.entrySet()) {
                Id<Zone> zoneId = entry.getKey();
                var personsInZone = entry.getValue();
                Zone homeZone = zones.getZone(zoneId);
                for (Zone destinationZone : zones.getZones()) {
                    //calc zone-to-zone probabilities based on demand strata function
                }


                //4 for each person in this zone, select a primary location based on the probabilities
                for (var person : personsInZone) {
                    // Example: var selectedLocation = selectPrimaryLocation(probabilities, person);

                    //5 assign the selected primary location to the person as a facility
                }
            }
            //5 assign the selected primary location to the person as a facility
        }

    }


}
