package ch.sbb.matsim.rerouting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class FilterPathLegs {

    public static void main(String[] args) {

        reduceCSV();

    }

    private static void reduceCSV() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("Z:/99_Playgrounds/MD/Umlegung/Input/Visum/modell_2028_DWV_putPathLegs_reduced.csv"))) {
            try (BufferedReader reader = new BufferedReader(new FileReader("Z:/99_Playgrounds/MD/Umlegung/Input/Visum/modell_2028_DWV_putPathLegs.csv"))) {
                List<String> header = List.of(reader.readLine().split(";"));
                writer.write("PATHINDEX;PATHLEGINDEX;FROMSTOPPOINTNO;TOSTOPPOINTNO;DEPTIME;ARRTIME");
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] splitLine = line.split(";");
                    writer.newLine();
                    writer.write(splitLine[header.indexOf("PATHINDEX")] + ";" +
                        splitLine[header.indexOf("PATHLEGINDEX")] + ";" +
                        splitLine[header.indexOf("FROMSTOPPOINTNO")] + ";" +
                        splitLine[header.indexOf("TOSTOPPOINTNO")] + ";" +
                        splitLine[header.indexOf("DEPTIME")] + ";" +
                        splitLine[header.indexOf("ARRTIME")]);
                    writer.flush();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
