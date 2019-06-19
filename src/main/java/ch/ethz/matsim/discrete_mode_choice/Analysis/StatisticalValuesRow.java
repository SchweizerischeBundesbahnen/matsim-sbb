package ch.ethz.matsim.discrete_mode_choice.Analysis;

import java.util.Collections;
import java.util.List;

public class StatisticalValuesRow {
    public String name;
    public Integer count;
    public Double min;
    public Double max;
    public Double mean;
    public Double variance;
    public Double stdDev;
    public Double median;

    public StatisticalValuesRow(String name, List<Double> list) {
        this.name = name;
        if (list.isEmpty()) {
            this.name += "IsEmpty";
            return;
        }
        count = list.size();
        min = list.stream().mapToDouble(a -> a).min().getAsDouble();
        max = list.stream().mapToDouble(a -> a).max().getAsDouble();
        mean = list.stream().mapToDouble(a -> a).average().getAsDouble();
        variance = list.stream().mapToDouble(val -> (val - mean) * (val - mean)).sum() / (count - 1);
        stdDev = Math.sqrt(variance);
        Collections.sort(list);
        median = count % 2 == 0 ? (list.get((count / 2) - 1) + list.get(count / 2)) / 2.0 : list.get(count / 2);
    }
}