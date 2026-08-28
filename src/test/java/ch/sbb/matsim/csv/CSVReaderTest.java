/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.csv;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class CSVReaderTest {

	@Test
	public void read() throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter("testfile.csv"));
		writer.write("ATTR1;ATTR2;ATTR3\none;two;three\neins;zwei;drei\nun;deux;trois\n");
		writer.close();

		CSVReader reader = new CSVReader(new String[]{"ATTR1", "ATTR2", "ATTR3"}, "testfile.csv", ";");

		// header
		Map<String, String> row0 = reader.readLine();
		Assertions.assertNotNull(row0);
		Assertions.assertEquals(3, row0.size(), "row has too many columns");
		Assertions.assertEquals("ATTR1", row0.get("ATTR1"));
		Assertions.assertEquals("ATTR2", row0.get("ATTR2"));
		Assertions.assertEquals("ATTR3", row0.get("ATTR3"));

		// row1
		Map<String, String> row1 = reader.readLine();
		Assertions.assertNotNull(row1);
		Assertions.assertEquals(3, row1.size(), "row has too many columns");
		Assertions.assertEquals("one", row1.get("ATTR1"));
		Assertions.assertEquals("two", row1.get("ATTR2"));
		Assertions.assertEquals("three", row1.get("ATTR3"));

		// row2
		Map<String, String> row2 = reader.readLine();
		Assertions.assertNotNull(row2);
		Assertions.assertEquals(3, row2.size(), "row has too many columns");
		Assertions.assertEquals("eins", row2.get("ATTR1"));
		Assertions.assertEquals("zwei", row2.get("ATTR2"));
		Assertions.assertEquals("drei", row2.get("ATTR3"));

		// row3
		Map<String, String> row3 = reader.readLine();
		Assertions.assertNotNull(row3);
		Assertions.assertEquals(3, row3.size(), "row has too many columns");
		Assertions.assertEquals("un", row3.get("ATTR1"));
		Assertions.assertEquals("deux", row3.get("ATTR2"));
		Assertions.assertEquals("trois", row3.get("ATTR3"));

		// eof
		Map<String, String> row4 = reader.readLine();
		Assertions.assertNull(row4);

		reader.close();

		new File("testfile.csv").delete();
	}

	@Test
	public void throwsOnMalformedRow() throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter("testfile_malformed.csv"));
		writer.write("A;B\n1;2\n3\n");
		writer.close();

		try (CSVReader reader = new CSVReader("testfile_malformed.csv", ";")) {
			Map<String, String> row1 = reader.readLine();
			Assertions.assertNotNull(row1);
			Assertions.assertThrows(java.io.IOException.class, reader::readLine);
		}

		new File("testfile_malformed.csv").delete();
	}

	@Test
	public void throwsOnEmptyCsv() throws Exception {
		BufferedWriter writer = new BufferedWriter(new FileWriter("testfile_empty.csv"));
		writer.write("");
		writer.close();

		Assertions.assertThrows(java.io.IOException.class, () -> new CSVReader("testfile_empty.csv", ";"));

		new File("testfile_empty.csv").delete();
	}

}