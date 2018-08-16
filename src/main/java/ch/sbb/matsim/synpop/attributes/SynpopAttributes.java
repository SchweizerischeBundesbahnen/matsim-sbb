package ch.sbb.matsim.synpop.attributes;

import ch.sbb.matsim.csv.CSVReader;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SynpopAttributes {

    private static final Logger log = Logger.getLogger(SynpopAttributes.class);
    private final Map<String, Map<String, SynpopAttribute>> data;


    public SynpopAttributes(String csvAttributesDef) {
        this.data = new HashMap<>();
        this.load(csvAttributesDef);
    }

    private void load(String file) {
        try (CSVReader reader = new CSVReader(file, ";")) {
            Map<String, String> row;
            while ((row = reader.readLine()) != null) {
                String object = row.get("OBJECT");
                this.data.putIfAbsent(object, new HashMap<>());

                SynpopAttribute synpopAttribute = new SynpopAttribute(row.get("ATTRIBUTE"), row.get("SQLTYPE"));
                this.data.get(object).put(synpopAttribute.getName(), synpopAttribute);

            }
        } catch (IOException e) {
            log.warn(e);
        }
    }

    public Collection<SynpopAttribute> getAttributes(String object){
        return this.data.get(object).values();
    }
}
