package ch.sbb.matsim.synpop.zoneAggregator;

import java.util.ArrayList;

public class Zone<T> {
    private final int zoneId;
    private ArrayList<T> data;

    public Zone(int id) {
        this.zoneId = id;
        this.data = new ArrayList<T>();
    }

    public int getId() {
        return this.zoneId;
    }

    void addItem(T item) {
        this.data.add(item);
    }

    public int count() {
        return this.data.size();
    }

    public ArrayList<T> getData() {
        return this.data;
    }
}
