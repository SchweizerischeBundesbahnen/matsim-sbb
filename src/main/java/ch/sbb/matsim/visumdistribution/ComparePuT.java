package ch.sbb.matsim.visumdistribution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ComparePuT {

    public static void main(String[] args) {

        String visum = "Z:/99_Playgrounds/MD/Umlegung2/PathRoutesVisumLines.csv";
        String matsim = "Z:/99_Playgrounds/MD/Umlegung2/routesDepatureVisumLines.csv";
        String out = "beideLines.csv";

        Set<String> matSimRoutesSet = new HashSet<>();
        Set<String> visumRoutesSet = new HashSet<>();
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(matsim))) {
            String line;
            List<String> header = List.of(reader.readLine().split(";"));
            String newLine = "";
            int index = -1;
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                if (!newLine.equals("")) {
                    if (splitLine[header.indexOf("DATENSATZNR")].equals(String.valueOf(index))) {
                        newLine = newLine +
                            splitLine[header.indexOf("TWEGIND")] +
                            splitLine[header.indexOf("VONHSTNR")] +
                            splitLine[header.indexOf("NACHHSTNR")] +
                            splitLine[header.indexOf("VSYSCODE")] +
                            splitLine[header.indexOf("LINNAME")] +
                            splitLine[header.indexOf("LINROUTENAME")] +
                            splitLine[header.indexOf("RICHTUNGSCODE")] +
                            splitLine[header.indexOf("FZPNAME")] +
                            splitLine[header.indexOf("EINHSTABFAHRTSZEIT")];
                    } else {
                        matSimRoutesSet.add(newLine);
                        newLine = "";
                        index = Integer.parseInt(splitLine[header.indexOf("DATENSATZNR")]);
                        newLine = newLine +
                            splitLine[header.indexOf("TWEGIND")] +
                            splitLine[header.indexOf("VONHSTNR")] +
                            splitLine[header.indexOf("NACHHSTNR")] +
                            splitLine[header.indexOf("VSYSCODE")] +
                            splitLine[header.indexOf("LINNAME")] +
                            splitLine[header.indexOf("LINROUTENAME")] +
                            splitLine[header.indexOf("RICHTUNGSCODE")] +
                            splitLine[header.indexOf("FZPNAME")] +
                            splitLine[header.indexOf("EINHSTABFAHRTSZEIT")];
                    }
                } else {
                    index = Integer.parseInt(splitLine[header.indexOf("DATENSATZNR")]);
                    newLine = newLine +
                        splitLine[header.indexOf("TWEGIND")] +
                        splitLine[header.indexOf("VONHSTNR")] +
                        splitLine[header.indexOf("NACHHSTNR")] +
                        splitLine[header.indexOf("VSYSCODE")] +
                        splitLine[header.indexOf("LINNAME")] +
                        splitLine[header.indexOf("LINROUTENAME")] +
                        splitLine[header.indexOf("RICHTUNGSCODE")] +
                        splitLine[header.indexOf("FZPNAME")] +
                        splitLine[header.indexOf("EINHSTABFAHRTSZEIT")];
                }
            }
            matSimRoutesSet.add(newLine);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(matSimRoutesSet.size());
        int beide = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(visum))) {
            String line;
            List<String> header = List.of(reader.readLine().split(";"));
            String newLine = "";
            int index = -1;
            int pos = 0;
            String[]  connection = new String[10];
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                if (line.equals("")) {
                    continue;
                }
                if (!newLine.equals("")) {
                    if (splitLine[header.indexOf("PATHINDEX")].equals(String.valueOf(index))) {
                        connection[pos] = line;
                        newLine = newLine +
                            splitLine[header.indexOf("PATHLEGINDEX")] +
                            splitLine[header.indexOf("FROMSTOPPOINTNO")] +
                            splitLine[header.indexOf("TOSTOPPOINTNO")] +
                            splitLine[header.indexOf("TSYSCODE")] +
                            splitLine[header.indexOf("LINENAME")] +
                            splitLine[header.indexOf("LINEROUTENAME")] +
                            splitLine[header.indexOf("DIRECTIONCODE")] +
                            splitLine[header.indexOf("TIMEPROFILENAME")] +
                            splitLine[header.indexOf("DEP")];
                    } else {
                        visumRoutesSet.add(newLine);
                        if (matSimRoutesSet.add(newLine)) {
                            for (int i = 0; i < 10; i++) {
                                if (connection[i] != null) {
                                    lines.add(connection[i]);
                                }
                            }
                        } else {
                            beide++;
                        }
                        newLine = "";
                        connection = new String[10];
                        index = Integer.parseInt(splitLine[header.indexOf("PATHINDEX")]);
                        newLine = newLine +
                            splitLine[header.indexOf("PATHLEGINDEX")] +
                            splitLine[header.indexOf("FROMSTOPPOINTNO")] +
                            splitLine[header.indexOf("TOSTOPPOINTNO")] +
                            splitLine[header.indexOf("TSYSCODE")] +
                            splitLine[header.indexOf("LINENAME")] +
                            splitLine[header.indexOf("LINEROUTENAME")] +
                            splitLine[header.indexOf("DIRECTIONCODE")] +
                            splitLine[header.indexOf("TIMEPROFILENAME")] +
                            splitLine[header.indexOf("DEP")];
                    }
                } else {
                    index = Integer.parseInt(splitLine[header.indexOf("PATHINDEX")]);
                    newLine = newLine +
                        splitLine[header.indexOf("PATHLEGINDEX")] +
                        splitLine[header.indexOf("FROMSTOPPOINTNO")] +
                        splitLine[header.indexOf("TOSTOPPOINTNO")] +
                        splitLine[header.indexOf("TSYSCODE")] +
                        splitLine[header.indexOf("LINENAME")] +
                        splitLine[header.indexOf("LINEROUTENAME")] +
                        splitLine[header.indexOf("DIRECTIONCODE")] +
                        splitLine[header.indexOf("TIMEPROFILENAME")] +
                        splitLine[header.indexOf("DEP")];
                }
            }
            visumRoutesSet.add(newLine);
            if (matSimRoutesSet.add(newLine)) {
                for (int i = 0; i < 10; i++) {
                    if (connection[i] != null) {
                        lines.add(connection[i]);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(visumRoutesSet.size());
        System.out.println(lines.size());
        System.out.println(beide);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
