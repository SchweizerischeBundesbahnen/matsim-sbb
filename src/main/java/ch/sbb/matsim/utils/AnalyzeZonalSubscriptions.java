package ch.sbb.matsim.utils;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.SimpleFeatureZone;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.opengis.feature.simple.SimpleFeature;

public class AnalyzeZonalSubscriptions {

    private final Set<PersonDefinition> personDefinitionSet = new HashSet<>();
    private ZonesImpl zones;

    public static void main(String[] args) {
        String personsFile = "C:\\Users\\u229187\\Desktop\\jugend\\persons-16-25.csv";
        String outputFile = "C:\\Users\\u229187\\Desktop\\jugend\\persons-16-25-out.csv";
        String shapeFile = "C:\\Users\\u229187\\Desktop\\jugend\\verkehrsklassen-poly.shp";
        new AnalyzeZonalSubscriptions().run(personsFile, outputFile, shapeFile);
    }

    private void run(String personsFile, String outputFile, String shapeFile) {
        readZones(shapeFile);
        readPersons(personsFile);
        analyzePersons();
        writePersons(outputFile);
    }

    private void writePersons(String outputFile) {
        String pt_subscr_str = "PT_SUBSCR_STR";
        String pt_subscr = "PT_SUBSCR";
        String age = "AGE";
        String ycoord = "YCOORD";
        String xcoord = "XCOORD";
        String personNo = "$PERSON:NO";
        String ptClass = "gueteKlasse";
        String[] header = {personNo, xcoord, ycoord, age, pt_subscr, pt_subscr_str, ptClass};
        try (CSVWriter writer = new CSVWriter(null, header, outputFile)) {
            for (PersonDefinition p : this.personDefinitionSet) {
                writer.set(personNo, p.id);
                writer.set(age, Integer.toString(p.age));
                writer.set(pt_subscr, Integer.toString(p.ptSubscription));
                writer.set(pt_subscr_str, p.ptSubscriptionString);
                writer.set(xcoord, Double.toString(p.coord.getX()));
                writer.set(ycoord, Double.toString(p.coord.getY()));
                writer.set(ptClass, p.ptClass);
                writer.writeRow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void analyzePersons() {
        personDefinitionSet.parallelStream().forEach(p -> {
            Zone z = zones.findZone(p.coord);
            p.ptClass = z == null ? "N" : z.getAttribute("KLASSE").toString();
        });
    }

    private void readZones(String shapeFile) {
        zones = new ZonesImpl(Id.create("z", Zones.class));
        int i = 0;
        for (SimpleFeature sf : ShapeFileReader.getAllFeatures(shapeFile)) {
            String zoneId = Integer.toString(i++);
            Zone zone = new SimpleFeatureZone(Id.create(zoneId, Zone.class), sf);
            zones.add(zone);
        }

    }

    private void readPersons(String personsFile) {
        TabularFileParser tbf = new TabularFileParser();
        TabularFileParserConfig tbc = new TabularFileParserConfig();
        tbc.setDelimiterRegex(";");
        tbc.setFileName(personsFile);
        tbf.parse(tbc, strings -> {
            if (!strings[0].startsWith("$") && strings.length == 6) {
                PersonDefinition p = new PersonDefinition(strings[0], new Coord(Double.parseDouble(strings[1]), Double.parseDouble(strings[2])), Integer.parseInt(strings[3]),
                        Integer.parseInt(strings[4]), strings[5]);
                personDefinitionSet.add(p);
            }
        });

    }

    private static class PersonDefinition {

        String id;
        Coord coord;
        int age;
        int ptSubscription;
        String ptSubscriptionString;
        String ptClass;

        public PersonDefinition(String id, Coord coord, int age, int ptSubscription, String ptSubscriptionString) {
            this.id = id;
            this.coord = coord;
            this.age = age;
            this.ptSubscription = ptSubscription;
            this.ptSubscriptionString = ptSubscriptionString;
        }
    }
}
