package clearcommon.util;

import java.util.ArrayList;
import java.util.List;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

public class ECDependent {


    public ECDependent(TBNode predicate) {
        this(predicate, null, null);
    }
    
    public ECDependent(TBNode predicate, TBNode subject, TBNode object) {
        this.predicate = predicate;
        this.subject = subject;
        this.object = object;
    }
    
    public TBNode getPredicate() {
        return predicate;
    }

    public TBNode getSubject() {
        return subject;
    }

    public void setSubject(TBNode node) {
        this.subject = node;
    }

    public TBNode getObject() {
        return object;
    }

    public void setObject(TBNode node) {
        this.object = node;
    }
    
    public static List<TBNode> getECCandidates(TBTree tree) {
        List<TBNode> nodes = new ArrayList<TBNode>();
        for (TBNode node:tree.getTokenNodes()) {
            if (!node.getPOS().matches("V.*")) continue;
            TBNode vpAncestor = null;
            for (TBNode ancestor:node.getPathToAncestor(node.getConstituentByHead())) {
                if (ancestor.getPOS().equals("VP")) {
                    vpAncestor = ancestor;
                    break;
                }
            }
            if (vpAncestor==null) continue;
            nodes.add(node);
        }
        return nodes;
    }
    
    public static List<ECDependent> getDependents(List<TBNode> nodes) {
        List<ECDependent> labels = new ArrayList<ECDependent>(); 

        for (TBNode node:nodes) {
            ECDependent label = new ECDependent(node);
            for (TBNode dependent:node.getDependentNodes(true)) {
                if (dependent.isEC()) {
                    TBNode dependentParent = dependent.getParent();
                    if (dependentParent.hasFunctionTag("SBJ") && label.subject == null)
                        label.subject = dependent;
                    if (dependentParent.hasFunctionTag("OBJ") && label.object == null)
                        label.object = dependent;
                }
            }
            labels.add(label);
        }
        return labels;
    }
    
    public static List<ECDependent> getDependents(TBTree tree) {
        return getDependents(getECCandidates(tree));
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (subject!=null)
            builder.append("sbj-"+subject.getECType()+" ");
        builder.append(predicate.getWord());
        if (object!=null)
            builder.append(" obj-"+object.getECType());
        return builder.toString();
    }
    
    TBNode predicate;
    TBNode subject;
    TBNode object;
}
