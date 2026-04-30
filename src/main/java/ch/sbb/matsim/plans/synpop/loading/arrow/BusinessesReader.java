package ch.sbb.matsim.plans.synpop.loading.arrow;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.SeekableReadChannel;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BusinessesReader {

    public static void main(String[] args) {
        String featherPopulation = "/Volumes/mobi/50_Ergebnisse/MOBi_5.1/synpop/20250407_synpop_jobs_for_work_kalif_on_amr_v6/synpop_2022/businesses_2022.feather";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        var activityFacilities = scenario.getActivityFacilities();
        double time = System.nanoTime();
        readBusinessesFile(featherPopulation, activityFacilities);
        System.out.println(activityFacilities.getFacilities().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
    }

    public static void readBusinessesFile(String filePath, ActivityFacilities facilities) {
        File file = new File(filePath);
        readBusinessesFile(file, facilities);
    }
    public static void readBusinessesFile(File file, ActivityFacilities facilities) {

        try (BufferAllocator allocator = new RootAllocator(); FileInputStream fileInputStream = new FileInputStream(file); SeekableReadChannel readChannel = new SeekableReadChannel(fileInputStream.getChannel()); ArrowFileReader arrowFileReader = new ArrowFileReader(readChannel, allocator)) {

            VectorSchemaRoot root = arrowFileReader.getVectorSchemaRoot();
            while (arrowFileReader.loadNextBatch()) {
                int rowCount = root.getRowCount();
                ValueVector businessIdVector = root.getVector(SynpopConstants.BUSINESS_ID);
                ValueVector zoneIdVector = root.getVector(SynpopConstants.ZONE_ID);
                Float8Vector xCoordVector = (Float8Vector) root.getVector(SynpopConstants.XCOORD);
                Float8Vector yCoordVector = (Float8Vector) root.getVector(SynpopConstants.YCOORD);
                ValueVector nogaCodeVector = root.getVector(SynpopConstants.NOGA_CODE);
                ValueVector fteEndoVector = root.getVector(SynpopConstants.FTE_ENDO);
                ValueVector fteExoVector = root.getVector(SynpopConstants.FTE_EXO);
                ValueVector jobsExoVector = root.getVector(SynpopConstants.JOBS_EXO);
                ValueVector jobsEndoVector = root.getVector(SynpopConstants.JOBS_ENDO);
                ValueVector schoolTypeVector = root.getVector(SynpopConstants.SCHOOL_TYPE);
                ValueVector sectorVector = root.getVector(SynpopConstants.SECTOR);
                ValueVector attractionVector = root.getVector(SynpopConstants.RELATIVE_ATTRACTION);


                for (int i = 0; i < rowCount; i++) {
                    Id<ActivityFacility> facilityId = Id.create(getVectorValue(businessIdVector, i).toString(), ActivityFacility.class);
                    Coord coord = new Coord(xCoordVector.get(i), yCoordVector.get(i));
                    var facility = facilities.getFactory().createActivityFacility(facilityId, coord);
                    facility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, String.valueOf(getVectorValue(zoneIdVector, i)));
                    facility.getAttributes().putAttribute(SynpopConstants.NOGA_CODE, getVectorValue(nogaCodeVector, i));
                    Object fteEndo = getVectorValue(fteEndoVector, i);
                    facility.getAttributes().putAttribute(SynpopConstants.FTE_ENDO, fteEndo);
                    Object fteExo = getVectorValue(fteExoVector, i);
                    facility.getAttributes().putAttribute(SynpopConstants.FTE_EXO, fteExo);
                    facility.getAttributes().putAttribute(SynpopConstants.JOBS_ENDO, getVectorValue(jobsEndoVector, i));
                    facility.getAttributes().putAttribute(SynpopConstants.JOBS_EXO, getVectorValue(jobsExoVector, i));
                    facility.getAttributes().putAttribute(SynpopConstants.SCHOOL_TYPE, getVectorValue(schoolTypeVector, i));
                    facility.getAttributes().putAttribute(SynpopConstants.SECTOR, getVectorValue(sectorVector, i));
                    if (attractionVector != null) {
                        facility.getAttributes().putAttribute(SynpopConstants.RELATIVE_ATTRACTION, getVectorValue(attractionVector, i));
                    } else {
                        facility.getAttributes().putAttribute(SynpopConstants.RELATIVE_ATTRACTION, (double)fteEndo+(double)fteExo);
                    }
                    facilities.addActivityFacility(facility);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Object getVectorValue(ValueVector vector, int index) {
        if (vector instanceof Float8Vector v) {
            return v.get(index);
        } else if (vector instanceof IntVector v) {
            return v.get(index);
        } else if (vector instanceof BigIntVector v) {
            return v.get(index);
        } else if (vector instanceof TinyIntVector v) {
            return Integer.valueOf(v.get(index));
        } else if (vector instanceof org.apache.arrow.vector.SmallIntVector v) {
            return v.get(index);}
        else if (vector instanceof BitVector v) {
            return v.get(index);
        } else if (vector instanceof VarCharVector v) {
            return v.getObject(index).toString();
        } else {
            throw new IllegalArgumentException("Unsupported vector type: " + vector.getClass());
        }
    }


}
