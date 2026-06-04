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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.households.HouseholdsWriterV10;

import java.io.BufferedWriter;
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

    private static final Logger LOG = LogManager.getLogger(MOBiPlansOutputWriter.class);

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
        new PopulationWriter(scenario.getPopulation()).write(outputDirPrefix+"plans_complete.xml.zst");

        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputDirPrefix+"facilities.xml.zst");
        new HouseholdsWriterV10(scenario.getHouseholds()).writeFile(outputDirPrefix+"households.xml.zst");
        ZoneGeoPackageWriter.writeGeoPackage(zones, outputDirPrefix+"mobi_zones.gpkg");
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, outputDirPrefix+"mobi_zones.csv");
        writePopulationAttributes(scenario.getPopulation(),outputDirPrefix+"population_attributes.csv.gz");
        /*
        produce a plans file with only those attributes needed in MATSim
         */
        Population reducedPopulation = reducePopulation();
        new PopulationWriter(reducedPopulation).write(outputDirPrefix+"plans.xml.zst");
    }

    private void writePopulationAttributes(Population population, String outputFile) {
        LOG.info("Writing population attributes to "+outputFile);
        List<String> allAttributes = population.getPersons().values().parallelStream()
                .flatMap(person -> person.getAttributes().getAsMap().keySet().stream())
                .distinct()
                .sorted()
                .toList();

        try (BufferedWriter writer = IOUtils.getBufferedWriter(outputFile)) {
            writer.write("person_id");
            String delimiter = ";";
            for (String attr : allAttributes) {
                writer.write(delimiter);
                writer.write(attr);
            }
            writer.write("\n");

            StringBuilder sb = new StringBuilder(512);
            for (var person : population.getPersons().values()) {
                sb.setLength(0);
                sb.append(person.getId().toString());
                for (String attr : allAttributes) {
                    sb.append(delimiter);
                    Object val = person.getAttributes().getAttribute(attr);
                    if (val != null) {
                        if (val instanceof Identifiable<?> identifiable) {
                            val = identifiable.getId();
                        }
                        String strVal = val.toString();
                        if (strVal.contains(delimiter)) {
                            sb.append("\"").append(strVal).append("\"");
                        } else {
                            sb.append(strVal);
                        }
                    }
                }
                sb.append("\n");
                writer.write(sb.toString());
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error writing population attributes to " + outputFile, e);
        }
        LOG.info("...done.");
    }

    private Population reducePopulation() {
        LOG.info("Reducing Population for MATSim export");
        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        scenario.getPopulation().getPersons().values().stream()
                .filter(person -> !(person.getSelectedPlan() == null))
                .forEach(person -> population.addPerson(person));



        population.getPersons().values().forEach(
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
        return population;
    }

    private Set<String> findAttributesToKeep(Set<String> attributes) {
        return attributes.stream().filter(attribute -> attributesToKeep.contains(attribute) || attributes.contains("2pt"))
                .collect(Collectors.toSet());
    }
}
