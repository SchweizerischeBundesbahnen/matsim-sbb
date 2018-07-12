package ch.sbb.matsim.synpop.loader;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.synpop.reader.Synpop2MATSim;
import org.matsim.api.core.v01.population.Population;

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.facilities.ActivityFacilities;


public class SynpopCSVLoaderImpl implements SynpopLoader {
    private static final Logger log = Logger.getLogger(SynpopCSVLoaderImpl.class);
    private Synpop2MATSim synpop2MATSim = new Synpop2MATSim();
    private String folder;

    public SynpopCSVLoaderImpl(String folder) {
        this.folder = folder;
    }

    @Override
    public ActivityFacilities getFacilities() {
        return synpop2MATSim.getFacilites();
    }

    @Override
    public Population getPopulation() {
        return synpop2MATSim.getPopulation();
    }

    @Override
    public void load() {
        this.readPersons(folder + "/persons.csv");
        this.readHouseholds(folder + "/households.csv");
        this.readBusinesses(folder + "/businesses.csv");
    }

    private void readPersons(String personsFile) {
        String[] columns = {"person_id", "household_id", "position_in_hh", "partnership_since", "business_id",
                "position_in_bus", "employed_since", "income", "status", "sex", "dbirth", "ddeath", "education",
                "position_in_edu", "nation", "mobility", "car_ownership", "language", "type_1", "type_2", "type_3",
                "type_4", "mother_id", "father_id", "decissionNumber"};

        try (CSVReader reader = new CSVReader(columns, personsFile, ";")) {
            Map<String, String> map = reader.readLine(); // header
            while ((map = reader.readLine()) != null) {
                synpop2MATSim.loadPerson(map);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }

    private void readBusinesses(String personsFile) {

        String[] columns = {"business_id", "location_id", "type_1", "type_2", "type_3", "profit", "dfoundation",
                "dclosing", "nr_of_jobs", "fte", "cb_nr_of_jobs", "cb_fte", "nr_of_cars", "school_type", "plz", "X", "Y"};

        try (CSVReader reader = new CSVReader(columns, personsFile, ";")) {
            Map<String, String> map = reader.readLine(); // header
            while ((map = reader.readLine()) != null) {
                synpop2MATSim.loadBusiness(map);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }

    private void readHouseholds(String personsFile) {
        String[] columns = {"household_id", "location_id", "dfoundation", "dclosing", "type_1", "type_2",
                "type_3", "type_4", "plz", "X", "Y"};

        try (CSVReader reader = new CSVReader(columns, personsFile, ";")) {
            Map<String, String> map = reader.readLine(); // header
            while ((map = reader.readLine()) != null) {
                synpop2MATSim.loadHousehold(map);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }


}
