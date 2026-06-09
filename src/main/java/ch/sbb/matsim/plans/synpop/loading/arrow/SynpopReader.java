package ch.sbb.matsim.plans.synpop.loading.arrow;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.SeekableReadChannel;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

public class SynpopReader {

        public static void readSynpopFile(String filePath, Population population) {
        File file = new File(filePath);
        readSynpopFile(file, population);
    }
    public static void readSynpopFile(File file, Population population) {
        try (
                BufferAllocator allocator = new RootAllocator();
                FileInputStream fileInputStream = new FileInputStream(file);
                SeekableReadChannel readChannel = new SeekableReadChannel(fileInputStream.getChannel());
                ArrowFileReader arrowFileReader = new ArrowFileReader(readChannel, allocator)
        ) {
            VectorSchemaRoot root = arrowFileReader.getVectorSchemaRoot();
            var dictionaryVectors = arrowFileReader.getDictionaryVectors();
            while (arrowFileReader.loadNextBatch()) {
                int rowCount = root.getRowCount();

                BigIntVector personIdVector = (BigIntVector) root.getVector(SynpopConstants.PERSON_ID);
                BigIntVector householdIdVector = (BigIntVector) root.getVector(SynpopConstants.HOUSEHOLD_ID);
                FieldVector highestEducationVector = root.getVector(SynpopConstants.HIGHEST_EDUCATION);
                BigIntVector levelOfEmploymentVector = (BigIntVector) root.getVector(SynpopConstants.LEVEL_OF_EMPLOYMENT);
                IntVector ageVector = (IntVector) root.getVector(SynpopConstants.AGE);
                BitVector isSwissVector = (BitVector) root.getVector(SynpopConstants.IS_SWISS);
                VarCharVector analysisSubpopulationVector = (VarCharVector) root.getVector(SynpopConstants.ANALYSIS_SUBPOPULATION);
                FieldVector languageVector = root.getVector(SynpopConstants.LANGUAGE);
                FieldVector currentEduVector = root.getVector(SynpopConstants.CURRENT_EDU);
                FieldVector currentJobRankVector = root.getVector(SynpopConstants.CURRENT_JOB_RANK);
                BitVector isApprenticeVector = (BitVector) root.getVector(SynpopConstants.IS_APPRENTICE);

                for (int i = 0; i < rowCount; i++) {
                    Id<Person> personId = Id.createPersonId(personIdVector.get(i));
                    Person person = population.getFactory().createPerson(personId);

                    person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD_ID, Long.toString(householdIdVector.get(i)));


                    String highestEducation = getStringFromVector(highestEducationVector, dictionaryVectors, i);
                    person.getAttributes().putAttribute(SynpopConstants.HIGHEST_EDUCATION, SynpopConstants.getCodeOrFail(SynpopConstants.HIGHEST_EDUCATION_REVERSE, highestEducation, SynpopConstants.HIGHEST_EDUCATION));

                    String language = getStringFromVector(languageVector, dictionaryVectors, i);
                    person.getAttributes().putAttribute(SynpopConstants.LANGUAGE, SynpopConstants.getCodeOrFail(SynpopConstants.LANGUAGE_REVERSE, language, SynpopConstants.LANGUAGE));

                    String currentEdu = getStringFromVector(currentEduVector, dictionaryVectors, i);
                    person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, SynpopConstants.getCodeOrFail(SynpopConstants.CURRENT_EDU_REVERSE, currentEdu, SynpopConstants.CURRENT_EDU));

                    String jobRank = getStringFromVector(currentJobRankVector, dictionaryVectors, i);
                    person.getAttributes().putAttribute(SynpopConstants.CURRENT_JOB_RANK, SynpopConstants.getCodeOrFail(SynpopConstants.CURRENT_JOB_RANK_REVERSE, jobRank, SynpopConstants.CURRENT_JOB_RANK));


                    person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, levelOfEmploymentVector.get(i));
                    person.getAttributes().putAttribute(SynpopConstants.AGE, ageVector.get(i));
                    person.getAttributes().putAttribute(SynpopConstants.IS_SWISS, isSwissVector.get(i) == 1);
                    person.getAttributes().putAttribute(SynpopConstants.ANALYSIS_SUBPOPULATION, analysisSubpopulationVector.getObject(i).toString());
                    person.getAttributes().putAttribute(SynpopConstants.IS_APPRENTICE, isApprenticeVector.get(i) == 1);

                    population.addPerson(person);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getStringFromVector(FieldVector vector, Map<Long, Dictionary> dictMap, int index) {
        var dictMetadata = vector.getField().getDictionary();
        if (dictMetadata != null) {
            long dictId = dictMetadata.getId();
            Dictionary dict = dictMap.get(dictId);
            if (dict == null) throw new IllegalStateException("Missing dictionary for id: " + dictId);
            int encodedValue = ((TinyIntVector) vector).get(index);
            VarCharVector dictVector = (VarCharVector) dict.getVector();
            return dictVector.getObject(encodedValue).toString();
        } else if (vector instanceof VarCharVector varCharVector) {
            return varCharVector.getObject(index).toString();
        } else {
            return vector.getObject(index).toString();
        }
    }
}