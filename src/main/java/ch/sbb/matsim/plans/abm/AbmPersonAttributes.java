package ch.sbb.matsim.plans.abm;

public class AbmPersonAttributes {

    private int ageCat;
    private int emplPctCat;
    private int eduType;
    private int mobility;

    public AbmPersonAttributes(int ageCat, int emplPctCat, int eduType, int mobility) {
        this.ageCat = ageCat;
        this.emplPctCat = emplPctCat;
        this.eduType = eduType;
        this.mobility = mobility;
    }

    public int getAgeCat() {
        return ageCat;
    }

    public int getEmplPctCat() {
        return emplPctCat;
    }

    public int getEduType() {
        return eduType;
    }

    public int getMobility()    {
        return mobility;
    }

}
