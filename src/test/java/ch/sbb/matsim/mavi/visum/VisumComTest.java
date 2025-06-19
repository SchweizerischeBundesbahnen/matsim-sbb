package ch.sbb.matsim.mavi.visum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

class VisumComTest {

    @Test
    @Disabled
    void smokeComBackend() throws Exception {
        int version = 24;

        String cwd = System.getProperty("user.dir");
        String path = cwd + "/test/input/ch/sbb/matsim/mavi/minirail.ver";

        try (Visum visum = Visum.connect(version)) {
            visum.load(path);
            VisumDataSource.DataTable links = visum.getTable("Links");
            assertTrue(links.size() > 0, "Links table should not be empty");
            // pull a couple of columns – no exception expected
            String[][] demo = links.attrs("No", "Length");
            assertEquals(links.size(), demo.length);
        }
    }

}
