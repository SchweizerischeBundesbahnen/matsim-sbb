package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.ModelStep;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.ZoneGeoPackageWriter;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.households.HouseholdsWriterV10;

@Singleton
public class MOBiPlansOutputWriter implements ModelStep {
    @Inject
    Scenario scenario;
    @Inject
    MOBiPlansConfig config;
    @Inject
    Zones zones;

    @Override
    public String getName() {
        return MOBiPlansRunner.WRITE_OUTPUTS;
    }

    @Override
    public void run() {
        String outputDirPrefix = config.data().outputBaseDir()+"/"+config.models().run_id()+".";
        new PopulationWriter(scenario.getPopulation()).write(outputDirPrefix+"plans.xml.gz");
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputDirPrefix+"facilities.xml.gz");
        //new HouseholdsWriterV10(scenario.getHouseholds()).writeFile(outputDirPrefix+"households.xml.gz");
        ZoneGeoPackageWriter.writeGeoPackage(zones, outputDirPrefix+"mobi_zones.gpkg");
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, outputDirPrefix+"mobi_zones.csv");
    }
}
