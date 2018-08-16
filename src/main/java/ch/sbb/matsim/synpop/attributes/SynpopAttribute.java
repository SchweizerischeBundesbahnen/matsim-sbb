package ch.sbb.matsim.synpop.attributes;

public class SynpopAttribute {

    private String name;

    public String getName() {
        return name;
    }

    public String getSqlType() {
        return sqlType;
    }

    private String sqlType;

    public SynpopAttribute(String name, String sqlType){
        this.name = name;
        this.sqlType = sqlType;

    }
}
