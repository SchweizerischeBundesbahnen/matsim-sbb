/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CSVReader implements AutoCloseable {


    private String[] columns;
    private final String splitBy;
    private final BufferedReader br;

    public CSVReader(String[] columns, final String csvFile, final String splitBy) throws UncheckedIOException {
        this.columns = columns;
        this.splitBy = splitBy;
        this.br = IOUtils.getBufferedReader(csvFile);
    }

    public CSVReader(final String csvFile, final String splitBy) throws UncheckedIOException, IOException {
        this.splitBy = splitBy;
        this.br = IOUtils.getBufferedReader(csvFile);

        String line = this.br.readLine();
        this.columns = line.split(this.splitBy);
        this.columns = Arrays.asList(this.columns).stream().map(item -> item.replace("\"", "")).toArray(size -> new String[size]);
    }


    public CSVReader(String[] columns, final InputStream stream, final String splitBy) {
        this.columns = columns;
        this.splitBy = splitBy;
        this.br = new BufferedReader(new InputStreamReader(stream));
    }


    public void setColumns(String[] columns) {
        this.columns = columns;
    }

    public String[] getColumns() {
        return columns;
    }

    /**
     * Reads the next available data row from the file.
     *
     * @return map containing the value for each column, <code>null</code> if no more line is available
     */
    public Map<String, String> readLine() throws IOException {
        String line = this.br.readLine();
        if (line == null) {
            return null;
        }

        Map<String, String> currentRow = new HashMap<>();
        String[] parts = line.split(this.splitBy, -1);
        for (int i = 0; i < this.columns.length; i++) {
            String column = this.columns[i];
            String value = parts[i];
            currentRow.put(column, value);
        }
        return currentRow;
    }

    @Override
    public void close() throws IOException {
        this.br.close();
    }

}
