package ch.sbb.matsim.plans.synpop.loading.arrow;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.SeekableReadChannel;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BusinessesReader {


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
            throw new RuntimeException(e);
        }
    }

    private static Object getVectorValue(ValueVector vector, int index) {
        return switch (vector) {
            case Float8Vector v -> v.get(index);
            case IntVector v -> v.get(index);
            case BigIntVector v -> v.get(index);
            case TinyIntVector v -> Integer.valueOf(v.get(index));
            case SmallIntVector v -> v.get(index);
            case BitVector v -> v.get(index);
            case VarCharVector v -> v.getObject(index).toString();
            default -> throw new IllegalArgumentException("Unsupported vector type: " + vector.getClass());
        };
    }


}
