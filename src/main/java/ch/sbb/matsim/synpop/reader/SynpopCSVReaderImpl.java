package ch.sbb.matsim.synpop.reader;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.synpop.loader.Synpop2MATSim;
import org.matsim.api.core.v01.population.Population;

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.facilities.ActivityFacilities;


public class SynpopCSVReaderImpl implements SynpopReader {
    private static final Logger log = Logger.getLogger(SynpopCSVReaderImpl.class);
    private Synpop2MATSim synpop2MATSim = new Synpop2MATSim();
    private String folder;

    public SynpopCSVReaderImpl(String folder) {
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
        try (CSVReader reader = new CSVReader(personsFile, ";")) {
            Map<String, String> map;
            int i = 0;
            while ((map = reader.readLine()) != null && i < 100) {
                synpop2MATSim.loadPerson(map);
                i++;
            }
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private void readBusinesses(String personsFile) {
        try (CSVReader reader = new CSVReader(personsFile, ";")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                synpop2MATSim.loadBusiness(map);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }

    private void readHouseholds(String personsFile) {
        try (CSVReader reader = new CSVReader(personsFile, ";")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                synpop2MATSim.loadHousehold(map);
            }
        } catch (IOException e) {
            log.warn(e);
        }

    }


}
