/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class CSVReader {

    private final String[] columns;

    public CSVReader(String[] columns) {
        this.columns = columns;
    }

    public CSVIterator read(final String csvFile, final String splitBy) {
        return new CSVIterator(this.columns, csvFile, splitBy);
    }

    public static class CSVIterator implements Iterator<Map<String, String>>, AutoCloseable {
        private final BufferedReader br;
        private String nextLine;
        private final String[] columns;
        private final String splitBy;

        CSVIterator(String[] columns, final String csvFilename, final String splitBy) {
            this.columns = columns;
            this.splitBy = splitBy;
            try {
                this.br = new BufferedReader(new FileReader(csvFilename));
                this.nextLine = this.br.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return this.nextLine != null;
        }

        @Override
        public Map<String, String> next() {
            if (this.nextLine == null) {
                throw new NoSuchElementException();
            }
            Map<String, String> currentRow = new HashMap<>();
            String[] parts = this.nextLine.split(this.splitBy, -1);
            for (int i = 0; i < this.columns.length; i++) {
                String column = this.columns[i];
                String value = parts[i];
                currentRow.put(column, value);
            }
            try {
                this.nextLine = this.br.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return currentRow;
        }

        @Override
        public void close() throws IOException {
            this.br.close();
        }
    }

}
