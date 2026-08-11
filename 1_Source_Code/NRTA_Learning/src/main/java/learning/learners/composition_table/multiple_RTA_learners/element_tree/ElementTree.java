package learning.learners.composition_table.multiple_RTA_learners.element_tree;

import com.microsoft.z3.BoolExpr;
import language.actions.RTARegionAction;
import language.words.RTARegionWord;
import learning.learners.composition_table.TriBool;
import lombok.Getter;
import ta.Action;
import ta.Clock;
import tools.ExtendSolver;

import java.util.*;

public class ElementTree implements Iterable<ElementNode> {
    private final ExtendSolver slv;
    private final Clock clock;
    @Getter
    private final ElementNode root;
    @Getter
    private final Set<ElementNode> nodes;

    public ElementTree(ExtendSolver slv, Clock clock){
        this.slv = slv;
        this.clock = clock;
        this.nodes = new HashSet<>();
        this.root = new ElementNode(new RTARegionWord(clock), null, slv);
        this.root.setAccept(TriBool.tri_true);
        this.nodes.add(this.root);
    }

    public Set<ElementNode> getAllNodes() {
        LinkedList<ElementNode> queue = new LinkedList<>();
        Set<ElementNode> nodes = new HashSet<>();
        queue.add(this.root);
        while (!queue.isEmpty()) {
            ElementNode curNode = queue.pop();
            nodes.add(curNode);
            for (List<ElementNode> childrenOfCertainAction : curNode.getChildren().values()) {
                queue.addAll(childrenOfCertainAction);
            }
        }
        if (nodes.size() != this.nodes.size()) {
            throw new RuntimeException("elementTree nodes do not contain all nodes");
        }
        return nodes;
    }

    private ElementNode addNode(RTARegionWord lw, ElementNode root){
        ElementNode current = root;
        for(int i=0;i<lw.getLength();i++){
            RTARegionAction la = lw.getTimedAction(i);
            current = current.addChild(la);
            this.nodes.add(current);
        }
        return current;
    }

    public ElementNode addNode(RTARegionWord lw){
        return this.addNode(lw, this.root);
    }

    public ElementNode addNode(RTARegionWord prefix, RTARegionWord suffix){
        ElementNode temp = this.addNode(prefix);
        return this.addNode(suffix, temp);
    }

    private ElementNode seekNode(RTARegionWord lw, ElementNode root){
        ElementNode current = root;
        ElementNode next;
        for(int i=0;i< lw.getLength();i++){
            RTARegionAction la = lw.getTimedAction(i);
            next = current.seekChild(la);
            if(next==null) return null;
            else current = next;
        }
        return current;
    }

    public ElementNode seekNode(RTARegionWord lw){
        return this.seekNode(lw, this.root);
    }

    public ElementNode seekNode(RTARegionWord prefix, RTARegionWord suffix){
        ElementNode temp = this.seekNode(prefix);
        if(temp==null)
            return null;
        else
            return this.seekNode(suffix, temp);
    }

    public BoolExpr generateSinkConstraint(){
        List<BoolExpr> constraints = new LinkedList<>();
        this.root.generateSinkConstraintCore(constraints);
        if(constraints.isEmpty())
            return this.slv.mkTrue();
        else
            return this.slv.mkArrayAnd(constraints.toArray(new BoolExpr[0]));
    }

    public ElementNode updateTreeByCtx(RTARegionWord ctxWord, boolean isPosOrNeg){
        ElementNode current = this.root;
        int i = 0;
        for(RTARegionAction la : ctxWord){
            i++;
            current = current.addChild(la);
            if(i != ctxWord.getLength() || isPosOrNeg) {
                current.setAccept(TriBool.tri_true);
            }
        }
        if(!isPosOrNeg)
            current.setAccept(TriBool.tri_false);
        return current;
    }

    public ElementRecord exportRecord() {
        List<RTARegionWord> unknownFrontier = new LinkedList<>();
        List<RTARegionWord> falseFrontier = new LinkedList<>();
        for (ElementNode node : this.getAllNodesInStableOrder()) {
            if (node.isRoot()) {
                continue;
            }
            if (node.isAccept() == TriBool.unknown && this.isFrontierNode(node, TriBool.unknown)) {
                unknownFrontier.add(node.getLw().copy());
            } else if (node.isAccept() == TriBool.tri_false && this.isFrontierNode(node, TriBool.tri_false)) {
                falseFrontier.add(node.getLw().copy());
            }
        }
        return new ElementRecord(unknownFrontier, falseFrontier);
    }

    public void rollback(ElementRecord record, Set<RTARegionWord> certainNegativeCounterExamples) {
        this.pruneNonTrueNodes();

        List<RTARegionWord> unknownWords = new ArrayList<>(record.getUnknownFrontierWords());
        unknownWords.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
        for (RTARegionWord word : unknownWords) {
            ElementNode node = this.seekNode(word);
            if (node != null) {
                if (node.isAccept() == TriBool.tri_true) {
                    continue;
                }
                this.setSubTreeUnknown(node);
            } else {
                ElementNode newNode = this.addNode(word);
                if (newNode.isAccept() != TriBool.tri_true) {
                    this.setSubTreeUnknown(newNode);
                }
            }
        }

        List<RTARegionWord> falseWords = new ArrayList<>(record.getFalseFrontierWords());
        falseWords.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
        for (RTARegionWord word : falseWords) {
            ElementNode node = this.seekNode(word);
            if (node != null) {
                if (node.isAccept() == TriBool.tri_true) {
                    continue;
                }
                this.setSubTreeFalse(node);
            } else {
                ElementNode newNode = this.addNode(word);
                if (newNode.isAccept() != TriBool.tri_true) {
                    this.setSubTreeFalse(newNode);
                }
            }
        }

        List<RTARegionWord> certainNegWords = new ArrayList<>(certainNegativeCounterExamples);
        certainNegWords.sort(Comparator.comparingInt(RTARegionWord::getLength).thenComparing(RTARegionWord::toString));
        for (RTARegionWord negativeCtx : certainNegWords) {
            if (negativeCtx.isEmpty()) {
                continue;
            }
            this.updateTreeByCtx(negativeCtx, false);
        }

        this.root.setAccept(TriBool.tri_true);
    }

    private void pruneNonTrueNodes() {
        this.pruneNonTrueNodesCore(this.root);
        this.nodes.clear();
        this.nodes.addAll(this.getAllNodesInStableOrder());
    }

    private void pruneNonTrueNodesCore(ElementNode node) {
        Iterator<Map.Entry<Action, List<ElementNode>>> iterator = node.getChildren().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Action, List<ElementNode>> entry = iterator.next();
            List<ElementNode> keptChildren = new LinkedList<>();
            for (ElementNode child : entry.getValue()) {
                if (child.isAccept() == TriBool.tri_true) {
                    this.pruneNonTrueNodesCore(child);
                    keptChildren.add(child);
                } else {
                    this.removeSubTreeFromNodeSet(child);
                }
            }
            if (keptChildren.isEmpty()) {
                iterator.remove();
            } else {
                entry.setValue(keptChildren);
            }
        }
    }

    private void removeSubTreeFromNodeSet(ElementNode node) {
        Deque<ElementNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            ElementNode current = stack.pop();
            this.nodes.remove(current);
            for (List<ElementNode> children : current.getChildren().values()) {
                for (ElementNode child : children) {
                    stack.push(child);
                }
            }
        }
    }

    public Set<RTARegionWord> getLongestAcceptedWords() {
        List<ElementNode> leafNodes = this.getAllNodesInStableOrder().stream()
                .filter(ElementNode::isLeaf)
                .toList();
        Set<RTARegionWord> acceptedLogicalWordSet = new LinkedHashSet<>();
        for(ElementNode leafNode : leafNodes) {
            ElementNode curNode = leafNode;
            while (!curNode.isRoot() && curNode.isAccept() != TriBool.tri_true) {
                curNode = curNode.getParent();
            }
            acceptedLogicalWordSet.add(curNode.getLw());
        }
        return acceptedLogicalWordSet;
    }

    @Override
    public Iterator<ElementNode> iterator() {
        return this.getAllNodesInStableOrder().iterator();
    }

    private List<ElementNode> getAllNodesInStableOrder() {
        List<ElementNode> orderedNodes = new ArrayList<>();
        Deque<ElementNode> queue = new ArrayDeque<>();
        queue.add(this.root);
        while (!queue.isEmpty()) {
            ElementNode current = queue.removeFirst();
            orderedNodes.add(current);
            List<Map.Entry<Action, List<ElementNode>>> childrenEntries = new ArrayList<>(current.getChildren().entrySet());
            childrenEntries.sort(Comparator.comparing(entry -> entry.getKey().getName()));
            for (Map.Entry<Action, List<ElementNode>> entry : childrenEntries) {
                List<ElementNode> children = new ArrayList<>(entry.getValue());
                children.sort(Comparator.comparing(ElementNode::wordToString));
                queue.addAll(children);
            }
        }
        return orderedNodes;
    }

    private boolean isFrontierNode(ElementNode node, TriBool label) {
        ElementNode parent = node.getParent();
        while (parent != null) {
            if (parent.isAccept() == label) {
                return false;
            }
            parent = parent.getParent();
        }
        return true;
    }

    private void setSubTreeUnknown(ElementNode node) {
        if (node.isAccept() == TriBool.tri_true) {
            return;
        }
        node.setAcceptDirect(TriBool.unknown);
        for (List<ElementNode> children : node.getChildren().values()) {
            for (ElementNode child : children) {
                this.setSubTreeUnknown(child);
            }
        }
    }

    private void setSubTreeFalse(ElementNode node) {
        if (node.isAccept() == TriBool.tri_true) {
            return;
        }
        node.setAcceptDirect(TriBool.tri_false);
        for (List<ElementNode> children : node.getChildren().values()) {
            for (ElementNode child : children) {
                this.setSubTreeFalse(child);
            }
        }
    }

    public String fingerprintSummary() {
        int trueCnt = 0;
        int falseCnt = 0;
        int unknownCnt = 0;
        List<String> frontier = new ArrayList<>();
        for (ElementNode node : this.getAllNodesInStableOrder()) {
            TriBool accept = node.isAccept();
            if (accept == TriBool.tri_true) {
                trueCnt++;
            } else if (accept == TriBool.tri_false) {
                falseCnt++;
            } else {
                unknownCnt++;
            }
            if (!node.isRoot() && this.isFrontierNode(node, accept)) {
                frontier.add(node.wordToString() + ":" + accept);
            }
        }
        Collections.sort(frontier);
        String frontierJoined = String.join("|", frontier);
        return "nodes=" + this.nodes.size()
                + ",true=" + trueCnt
                + ",false=" + falseCnt
                + ",unknown=" + unknownCnt
                + ",frontierHash=" + Integer.toHexString(frontierJoined.hashCode());
    }
}
