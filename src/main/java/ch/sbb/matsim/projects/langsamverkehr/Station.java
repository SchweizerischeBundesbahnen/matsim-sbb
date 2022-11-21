package ch.sbb.matsim.projects.langsamverkehr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.matsim.api.core.v01.network.Node;

public class Station {

    final List<Node> nodesList;
    Map<Node, Integer> usageMap = new HashMap<>();
    int count = 0;

    public Station(List<Node> nodes) {
        this.nodesList = nodes;
        for (Node node : nodesList) {
            usageMap.put(node, 0);
        }
    }

    void useSation(Node node) {
        usageMap.put(node, usageMap.get(node)+1);
        count++;
    }

}
