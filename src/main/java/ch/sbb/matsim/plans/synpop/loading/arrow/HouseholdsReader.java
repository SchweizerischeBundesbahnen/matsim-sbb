package ch.sbb.matsim.plans.synpop.loading.arrow;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.SeekableReadChannel;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.Household;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsWriterV10;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class HouseholdsReader {

    public static void main(String[] args) {
        String featherPopulation = "C:\\devsbb\\tmp\\synpop_2022\\households_2022.feather";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        var households = scenario.getHouseholds();
        double time = System.nanoTime();
        readHouseholdFile(featherPopulation, households);
        System.out.println(households.getHouseholds().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
        new HouseholdsWriterV10(households).writeFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022.xml.gz");
    }

    public static void readHouseholdFile(String filePath, Households households) {
        File file = new File(filePath);
        readHouseholdFile(file, households);
    }
        public static void readHouseholdFile(File file, Households households) {

        try (BufferAllocator allocator = new RootAllocator(); FileInputStream fileInputStream = new FileInputStream(file); SeekableReadChannel readChannel = new SeekableReadChannel(fileInputStream.getChannel()); ArrowFileReader arrowFileReader = new ArrowFileReader(readChannel, allocator)) {

            VectorSchemaRoot root = arrowFileReader.getVectorSchemaRoot();
            while (arrowFileReader.loadNextBatch()) {
                int rowCount = root.getRowCount();
                BigIntVector householdIdVector = (BigIntVector) root.getVector(SynpopConstants.HOUSEHOLD_ID);
                BigIntVector zoneIdVector = (BigIntVector) root.getVector(SynpopConstants.ZONE_ID);
                Float8Vector xCoordVector = (Float8Vector) root.getVector(SynpopConstants.XCOORD);
                Float8Vector yCoordVector = (Float8Vector) root.getVector(SynpopConstants.YCOORD);

                for (int i = 0; i < rowCount; i++) {
                    Id<Household> householdId = Id.create(householdIdVector.get(i), Household.class);
                    var household = households.getFactory().createHousehold(householdId);
                    households.getHouseholds().put(householdId, household);
                    Coord coord = new Coord(xCoordVector.get(i), yCoordVector.get(i));
                    household.getAttributes().putAttribute(SynpopConstants.COORD, coord);
                    household.getAttributes().putAttribute(SynpopConstants.ZONE_ID, Long.toString(zoneIdVector.get(i)));
                }
            }


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
