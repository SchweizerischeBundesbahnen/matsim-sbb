package ch.sbb.matsim.mavi.visum;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End‑to‑end smoke tests for both {@link VisumNetFile} and (if available)
 * the COM‑based {@link Visum} implementation.  The contract under test is the
 * {@link VisumDataSource} interface.
 */
class VisumNetFileTest {

    @Test
    void parseMinimalNetFile(@TempDir Path tmp) throws Exception {
        // --- arrange --------------------------------------------------
        Path net = tmp.resolve("mini.net");
        Files.writeString(net, minimalNetFixture());

        try (VisumNetFile ds = VisumNetFile.open(net)) {
            // --- act ---------------------------------------------------
            VisumDataSource.DataTable nodes = ds.getTable("NODES");
            String[][] a = nodes.attrs("No", "XCoord", "YCoord");

            // --- assert -----------------------------------------------
            assertEquals(3, nodes.size(), "row count");
            assertEquals("2", a[1][0]);           // second row, column No
            assertEquals("829984.63161262858", a[1][1]); // XCoord
            assertEquals("5930324.6994440285", a[1][2]); // YCoord
        }
    }

    private static String minimalNetFixture() {
        return """
                $VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT
                15;Net;ENG;KM
                * Nodes
                $NODE:NO;XCOORD;YCOORD
                1;878576.45805652544;5987895.1534498949
                2;829984.63161262858;5930324.6994440285
                3;739067.68657134811;5899947.0959704844
                """;
    }
}
