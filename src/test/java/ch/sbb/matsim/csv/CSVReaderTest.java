/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.csv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

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
		Assert.assertNotNull(row0);
		Assert.assertEquals("row has too many columns", 3, row0.size());
		Assert.assertEquals("ATTR1", row0.get("ATTR1"));
		Assert.assertEquals("ATTR2", row0.get("ATTR2"));
		Assert.assertEquals("ATTR3", row0.get("ATTR3"));

		// row1
		Map<String, String> row1 = reader.readLine();
		Assert.assertNotNull(row1);
		Assert.assertEquals("row has too many columns", 3, row1.size());
		Assert.assertEquals("one", row1.get("ATTR1"));
		Assert.assertEquals("two", row1.get("ATTR2"));
		Assert.assertEquals("three", row1.get("ATTR3"));

		// row2
		Map<String, String> row2 = reader.readLine();
		Assert.assertNotNull(row2);
		Assert.assertEquals("row has too many columns", 3, row2.size());
		Assert.assertEquals("eins", row2.get("ATTR1"));
		Assert.assertEquals("zwei", row2.get("ATTR2"));
		Assert.assertEquals("drei", row2.get("ATTR3"));

		// row3
		Map<String, String> row3 = reader.readLine();
		Assert.assertNotNull(row3);
		Assert.assertEquals("row has too many columns", 3, row3.size());
		Assert.assertEquals("un", row3.get("ATTR1"));
		Assert.assertEquals("deux", row3.get("ATTR2"));
		Assert.assertEquals("trois", row3.get("ATTR3"));

		// eof
		Map<String, String> row4 = reader.readLine();
		Assert.assertNull(row4);

		reader.close();

		new File("testfile.csv").delete();
	}

}