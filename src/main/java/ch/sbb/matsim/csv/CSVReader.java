/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.csv;

import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVReader implements AutoCloseable {

	private final String splitBy;
	private final BufferedReader br;
	private final String csvSource;
	private String[] columns;
	private int currentLineNumber;

	public CSVReader(String[] columns, final String csvFile, final String splitBy) {
		this.columns = columns;
		this.splitBy = splitBy;
		this.br = IOUtils.getBufferedReader(csvFile);
		this.csvSource = csvFile;
	}

	public CSVReader(String[] columns, final URL csvFileURL, final String splitBy) {
		this.columns = columns;
		this.splitBy = splitBy;
		this.br = IOUtils.getBufferedReader(csvFileURL);
		this.csvSource = csvFileURL.toString();
	}

	public CSVReader(final URL csvFileURL, final String splitBy) throws IOException {
		this(splitBy, IOUtils.getBufferedReader(csvFileURL), csvFileURL.toString());
	}

	public CSVReader(final String csvFile, final String splitBy) throws IOException {
		this(splitBy, IOUtils.getBufferedReader(csvFile), csvFile);
	}

	public CSVReader(final String splitBy, BufferedReader br) throws IOException {
		this(splitBy, br, "<buffered-reader>");
	}

	private CSVReader(final String splitBy, BufferedReader br, String csvSource) throws IOException {
		this.splitBy = splitBy;
		this.br = br;
		this.csvSource = csvSource;

		String line = this.br.readLine();
		if (line == null) {
			throw new IOException("CSV file is empty: missing header line.");
		}
		this.currentLineNumber = 1;
		this.columns = parseCsvLine(line);
		//if column is not in contructor defined, we take it from the csv
		this.columns = Arrays.stream(this.columns).map(item -> item.replace("\"", "")).toArray(String[]::new);
	}

	public CSVReader(String[] columns, final InputStream stream, final String splitBy) {
		this.columns = columns;
		this.splitBy = splitBy;
		this.br = new BufferedReader(new InputStreamReader(stream));
		this.csvSource = "<input-stream>";
	}

	public String[] getColumns() {
		return columns;
	}

	public void setColumns(String[] columns) {
		this.columns = columns;
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
		this.currentLineNumber++;

		Map<String, String> currentRow = new HashMap<>();
		String[] parts = parseCsvLine(line);
		if (parts.length != this.columns.length) {
			throw new IOException("Malformed CSV in '" + this.csvSource + "' at line " + this.currentLineNumber
				+ ": expected " + this.columns.length + " columns but found " + parts.length + ".");
		}
		for (int i = 0; i < this.columns.length; i++) {
			String column = this.columns[i];
			String value = parts[i];
			currentRow.put(column, value);
		}
		return currentRow;
	}

	/**
	 * Goal: ignoring the ";" inside quotes ("..."), as in the NOVA code file.
	 */
	private String[] parseCsvLine(String line) throws IOException {
		List<String> values = new ArrayList<>();
		StringBuilder currentValue = new StringBuilder();
		boolean inQuotes = false;
		int delimiterLength = this.splitBy.length();

		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					currentValue.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
				continue;
			}

			if (!inQuotes && delimiterLength > 0 && i + delimiterLength <= line.length()
					&& line.startsWith(this.splitBy, i)) {
				values.add(currentValue.toString());
				currentValue.setLength(0);
				i += delimiterLength - 1;
				continue;
			}

			currentValue.append(ch);
		}

		if (inQuotes) {
			throw new IOException("Malformed CSV in '" + this.csvSource + "' at line " + this.currentLineNumber
					+ ": unterminated quoted field.");
		}

		values.add(currentValue.toString());
		return values.toArray(String[]::new);
	}

	@Override
	public void close() throws IOException {
		this.br.close();
	}

}