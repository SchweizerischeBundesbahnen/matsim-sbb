/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.csv;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @author mrieser / SBB
 */
public class CSVReaderTest {

    @Test
    public void read() throws Exception {
        BufferedWriter writer = new BufferedWriter(new FileWriter("testfile.csv"));
        writer.write("ATTR1;ATTR2;ATTR3\none;two;three\neins;zwei;drei\nun;deux;trois\n");
        writer.close();

        CSVReader reader = new CSVReader(new String[]{"ATTR1", "ATTR2", "ATTR3"});
        CSVReader.CSVIterator iter = reader.read("testfile.csv", ";");

        // header
        Assert.assertTrue(iter.hasNext());
        Map<String, String> row0 = iter.next();
        Assert.assertEquals("row has too many columns", 3, row0.size());
        Assert.assertEquals("ATTR1", row0.get("ATTR1"));
        Assert.assertEquals("ATTR2", row0.get("ATTR2"));
        Assert.assertEquals("ATTR3", row0.get("ATTR3"));

        // row1
        Assert.assertTrue(iter.hasNext());
        Map<String, String> row1 = iter.next();
        Assert.assertEquals("row has too many columns", 3, row1.size());
        Assert.assertEquals("one", row1.get("ATTR1"));
        Assert.assertEquals("two", row1.get("ATTR2"));
        Assert.assertEquals("three", row1.get("ATTR3"));

        // row2
        Assert.assertTrue(iter.hasNext());
        Map<String, String> row2 = iter.next();
        Assert.assertEquals("row has too many columns", 3, row2.size());
        Assert.assertEquals("eins", row2.get("ATTR1"));
        Assert.assertEquals("zwei", row2.get("ATTR2"));
        Assert.assertEquals("drei", row2.get("ATTR3"));

        // row3
        Assert.assertTrue(iter.hasNext());
        Map<String, String> row3 = iter.next();
        Assert.assertEquals("row has too many columns", 3, row3.size());
        Assert.assertEquals("un", row3.get("ATTR1"));
        Assert.assertEquals("deux", row3.get("ATTR2"));
        Assert.assertEquals("trois", row3.get("ATTR3"));

        // eof
        Assert.assertFalse(iter.hasNext());
        try {
            iter.next();
            Assert.fail("Expected an exception.");
        } catch (NoSuchElementException e) {
            // that was expected
        }

        iter.close();

        new File("testfile.csv").delete();
    }

}