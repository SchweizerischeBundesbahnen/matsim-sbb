/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import java.io.IOException;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class CSVWriter {
    final static Logger logger = Logger.getLogger(CSVWriter.class);
    private final String[] columns;
    private List<HashMap<String, String>> data = new ArrayList<>();

    public CSVWriter(final String[] columns) {
        this.columns = columns;
    }

    public HashMap<String, String> addRow() {
        HashMap<String, String> row = new HashMap<>();
        for (String c : columns) {
            row.put(c, "");
        }
        data.add(row);
        return row;
    }

    public List<HashMap<String,String>> getData(){
        return this.data;
    }

    public void clear(){
        data.clear();
    }

    public void write(String filename) {
        try {
            BufferedWriter Writer = IOUtils.getBufferedWriter(filename);

            String s = "";
            for (String c : columns) {
                s += c + ";";
            }

            Writer.write(s + "\n");

            Counter counter = new Counter("Output lines written: ");
            for (HashMap<String, String> d : data) {
                s = "";
                for (String c : columns) {
                    s += d.get(c) + ";";
                }

                Writer.write(s + "\n");
                counter.incCounter();
            }

            Writer.close();
            counter.printCounter();
        } catch (IOException ex) {

        }
        logger.info(filename);
    }
}
