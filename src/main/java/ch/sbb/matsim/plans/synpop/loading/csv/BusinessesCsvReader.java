package ch.sbb.matsim.plans.synpop.loading.csv;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class BusinessesCsvReader {


    public static void readBusinessesCsvFile(String filePath, ActivityFacilities facilities) {
        File file = new File(filePath);
        readBusinessesCsvFile(file, facilities);
    }

    public static void readBusinessesCsvFile(File file, ActivityFacilities facilities) {

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
                // BUSINESS_ID can be a Long or String based on your Arrow getVectorValue.
                // Assuming it's a value that can be represented as a String for Id.create.
                Id<ActivityFacility> facilityId = Id.create(record.get(SynpopConstants.BUSINESS_ID), ActivityFacility.class);

                double xCoord = Double.parseDouble(record.get(SynpopConstants.XCOORD));
                double yCoord = Double.parseDouble(record.get(SynpopConstants.YCOORD));
                Coord coord = new Coord(xCoord, yCoord);

                var facility = facilities.getFactory().createActivityFacility(facilityId, coord);

                // For other attributes, we'll read them as strings and let MATSim's attribute system handle them.
                // If specific attributes need numerical parsing (e.g., FTE_ENDO, JOBS_EXO),
                // you would add Double.parseDouble or Long.parseLong as appropriate.
                facility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, record.get(SynpopConstants.ZONE_ID));
                facility.getAttributes().putAttribute(SynpopConstants.NOGA_CODE, Integer.parseInt(record.get(SynpopConstants.NOGA_CODE)));

                // Assuming FTE_ENDO, FTE_EXO, JOBS_ENDO, JOBS_EXO are numeric (Doubles or Longs)
                double fteEndo = Double.parseDouble(record.get(SynpopConstants.FTE_ENDO));
                facility.getAttributes().putAttribute(SynpopConstants.FTE_ENDO, fteEndo);
                double fteExo = Double.parseDouble(record.get(SynpopConstants.FTE_EXO));
                facility.getAttributes().putAttribute(SynpopConstants.FTE_EXO, fteExo);
                facility.getAttributes().putAttribute(SynpopConstants.JOBS_ENDO, Long.parseLong(record.get(SynpopConstants.JOBS_ENDO)));
                facility.getAttributes().putAttribute(SynpopConstants.JOBS_EXO, Long.parseLong(record.get(SynpopConstants.JOBS_EXO)));
                if (record.isMapped(SynpopConstants.RELATIVE_ATTRACTION)) {
                    facility.getAttributes().putAttribute(SynpopConstants.RELATIVE_ATTRACTION, Double.parseDouble(record.get(SynpopConstants.RELATIVE_ATTRACTION)));
                } else {
                    facility.getAttributes().putAttribute(SynpopConstants.RELATIVE_ATTRACTION, fteEndo+fteExo);
                }
                facility.getAttributes().putAttribute(SynpopConstants.SCHOOL_TYPE, record.get(SynpopConstants.SCHOOL_TYPE));
                facility.getAttributes().putAttribute(SynpopConstants.SECTOR, Integer.parseInt(record.get(SynpopConstants.SECTOR)));

                facilities.addActivityFacility(facility);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Error parsing a number from CSV: " + e.getMessage(), e);
        }
    }
}