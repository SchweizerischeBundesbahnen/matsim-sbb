package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.SeekableReadChannel;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SynpopReader {

    public static void main(String[] args) {
        String featherPopulation = "C:\\devsbb\\tmp\\synpop_2022\\persons_2022_custom_batched.arrow";
        Population pop = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        double time = System.nanoTime();
        readSynpopFile(featherPopulation, pop);
        System.out.println(pop.getPersons().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
        new PopulationWriter(pop).write("C:\\devsbb\\tmp\\synpop_2022\\persons_2022.xml.gz");
    }

    public static void readSynpopFile(String filePath, Population population) {
        File file = new File(filePath);
        try (BufferAllocator allocator = new RootAllocator(); FileInputStream fileInputStream = new FileInputStream(file); SeekableReadChannel readChannel = new SeekableReadChannel(fileInputStream.getChannel()); ArrowFileReader arrowFileReader = new ArrowFileReader(readChannel, allocator)) {

            VectorSchemaRoot root = arrowFileReader.getVectorSchemaRoot();
            while (arrowFileReader.loadNextBatch()) {
                int rowCount = root.getRowCount();
                BigIntVector personIdVector = (BigIntVector) root.getVector(SynpopConstants.PERSON_ID);
                BigIntVector householdIdVector = (BigIntVector) root.getVector(SynpopConstants.HOUSEHOLD_ID);
                TinyIntVector positionInBusVector = (TinyIntVector) root.getVector(SynpopConstants.POSITION_IN_BUS);
                TinyIntVector highestEducationVector = (TinyIntVector) root.getVector(SynpopConstants.HIGHEST_EDUCATION);
                TinyIntVector positionInEduVector = (TinyIntVector) root.getVector(SynpopConstants.POSITION_IN_EDU);
                TinyIntVector nationVector = (TinyIntVector) root.getVector(SynpopConstants.NATION);
                TinyIntVector languageVector = (TinyIntVector) root.getVector(SynpopConstants.LANGUAGE);
                BigIntVector levelOfEmploymentVector = (BigIntVector) root.getVector(SynpopConstants.LEVEL_OF_EMPLOYMENT);
                IntVector ageVector = (IntVector) root.getVector(SynpopConstants.AGE);
                BitVector isSwissVector = (BitVector) root.getVector(SynpopConstants.IS_SWISS);
                VarCharVector analysisSubpopulationVector = (VarCharVector) root.getVector(SynpopConstants.ANALYSIS_SUBPOPULATION);
                BitVector isEmployedVector = (BitVector) root.getVector(SynpopConstants.IS_EMPLOYED);
                TinyIntVector loeGroupVector = (TinyIntVector) root.getVector(SynpopConstants.LOE_GROUP);
                TinyIntVector currentEduVector = (TinyIntVector) root.getVector(SynpopConstants.CURRENT_EDU);
                TinyIntVector currentJobRankVector = (TinyIntVector) root.getVector(SynpopConstants.CURRENT_JOB_RANK);
                BitVector isApprenticeVector = (BitVector) root.getVector(SynpopConstants.IS_APPRENTICE);

                for (int i = 0; i < rowCount; i++) {

                    Id<Person> personId = Id.createPersonId(personIdVector.get(i));
                    Person person = population.getFactory().createPerson(Id.createPersonId(personId));
                    person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD_ID, Long.toString(householdIdVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.POSITION_IN_BUS, Integer.valueOf(positionInBusVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.HIGHEST_EDUCATION, Integer.valueOf(highestEducationVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.POSITION_IN_EDU, Integer.valueOf(positionInEduVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.NATION, Integer.valueOf(nationVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.LANGUAGE, Integer.valueOf(languageVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, levelOfEmploymentVector.get(i));
                    person.getAttributes().putAttribute(SynpopConstants.AGE, ageVector.get(i));
                    person.getAttributes().putAttribute(SynpopConstants.IS_SWISS, isSwissVector.get(i) == 1);
                    person.getAttributes().putAttribute(SynpopConstants.ANALYSIS_SUBPOPULATION, analysisSubpopulationVector.getObject(i).toString());
                    person.getAttributes().putAttribute(SynpopConstants.IS_EMPLOYED, isEmployedVector.get(i) == 1);
                    person.getAttributes().putAttribute(SynpopConstants.LOE_GROUP, Integer.valueOf(loeGroupVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, Integer.valueOf(currentEduVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.CURRENT_JOB_RANK, Integer.valueOf(currentJobRankVector.get(i)));
                    person.getAttributes().putAttribute(SynpopConstants.IS_APPRENTICE, isApprenticeVector.get(i) == 1);
                    population.addPerson(person);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
