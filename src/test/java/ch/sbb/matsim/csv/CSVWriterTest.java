/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.csv;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author mrieser / SBB
 */
public class CSVWriterTest {

    @Test
    public void testWrite() throws IOException {
        String header = "This is a\nrandom file header\n\n";
        String[] columns = new String[] {"Id", "ATTR1", "ATTR2"};
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        CSVWriter writer = new CSVWriter(header, columns, stream);
        writer.set("Id", "1");
        writer.set("ATTR1", "eins");
        writer.set("ATTR2", "zwei");
        writer.writeRow();

        writer.set("ATTR1", "un");
        writer.set("ATTR2", "deux");
        writer.set("Id", "2");
        writer.writeRow();

        writer.set("ATTR2", "due");
        writer.set("Id", "3");
        writer.set("ATTR1", "uno");
        writer.writeRow();

        writer.set("Id", "4");
        writer.set("ATTR1", "one");
        writer.writeRow();

        writer.set("Id", "5");
        writer.set("ATTR2", "two");
        writer.writeRow();

        writer.close();

        String result = stream.toString();

        Assert.assertEquals("This is a\nrandom file header\n\nId;ATTR1;ATTR2\n1;eins;zwei\n2;un;deux\n3;uno;due\n4;one;\n5;;two\n", result);
    }
}
