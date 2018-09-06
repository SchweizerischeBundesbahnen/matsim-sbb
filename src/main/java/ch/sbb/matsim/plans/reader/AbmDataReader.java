package ch.sbb.matsim.plans.reader;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.abm.AbmData;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.io.IOException;
import java.util.Map;

public class AbmDataReader {

    private static final Logger log = Logger.getLogger(AbmDataReader.class);
    private final String pathAbmOutput;

    public AbmDataReader(String pathAbmOutput)  {
        this.pathAbmOutput = pathAbmOutput;
    }

    public AbmData loadABMData()   {
        AbmData data = new AbmData();
        try (CSVReader reader = new CSVReader(this.pathAbmOutput, ",")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                // pid,tid,seq,otaz,dtaz,o_act,d_act,mode,deptime,arrtime,d_act_endtime,d_act_duration
                Id<Person> pid = Id.createPersonId((int) Double.parseDouble(map.get("pid")));
                int tid = (int) Double.parseDouble(map.get("tid"));
                int seq = (int) Double.parseDouble(map.get("seq"));
                int oTaz = (int) Double.parseDouble(map.get("otaz"));
                int dTaz = (int) Double.parseDouble(map.get("dtaz"));
                String oAct = map.get("o_act");
                String dAct = map.get("d_act");
                String mode = map.get("mode");
                double deptime = Double.parseDouble(map.get("deptime")) * 3600;
                double arrtime = Double.parseDouble(map.get("arrtime")) * 3600;
                double duration = 0.0;
                if (!dAct.equals("H"))   {
                    duration = Double.parseDouble(map.get("d_act_duration")) * 3600;
                }
                data.addTrip(pid, tid, seq, oTaz,dTaz, oAct, dAct, mode, deptime, arrtime, duration);
            }
        } catch (IOException e) {
            log.warn(e);
        }
        return data;
    }
}
