package ch.sbb.matsim.plans.abm;

public class AbmTrip {

    private int oTZone;
    private int dTZone;
    private String oAct;
    private String dAct;
    private String mode;
    private double deptime;
    private double arrtime;
    private double dActDuration;

    public AbmTrip(int oTZone, int dTZone, String oAct, String dAct, String mode,
                    double deptime, double arrtime, double dActDuration) {
        this.oTZone = oTZone;
        this.dTZone = dTZone;
        this.oAct = oAct;
        this.dAct = dAct;
        this.mode = mode;
        this.deptime = deptime;
        this.arrtime = arrtime;
        this.dActDuration = dActDuration;
    }

    public String getMode() {
        return this.mode;
    }

    public double getDepTime()    {
        return this.deptime;
    }

    public String getDestAct()  {
        return this.dAct;
    }

    public double getTravelTime()   {
        double traveltime = this.arrtime - this.deptime;
        return traveltime;
    }

    public int getDestTZone()   {
        return dTZone;
    }

    public double getDestActDuration()  {
        return dActDuration;
    }
}
