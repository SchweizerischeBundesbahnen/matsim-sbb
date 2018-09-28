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

    public AbmDataReader()  {
    }

    public AbmData loadABMData(String pathAbmOutput)   {
        AbmData data = new AbmData();
        try (CSVReader reader = new CSVReader(pathAbmOutput, ",")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                // pid,tid,seq,otaz,dtaz,o_act,d_act,mode,deptime,arrtime,d_act_endtime,d_act_duration
                int id = (int) Double.parseDouble(map.get("pid"));
                Id<Person> pid = Id.createPersonId("P_" + id);
                int tid = (int) Double.parseDouble(map.get("tid"));
                int seq = (int) Double.parseDouble(map.get("seq"));
                int oTaz = (int) Double.parseDouble(map.get("orig_tzone"));
                int dTaz = (int) Double.parseDouble(map.get("dest_tzone"));
                String oAct = map.get("orig_act");
                String dAct = map.get("dest_act");
                String mode = map.get("mode");
                double deptime = Double.parseDouble(map.get("dep_time")) * 3600;
                double arrtime = Double.parseDouble(map.get("arr_time")) * 3600;
                double duration = 0.0;
                if (!dAct.equals("H"))   {
                    duration = Double.parseDouble(map.get("dest_act_dur")) * 3600;
                }
                data.addTrip(pid, tid, seq, oTaz,dTaz, oAct, dAct, mode, deptime, arrtime, duration);
            }
        } catch (IOException e) {
            log.warn(e);
        }
        return data;
    }
}
