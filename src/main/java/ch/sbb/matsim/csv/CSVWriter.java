/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.core.utils.misc.Counter;

import java.io.*;
import java.nio.charset.Charset;

public class CSVWriter implements AutoCloseable {

    private final String separator;
    private final String[] columns;
    private final int columnCount;
    private final BufferedWriter writer;
    private final String[] currentRow;
    private final Counter counter;

    public CSVWriter(final String header, final String[] columns, final String filename) throws IOException {
        this(header, columns, IOUtils.getBufferedWriter(filename), ";");
    }

    public CSVWriter(final String header, final String[] columns, final String filename, final String separator) throws IOException {
        this(header, columns, IOUtils.getBufferedWriter(filename), separator);
    }

    public CSVWriter(final String header, final String[] columns, final String filename, final Charset encoding) throws IOException {
        this(header, columns, new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), encoding)), ";");
    }

    public CSVWriter(final String header, final String[] columns, final String filename, final Charset encoding, final String separator) throws IOException {
        this(header, columns, new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), encoding)), separator);
    }

    public CSVWriter(final String header, final String[] columns, final OutputStream stream, final String separator) throws IOException {
        this(header, columns, new BufferedWriter(new OutputStreamWriter(stream)), separator);
    }

    private CSVWriter(final String header, final String[] columns, final BufferedWriter writer, final String separator) throws IOException {
        this.columns = columns;
        this.columnCount = this.columns.length;
        this.currentRow = new String[this.columnCount];
        this.writer = writer;
        this.counter = new Counter("Output lines written: ");
        this.separator = separator;

        // write header data
        if (header != null) {
            this.writer.write(header);
        }

        // write column names
        for (int i = 0; i < this.columnCount; i++) {
            if (i > 0) {
                this.writer.write(this.separator);
            }
            String col = columns[i];
            this.writer.write(col);
        }
        this.writer.write("\n");

        clearRow();
    }

    /**
     * Sets the column in the current row to the specified value;
     * @param column
     * @param value
     */
    public void set(String column, String value) {
        for (int i = 0; i < this.columnCount; i++) {
            if (this.columns[i].equals(column)) {
                this.currentRow[i] = value;
                return;
            }
        }
        throw new IllegalArgumentException("Column not found: " + column);
    }

    /**
     * Writes the current row to the file and clears the current row afterwards.
     *
     * @throws UncheckedIOException
     */
    public void writeRow() throws UncheckedIOException {
        writeRow(false);
    }

    public void writeRow(boolean flush) throws UncheckedIOException {
        try {
            for (int i = 0; i < this.columnCount; i++) {
                if (i > 0) {
                    this.writer.write(this.separator);
                }
                this.writer.write(this.currentRow[i]);
            }
            this.writer.write("\n");
            if (flush) {
                this.writer.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.counter.incCounter();
        clearRow();
    }

    private void clearRow() {
        for (int i = 0; i < this.columns.length; i++) {
            this.currentRow[i] = "";
        }
    }

    @Override
    public void close() throws IOException {
        this.counter.printCounter();
        this.writer.close();
    }

}
