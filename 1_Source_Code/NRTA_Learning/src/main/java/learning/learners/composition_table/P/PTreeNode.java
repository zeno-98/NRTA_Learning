package learning.learners.composition_table.P;

import language.actions.RTARegionAction;
import lombok.Getter;
import ta.Action;

import java.util.*;

public class PTreeNode {
    @Getter
    private final RTARegionAction timedAction;
    @Getter
    private final Map<Action, Set<PTreeNode>> childrenNodesMap;

    public PTreeNode(RTARegionAction timedAction) {
        this.timedAction = timedAction;
        this.childrenNodesMap = new HashMap<>();
    }

    public Action action() {
        return this.timedAction.getAction();
    }

    public void addChild(PTreeNode node) {
        Action action = node.timedAction.getAction();
        if (this.childrenNodesMap.containsKey(action)) {
            this.childrenNodesMap.get(action).add(node);
        } else {
            Set<PTreeNode> node_set = new LinkedHashSet<>();
            node_set.add(node);
            this.childrenNodesMap.put(action, node_set);
        }
    }

    public Set<PTreeNode> getNodeSet(Action action) {
        return this.childrenNodesMap.get(action);
    }

    /******
     * Find child node
     * @param timedAction
     * @return
     */
    public PTreeNode seekChild(RTARegionAction timedAction) {
        Action action = timedAction.getAction();
        if (!this.childrenNodesMap.containsKey(action)) {
            return null;
        }
        Set<PTreeNode> childrenNodes = this.childrenNodesMap.get(action);
        for (PTreeNode node : childrenNodes) {
            RTARegionAction recordedTimedAction = node.getTimedAction();
            if (timedAction.equals(recordedTimedAction)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        if (this.timedAction == null) {
            return "null";
        }
        return this.timedAction.toString();
    }
}
