package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.steps.ModelStep;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.ZoneGeoPackageWriter;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.households.HouseholdsWriterV10;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MOBiPlansOutputWriter implements ModelStep {
    @Inject
    Scenario scenario;
    @Inject
    MOBiPlansConfig config;
    @Inject
    Zones zones;

    List<String> attributesToKeep = List.of(
            SynpopConstants.ANALYSIS_SUBPOPULATION,
            SynpopConstants.CAR_AVAILABLE,
            SynpopConstants.HAS_DRIVING_LICENSE,
            SynpopConstants.HAS_EBIKE45,
            Variables.PT_SUBSCRIPTION,
            SynpopConstants.RESIDENCE_ZONE_ID,
            SynpopConstants.RESIDENCE_AMR_ID,
            SynpopConstants.CURRENT_EDU,
            SynpopConstants.CURRENT_JOB_RANK,
            SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT,
            SynpopConstants.PEDESTRIAN_SPEED,
            Variables.SLICE);
    List<String> activityAttributesToKeep =  List.of(Variables.MOBiTripAttributes.PURPOSE,Variables.MOBiTripAttributes.DIRECTION, Variables.MOBiTripAttributes.DNWV_FACTOR);


    @Override
    public String getName() {
        return MOBiPlansRunner.WRITE_OUTPUTS;
    }

    @Override
    public void run() {
        String outputDirPrefix = config.data().outputBaseDir()+"/"+config.models().run_id()+".";
        new PopulationWriter(scenario.getPopulation()).write(outputDirPrefix+"plans_complete.xml.gz");

        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputDirPrefix+"facilities.xml.gz");
        new HouseholdsWriterV10(scenario.getHouseholds()).writeFile(outputDirPrefix+"households.xml.gz");
        ZoneGeoPackageWriter.writeGeoPackage(zones, outputDirPrefix+"mobi_zones.gpkg");
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, outputDirPrefix+"mobi_zones.csv");

        /*
        produce a plans file with only those attributes needed in MATSim
         */
        reducePopulation();
        new PopulationWriter(scenario.getPopulation()).write(outputDirPrefix+"plans.xml.gz");
    }

    private void reducePopulation() {
        var personsToRemove = scenario.getPopulation().getPersons().values().stream()
                .filter(person -> person.getSelectedPlan() == null)
                .map(person -> person.getId())
                .collect(Collectors.toSet());
        personsToRemove.forEach(scenario.getPopulation()::removePerson);
        scenario.getPopulation().getPersons().values().forEach(
                person->{
                    Set<String> attributes = person.getAttributes().getAsMap().keySet();
                    Set<String> attributesToKeep = findAttributesToKeep(attributes);
                    for (var attribute : attributes){
                        if (!attributesToKeep.contains(attribute)){
                            person.getAttributes().removeAttribute(attribute);
                        }
                    }
                    for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){
                        if (planElement instanceof  Leg leg){
                            for (var legAtt: leg.getAttributes().getAsMap().keySet()){
                                leg.getAttributes().removeAttribute(legAtt);
                            }
                        }
                        if (planElement instanceof Activity activity){
                            for (var activityAtt: activity.getAttributes().getAsMap().keySet()){
                                if (!activityAttributesToKeep.contains(activityAtt)) {
                                    activity.getAttributes().removeAttribute(activityAtt);
                                }
                            }
                        }
                    }
                    int ageCat = AttributeUtils.getAsInteger(person,SynpopConstants.AGE_CAT,-1);
                    if (ageCat != -1){
                        person.getAttributes().putAttribute(SynpopConstants.AGE_CAT, SynpopConstants.AGE_CATEGORIES.get(ageCat));
                    }
                }
        );
    }

    private Set<String> findAttributesToKeep(Set<String> attributes) {
        return attributes.stream().filter(attribute -> attributesToKeep.contains(attribute) || attributes.contains("2pt"))
                .collect(Collectors.toSet());
    }
}
