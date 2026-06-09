package ch.sbb.matsim.plans.synpop.loading.csv;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.households.Household;
import org.matsim.households.Households;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class HouseholdsCsvReader {

    public static void readHouseholdCsvFile(String filePath, Households households) {
        File file = new File(filePath);
        readHouseholdCsvFile(file, households);
    }

    public static void readHouseholdCsvFile(File file, Households households) {
        try (
                Reader in = new FileReader(file);
                CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.builder()
                        .setDelimiter(';')
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .get())
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
            throw new RuntimeException(e);
        }
    }
}