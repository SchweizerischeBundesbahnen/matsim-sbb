package ch.sbb.matsim.plans.synpop.loading.csv;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsWriterV10;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class HouseholdsCsvReader {

    public static void main(String[] args) {
        String csvHouseholds = "/Volumes/mobi/40_Projekte/20241104_MOBi_5.1/synpop/cb_commuters/v10/households.csv"; // Adjust path as needed
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        var households = scenario.getHouseholds();
        double time = System.nanoTime();
        readHouseholdCsvFile(csvHouseholds, households);
        System.out.println(households.getHouseholds().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
        new HouseholdsWriterV10(households).writeFile("/Users/joschka.bischoff/tmp/synpop2022/cb_households.xml.gz");
    }

    public static void readHouseholdCsvFile(String filePath, Households households) {
        File file = new File(filePath);
        readHouseholdCsvFile(file, households);
    }

    public static void readHouseholdCsvFile(File file, Households households) {
        try (
                Reader in = new FileReader(file);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader().withTrim())
        ) {
            for (CSVRecord record : parser) {
                Id<Household> householdId = Id.create(Long.parseLong(record.get(SynpopConstants.HOUSEHOLD_ID)), Household.class);
                var household = households.getFactory().createHousehold(householdId);
                households.getHouseholds().put(householdId, household);

                double xCoord = Double.parseDouble(record.get(SynpopConstants.XCOORD));
                double yCoord = Double.parseDouble(record.get(SynpopConstants.YCOORD));
                Coord coord = new Coord(xCoord, yCoord);

                household.getAttributes().putAttribute(SynpopConstants.COORD, coord);
                household.getAttributes().putAttribute(SynpopConstants.ZONE_ID, record.get(SynpopConstants.ZONE_ID)); // Assuming ZONE_ID is read as String directly
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}