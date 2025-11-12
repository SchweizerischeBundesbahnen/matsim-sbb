package ch.sbb.matsim.plans.modules;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MOBiZonesProvider implements Provider<Zones> {

    private static Logger logger = LogManager.getLogger(MOBiZonesProvider.class);
    @Inject
    MOBiPlansConfig config;

    @Inject
    Scenario scenario;

    @Override
    public Zones get() {
        Zones zones = ZonesLoader.loadZones("zones", config.data().zonesFile());
        List<String> additionalZonalAttributesFiles = new ArrayList<>();
        if (config.data().zone_informations()!= null) {
            additionalZonalAttributesFiles.add(config.data().zone_informations());
        } else {
            logger.warn("No zone_informations file provided in config. Skipping loading additional zone informations.");
        }
        if (config.data().school_enr_apprentices()!= null) {
            additionalZonalAttributesFiles.add(config.data().school_enr_apprentices());
        } else {
            logger.warn("No school_enr_apprentices file provided in config. Skipping loading additional school enrolment and apprentices data.");
        }
        if (config.data().sff_attributes() != null) {
            additionalZonalAttributesFiles.add(config.data().sff_attributes());
        } else {
            logger.warn("No sff_attributes file provided in config. Skipping loading additional school enrolment and apprentices data.");
        }
        if (config.data().foreign_attractions()!= null) {;
            additionalZonalAttributesFiles.add(config.data().foreign_attractions());
        } else {
            logger.warn("No foreign_attractions file provided in config. Skipping loading additional foreign attractions data.");
        }

        for (String filename : additionalZonalAttributesFiles) {
            if (filename != null && !filename.isBlank()) {
                loadZonalAttributes(zones, filename);
                logger.info("Loaded additional zonal attributes from file: " + filename);
            }
        }
        //convenience: add zones to scenario elements for easier access later
        scenario.addScenarioElement(Variables.ZONES, zones);
        return zones;
    }

    /**
     * Reads a semicolon-separated CSV file using Apache Commons CSV to populate zones
     * with detailed attributes. It assumes the file has a header row where the column
     * names match the desired attribute keys.
     *
     * @param zones    The {@link Zones} object to be populated.
     * @param filename The path to the attributes CSV file.
     */
    private void loadZonalAttributes(Zones zones, String filename) {
        // Define the CSV format: semicolon-separated, with a header row.
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();

        try (BufferedReader reader = IOUtils.getBufferedReader(filename);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            // Ensure the required ZONE_ID header exists.
            if (!csvParser.getHeaderMap().containsKey(ZonalAttributes.ZONE_ID)) {
                throw new IllegalStateException("CSV file must contain a '" + ZonalAttributes.ZONE_ID + "' header column. File: " + filename);
            }

            for (CSVRecord record : csvParser) {
                String zoneIdValue = record.get(ZonalAttributes.ZONE_ID);
                Zone zone = zones.getZone(Id.create(zoneIdValue, Zone.class));

                if (zone == null) {
                    logger.warn("Warning: Zone with ID '" + zoneIdValue + "' from attributes file not found. Skipping attributes for this row.");
                    continue;
                }

                // Iterate over all columns in the row using the header map
                for (String header : csvParser.getHeaderNames()) {
                    if (header.equals(ZonalAttributes.ZONE_ID) || !record.isSet(header) || record.get(header).isEmpty()) {
                        continue;
                    }

                    String value = record.get(header);
                    Object parsedValue;

                    try {
                        // Attempt to parse as an Integer first for whole numbers.
                        parsedValue = Integer.parseInt(value);
                    } catch (NumberFormatException e1) {
                        try {
                            // If that fails, attempt to parse as a Double.
                            parsedValue = Double.parseDouble(value);
                        } catch (NumberFormatException e2) {
                            // If both numeric conversions fail, treat it as a String.
                            parsedValue = value;
                        }
                    }

                    zone.getAttributes().putAttribute(header, parsedValue);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read or parse zonal attributes file: " + filename, e);
        }
    }
}
