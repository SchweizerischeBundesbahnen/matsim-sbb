package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.zones.Zone;
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
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.households.HouseholdsWriterV10;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;

import java.io.BufferedWriter;
import java.util.HashSet;
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
            SynpopConstants.AGE_CAT,
            SynpopConstants.CURRENT_EDU,
            SynpopConstants.CURRENT_JOB_RANK,
            SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT,
            SynpopConstants.PEDESTRIAN_SPEED,
            Variables.SUBPOPULATION,
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
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, outputDirPrefix+"mobi_zones_all_attributes.csv");
        stripZonalAttributes(zones);
        ZoneGeoPackageWriter.writeGeoPackage(zones, outputDirPrefix+"mobi_zones.gpkg");
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, outputDirPrefix+"mobi_zones.csv");


        writePopulationAttributes(scenario.getPopulation(),outputDirPrefix+"population_attributes.csv.zst");
        /*
        produce a plans file with only those attributes needed in MATSim
         */
        Population reducedPopulation = reducePopulation();
        new PopulationWriter(reducedPopulation).write(outputDirPrefix+"plans.xml.zst");
    }

    private void stripZonalAttributes(Zones zones) {
        zones.getZones().forEach(zone -> {
            Set<String> attributes = new HashSet<>(zone.getAttributes().getAsMap().keySet());
            for (var attribute : attributes){
                if (!ZonalAttributes.attributesForExport.contains(attribute)){
                    zone.getAttributes().removeAttribute(attribute);
                }
            }
        });
    }

    private void writePopulationAttributes(Population population, String outputFile) {
        LOG.info("Writing population attributes to "+outputFile);
        List<String> allAttributes = population.getPersons().values().parallelStream()
                .flatMap(person -> person.getAttributes().getAsMap().keySet().stream())
                .distinct()
                .filter(attr -> !attr.equals(SynpopConstants.ZONE))  // avoiding having 2 zone_id variables
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
        Population newPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        scenario.getPopulation().getPersons().values().stream()
                .filter(person -> !(person.getSelectedPlan() == null))
                .forEach(person -> {
                    var copiedPerson = newPopulation.getFactory().createPerson(person.getId());
                    AttributesUtils.copyAttributesFromTo( person,copiedPerson );
                    Plan plan = PopulationUtils.getFactory().createPlan();
                    copiedPerson.addPlan(plan);
                    PopulationUtils.copyFromTo(person.getSelectedPlan(),plan);
                    newPopulation.addPerson(copiedPerson);
                });


        newPopulation.getPersons().values().forEach(
                person->{
                    Zone zone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
                    if (zone != null) {
                        person.getAttributes().putAttribute(SynpopConstants.RESIDENCE_ZONE_ID, zone.getId().toString());
                        person.getAttributes().putAttribute(SynpopConstants.RESIDENCE_AMR_ID, zone.getAttributes().getAttribute(ZonalAttributes.AMR_ID).toString());
                    }
                    Set<String> attributes = new HashSet<>(person.getAttributes().getAsMap().keySet());
                    Set<String> attributesToKeep = findAttributesToKeep(attributes);
                    for (var attribute : attributes){
                        if (!attributesToKeep.contains(attribute)){
                            person.getAttributes().removeAttribute(attribute);
                        }
                    }
                    for (PlanElement planElement : person.getSelectedPlan().getPlanElements()){
                        if (planElement instanceof  Leg leg){
                            if (leg.getMode().equals(SBBModes.WALK_FOR_ANALYSIS)){
                                leg.setMode(SBBModes.WALK_MAIN_MAINMODE);
                            }
                            Set<String> legAttributes = new HashSet<>(leg.getAttributes().getAsMap().keySet());
                            for (var legAtt: legAttributes){
                                leg.getAttributes().removeAttribute(legAtt);
                            }
                        }
                        if (planElement instanceof Activity activity){
                            if (activity.getType().equals(SBBActivities.education_continued))
                            {activity.setType(SBBActivities.education);
                            }
                            Set<String> actAttributes = new HashSet<>(activity.getAttributes().getAsMap().keySet());
                            for (var activityAtt: actAttributes){
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
                    int loeCat = AttributeUtils.getAsInteger(person,SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT,-1);
                    if (loeCat != -1){
                        person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT, SynpopConstants.LEVEL_OF_EMPLOYMENT_CAT_CATEGORIES.get(loeCat));
                    }
                    int eduCat = AttributeUtils.getAsInteger(person,SynpopConstants.CURRENT_EDU,-1);
                    if (eduCat != -1){
                        person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, SynpopConstants.CURRENT_EDU_CATEGORIES.get(eduCat));

                    }


                }
        );
        return newPopulation;
    }

    private Set<String> findAttributesToKeep(Set<String> attributes) {
        return attributes.stream().filter(attribute -> attributesToKeep.contains(attribute) || attribute.contains("2pt"))
                .collect(Collectors.toSet());
    }
}
