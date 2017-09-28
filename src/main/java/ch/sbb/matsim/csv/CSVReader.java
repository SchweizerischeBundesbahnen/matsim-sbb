/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CSVReader {
    public List<HashMap<String, String>> data = new ArrayList<>();
    private String[] columns;

    public CSVReader(String[] columns) {
        this.columns = columns;
    }

    public HashMap<String, String> addRow() {
        HashMap<String, String> row = new HashMap<>();
        for (String c : this.columns) {
            row.put(c, "");
        }
        data.add(row);
        return row;
    }

    public void read(final String csvFile, final String splitBy){
        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null) {
                // use comma as separator
                String[] country = line.split(splitBy, -1);
                HashMap<String, String> row = this.addRow();
                for (int i=0; i<this.columns.length; i++) {
                    String c = this.columns[i];
                    String d = country[i];
                    row.put(c, d);
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static void main(String[] args) {

        String csvFile = "/Users/mkyong/csv/country.csv";

    }
}
