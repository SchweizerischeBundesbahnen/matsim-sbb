package ch.sbb.matsim.rerouting;

import omx.OmxFile;

public class ReadOMXMatrciesDayDemand {


    public static void readOMXMatrciesDayDemand(String file){

        OmxFile omxFile = new OmxFile(file);
        omxFile.openNew(new int[]{2378,2378});

        System.out.println("Done");

    }

}
