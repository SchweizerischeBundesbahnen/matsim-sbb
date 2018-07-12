package ch.sbb.matsim.synpop.zoneAggregator;

import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

public class Zone<T> {
    private SimpleFeature feature;
    private ArrayList<T> data;

    public Zone(SimpleFeature feature){
        this.feature = feature;
        this.data = new ArrayList<T>();
    }

    public String getId(){
        if(this.feature == null) return null;
        return this.feature.getID();
    }

    void addItem(T item){
        this.data.add(item);
    }

    public int count(){
        return this.data.size();
    }

    public ArrayList<T> getData(){
        return this.data;
    }
}
