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
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class BusinessesCsvReader {

     static void main(String[] args) {
        String csvBusinesses = "/Volumes/mobi/40_Projekte/20241104_MOBi_5.1/synpop/cb_commuters/v10/businesses.csv"; // Adjust path as needed
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        var activityFacilities = scenario.getActivityFacilities();
        double time = System.nanoTime();
        readBusinessesCsvFile(csvBusinesses, activityFacilities);
        System.out.println(activityFacilities.getFacilities().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
        new FacilitiesWriter(activityFacilities).write("/Users/joschka.bischoff/tmp/synpop2022/businesses_2022_facilities_from_csv_cb.xml.gz");
    }

    public static void readBusinessesCsvFile(String filePath, ActivityFacilities facilities) {
        File file = new File(filePath);
        readBusinessesCsvFile(file, facilities);
    }

    public static void readBusinessesCsvFile(File file, ActivityFacilities facilities) {

        try (
                Reader in = new FileReader(file);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT
                                                    .withDelimiter(';') // Set semicolon as delimiter
                                                    .withFirstRecordAsHeader()
                                                    .withTrim())
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
                facility.getAttributes().putAttribute(SynpopConstants.SECTOR, record.get(SynpopConstants.SECTOR));

                facilities.addActivityFacility(facility);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Error parsing a number from CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }
}