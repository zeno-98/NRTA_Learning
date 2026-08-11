package learning.learners.composition_table.P;

import language.actions.RTARegionAction;
import language.words.LogicalTimedWord;
import language.words.RTARegionWord;
import lombok.Getter;
import ta.*;

import java.util.*;
import tools.FormatOutput.ExactRationalFormat;

@Getter
public class PTree {
    private final Set<Action> Sigma;
    private final PTreeNode root;
    private final List<RTARegionWord> leafwordList;
    private final TA targetTA;

    public PTree(Set<Action> Sigma){
        this(Sigma, null);
    }

    public PTree(Set<Action> Sigma, TA targetTA) {
        this.Sigma = Sigma;
        this.root = new PTreeNode(null);
        this.leafwordList = new LinkedList<>();
        this.targetTA = targetTA;
    }

    public void add_P(RTARegionWord P) {
        this.add_P(P, P);
    }

    public void add_P(RTARegionWord logicalP, RTARegionWord P){
        if (P == null) {
            return;
        }
        PTreeNode currentNode = this.root;
        for (int i = 0; i < P.getLength(); i++) {
            RTARegionAction timedAction = P.getTimedAction(i);
            PTreeNode nextNode = currentNode.seekChild(timedAction);
            if (nextNode == null) {
                PTreeNode branchHead = this.build_branch(P, i);
                currentNode.addChild(branchHead);
                break;
            }
            currentNode = nextNode;
        }
        this.addWordList(P);
    }

    // Compatibility placeholder for legacy callers.
    public void add_P(LogicalTimedWord logicalP) {
    }

    // Compatibility placeholder for legacy callers.
    public void add_P(LogicalTimedWord logicalP, Map<Clock, Value> Kmap) {
    }

    public PTreeNode build_branch(RTARegionWord timedWord, int startIndex){
        PTreeNode head = new PTreeNode(timedWord.getTimedAction(startIndex));
        PTreeNode last = head;
        for (int i = startIndex + 1; i < timedWord.getLength(); i++) {
            PTreeNode node = new PTreeNode(timedWord.getTimedAction(i));
            last.addChild(node);
            last = node;
        }
        return head;
    }

    public boolean containsAction(Action action){
        return this.Sigma.contains(action);
    }

    public boolean containsWord(RTARegionWord word) {
        if (word == null) {
            return false;
        }
        PTreeNode current = this.root;
        for (int i = 0; i < word.getLength(); i++) {
            PTreeNode next = current.seekChild(word.getTimedAction(i));
            if (next == null) {
                return false;
            }
            current = next;
        }
        return true;
    }

    // Compatibility placeholder for old call chain.
    public LogicalTimedWord findLogicalWord(LogicalTimedWord logicalTimedWord) {
        return null;
    }

    public RTA getPassiveLearnedRTA(String name, Clock clock) {
        RTA hypothesis = new RTA(name);
        hypothesis.setSigma(this.Sigma);
        hypothesis.addClock(clock);
        Map<PTreeNode, Location> node2Loc = new LinkedHashMap<>();

        Deque<PTreeNode> queue = new ArrayDeque<>();
        queue.add(this.root);
        int locIdx = 0;
        while (!queue.isEmpty()) {
            PTreeNode node = queue.removeFirst();
            if (!node2Loc.containsKey(node)) {
                Location loc = new Location("q" + locIdx++, true, hypothesis);
                node2Loc.put(node, loc);
                hypothesis.addLocation(loc);
                if (node == this.root) {
                    hypothesis.setQ0(loc);
                }
            }
            List<Map.Entry<Action, Set<PTreeNode>>> orderedEntries = new ArrayList<>(node.getChildrenNodesMap().entrySet());
            orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
            for (Map.Entry<Action, Set<PTreeNode>> entry : orderedEntries) {
                List<PTreeNode> orderedChildren = new ArrayList<>(entry.getValue());
                orderedChildren.sort(Comparator.comparing(this::timedActionKey));
                queue.addAll(orderedChildren);
            }
        }

        Set<PTreeNode> visited = new HashSet<>();
        queue.add(this.root);
        visited.add(this.root);
        while (!queue.isEmpty()) {
            PTreeNode node = queue.removeFirst();
            Location source = node2Loc.get(node);
            List<Map.Entry<Action, Set<PTreeNode>>> orderedEntries = new ArrayList<>(node.getChildrenNodesMap().entrySet());
            orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
            for (Map.Entry<Action, Set<PTreeNode>> entry : orderedEntries) {
                List<PTreeNode> orderedChildren = new ArrayList<>(entry.getValue());
                orderedChildren.sort(Comparator.comparing(this::timedActionKey));
                for (PTreeNode child : orderedChildren) {
                    RTARegionAction rta = child.getTimedAction();
                    Action action = rta.getAction();
                    Constraint guard = this.buildGuard(clock, rta);
                    Location target = node2Loc.get(child);
                    Transition trans = new Transition(source, action, guard, Set.of(clock), target);
                    hypothesis.addTransition(trans);
                    if (visited.add(child)) {
                        queue.addLast(child);
                    }
                }
            }
        }

        return hypothesis;
    }

    private Constraint buildGuard(Clock clock, RTARegionAction action) {
        Constraint guard = new Constraint();
        if (action.isLowerClosed()) {
            guard.addAtomConstraint(AtomConstraint.generateGe(clock, action.getLower()));
        } else {
            guard.addAtomConstraint(AtomConstraint.generateGt(clock, action.getLower()));
        }
        if (action.isUpperClosed()) {
            guard.addAtomConstraint(AtomConstraint.generateLe(clock, action.getUpper()));
        } else {
            guard.addAtomConstraint(AtomConstraint.generateLt(clock, action.getUpper()));
        }
        return guard;
    }

    @Override
    public String toString() {
        return "PTree{root=" + this.root + ", leafwordListSize=" + this.leafwordList.size() + "}";
    }

    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PTree Summary").append('\n');
        sb.append("  sigmaSize=").append(this.Sigma.size())
                .append(", longestLeafWords=").append(this.leafwordList.size())
                .append('\n');
        sb.append("PTree Structure").append('\n');
        sb.append("  root").append('\n');
        List<PTreeNode> children = this.sortedChildren(this.root);
        for (int i = 0; i < children.size(); i++) {
            boolean isLast = (i == children.size() - 1);
            this.appendPrettyNode(sb, children.get(i), "", isLast);
        }
        if (children.isEmpty()) {
            sb.append("  (empty)").append('\n');
        }

        sb.append("Longest Leaf Words").append('\n');
        if (this.leafwordList.isEmpty()) {
            sb.append("  (none)").append('\n');
        } else {
            for (int i = 0; i < this.leafwordList.size(); i++) {
                sb.append("  ").append(i + 1).append(". ")
                        .append(ExactRationalFormat.regionWord(this.leafwordList.get(i))).append('\n');
            }
        }
        return sb.toString();
    }

    public List<RTARegionWord> getLongestLeafWord() {
        return new ArrayList<>(this.leafwordList);
    }

    /** Node table for deterministic reconstruction of the P-tree. */
    public String toNodesCsv() {
        GraphExport graph = this.exportGraph();
        StringBuilder output = new StringBuilder("node_id,parent_id,depth,action,interval\n");
        for (PTreeNode node : graph.nodes) {
            PTreeNode parent = graph.parents.get(node);
            if (node == this.root) {
                output.append("\"n0\",\"\",\"0\",\"ROOT\",\"\"\n");
                continue;
            }
            RTARegionAction action = node.getTimedAction();
            String interval = ExactRationalFormat.interval(action);
            output.append(this.csvRow(
                    graph.ids.get(node),
                    graph.ids.get(parent),
                    Integer.toString(graph.depths.get(node)),
                    action.getAction().getName(),
                    interval));
        }
        return output.toString();
    }

    /** Edge table paired with {@link #toNodesCsv()}. */
    public String toEdgesCsv() {
        GraphExport graph = this.exportGraph();
        StringBuilder output = new StringBuilder(
                "source_id,target_id,action,lower,lower_closed,upper,upper_closed\n");
        for (PTreeNode node : graph.nodes) {
            if (node == this.root) {
                continue;
            }
            RTARegionAction action = node.getTimedAction();
            output.append(this.csvRow(
                    graph.ids.get(graph.parents.get(node)),
                    graph.ids.get(node),
                    action.getAction().getName(),
                    ExactRationalFormat.value(action.getLower()),
                    Boolean.toString(action.isLowerClosed()),
                    ExactRationalFormat.value(action.getUpper()),
                    Boolean.toString(action.isUpperClosed())));
        }
        return output.toString();
    }

    private GraphExport exportGraph() {
        List<PTreeNode> nodes = new ArrayList<>();
        Map<PTreeNode, String> ids = new LinkedHashMap<>();
        Map<PTreeNode, PTreeNode> parents = new LinkedHashMap<>();
        Map<PTreeNode, Integer> depths = new LinkedHashMap<>();
        Deque<PTreeNode> queue = new ArrayDeque<>();
        queue.add(this.root);
        ids.put(this.root, "n0");
        depths.put(this.root, 0);
        while (!queue.isEmpty()) {
            PTreeNode node = queue.removeFirst();
            nodes.add(node);
            for (PTreeNode child : this.sortedChildren(node)) {
                if (!ids.containsKey(child)) {
                    ids.put(child, "n" + ids.size());
                    parents.put(child, node);
                    depths.put(child, depths.get(node) + 1);
                    queue.addLast(child);
                }
            }
        }
        return new GraphExport(nodes, ids, parents, depths);
    }

    private String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append('"').append(values[i].replace("\"", "\"\"")).append('"');
        }
        return row.append('\n').toString();
    }

    private record GraphExport(
            List<PTreeNode> nodes,
            Map<PTreeNode, String> ids,
            Map<PTreeNode, PTreeNode> parents,
            Map<PTreeNode, Integer> depths) {}

    private void addWordList(RTARegionWord newWord) {
        this.leafwordList.removeIf(oldWord -> oldWord.isPrefix(newWord));
        this.leafwordList.add(newWord);
        this.leafwordList.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
    }

    private String timedActionKey(PTreeNode node) {
        RTARegionAction action = node.getTimedAction();
        return action.getAction().getName()
                + "|" + ExactRationalFormat.value(action.getLower()) + ":" + action.isLowerClosed()
                + "|" + ExactRationalFormat.value(action.getUpper()) + ":" + action.isUpperClosed();
    }

    private void appendPrettyNode(StringBuilder sb, PTreeNode node, String prefix, boolean isLast) {
        sb.append(prefix).append(isLast ? "  └─ " : "  ├─ ").append(this.prettyNodeText(node)).append('\n');
        List<PTreeNode> children = this.sortedChildren(node);
        String nextPrefix = prefix + (isLast ? "     " : "  │  ");
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            this.appendPrettyNode(sb, children.get(i), nextPrefix, childIsLast);
        }
    }

    private List<PTreeNode> sortedChildren(PTreeNode node) {
        List<PTreeNode> orderedChildren = new ArrayList<>();
        List<Map.Entry<Action, Set<PTreeNode>>> orderedEntries = new ArrayList<>(node.getChildrenNodesMap().entrySet());
        orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
        for (Map.Entry<Action, Set<PTreeNode>> entry : orderedEntries) {
            List<PTreeNode> nodes = new ArrayList<>(entry.getValue());
            nodes.sort(Comparator.comparing(this::timedActionKey));
            orderedChildren.addAll(nodes);
        }
        return orderedChildren;
    }

    private String prettyNodeText(PTreeNode node) {
        RTARegionAction action = node.getTimedAction();
        return action.getAction().getName() + " " + ExactRationalFormat.interval(action);
    }
}
