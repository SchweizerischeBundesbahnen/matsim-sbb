package ch.sbb.matsim.plans.synpop.loading.csv;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class SynpopCsvReader {

    public static void main(String[] args) {
        String csvPopulation = "/Volumes/mobi/40_Projekte/20241104_MOBi_5.1/synpop/cb_commuters/v10/persons.csv"; // Adjust path as needed
        Population pop = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        double time = System.nanoTime();
        readSynpopCsvFile(csvPopulation, pop);
        System.out.println(pop.getPersons().size());
        System.out.println("Calculation time " + 0.001 * (System.nanoTime() - time));
        new PopulationWriter(pop).write("/Users/joschka.bischoff/tmp/synpop2022/cb_commuters.xml.gz");
    }

    public static void readSynpopCsvFile(String filePath, Population population) {
        File file = new File(filePath);
        readSynpopCsvFile(file, population);
    }

    public static void readSynpopCsvFile(File file, Population population) {
        try (
                Reader in = new FileReader(file);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader().withTrim())
        ) {
            for (CSVRecord record : parser) {
                Id<Person> personId = Id.createPersonId(Long.parseLong(record.get(SynpopConstants.PERSON_ID)));
                Person person = population.getFactory().createPerson(personId);

                person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD_ID, record.get(SynpopConstants.HOUSEHOLD_ID));

                String highestEducation = record.get(SynpopConstants.HIGHEST_EDUCATION);
                person.getAttributes().putAttribute(SynpopConstants.HIGHEST_EDUCATION, SynpopConstants.getCodeOrFail(SynpopConstants.HIGHEST_EDUCATION_REVERSE, highestEducation, SynpopConstants.HIGHEST_EDUCATION));

                String language = record.get(SynpopConstants.LANGUAGE);
                person.getAttributes().putAttribute(SynpopConstants.LANGUAGE, SynpopConstants.getCodeOrFail(SynpopConstants.LANGUAGE_REVERSE, language, SynpopConstants.LANGUAGE));

                String currentEdu = record.get(SynpopConstants.CURRENT_EDU);
                person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, SynpopConstants.getCodeOrFail(SynpopConstants.CURRENT_EDU_REVERSE, currentEdu, SynpopConstants.CURRENT_EDU));

                String jobRank = record.get(SynpopConstants.CURRENT_JOB_RANK);
                person.getAttributes().putAttribute(SynpopConstants.CURRENT_JOB_RANK, SynpopConstants.getCodeOrFail(SynpopConstants.CURRENT_JOB_RANK_REVERSE, jobRank, SynpopConstants.CURRENT_JOB_RANK));

                person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, Long.parseLong(record.get(SynpopConstants.LEVEL_OF_EMPLOYMENT)));
                person.getAttributes().putAttribute(SynpopConstants.AGE, Integer.parseInt(record.get(SynpopConstants.AGE)));
                person.getAttributes().putAttribute(SynpopConstants.IS_SWISS, Boolean.parseBoolean(record.get(SynpopConstants.IS_SWISS)));
                person.getAttributes().putAttribute(SynpopConstants.ANALYSIS_SUBPOPULATION, record.get(SynpopConstants.ANALYSIS_SUBPOPULATION));
                boolean isApprentice = false;
                try {
                    isApprentice = Boolean.parseBoolean(record.get(SynpopConstants.IS_APPRENTICE));
                } catch (IllegalArgumentException e) {
                }
                person.getAttributes().putAttribute(SynpopConstants.IS_APPRENTICE, isApprentice);
                population.addPerson(person);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}