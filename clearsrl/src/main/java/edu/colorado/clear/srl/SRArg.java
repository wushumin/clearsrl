package edu.colorado.clear.srl;

import java.io.Serializable;
import java.util.BitSet;

import edu.colorado.clear.common.treebank.TBNode;

public class SRArg implements Comparable<SRArg>, Serializable{
    /**
     * 
     */
    private static final long serialVersionUID = -6336607317441187664L;
    
    public static final String NOT_ARG="!ARG";
    
    boolean flat;
    TBNode node;
    BitSet tokenSet;
    String label;
    String auxLabel;
    double score;
    double[] scores; 
    
    public double getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public SRArg(String label, TBNode node) {
        this(label, node, 1);
    }
    
    public SRArg(String label, TBNode node, double score) {
    	this(label, node, score, null);
    }
    
    public SRArg(String label, TBNode node, double score, double[] scores) {
        this.node = node;
        tokenSet = new BitSet();
        for (TBNode aNode:node.getTokenNodes())
            tokenSet.set(aNode.getTokenIndex());
        this.label = label;
        this.score = score;
        this.scores = scores;
    }
    
    public SRArg(String label, BitSet tokenSet)
    {
        this.node = null;
        this.tokenSet = (BitSet)tokenSet.clone();
        this.label = label;
        this.score = 0;
    }
    
    public boolean isLabelArg() {
    	return !NOT_ARG.equals(label);
    }
    
    public String getLabel() {
        return label;
    }
    
    public String getAuxLabel() {
    	return auxLabel;
    }
    
    public BitSet getTokenSet() {
        return (BitSet)tokenSet.clone();
    }

    public boolean isPredicate() {
        return label.equals("rel");
    }
    
    @Override
    public int compareTo(SRArg arg0) {
        return tokenSet.nextSetBit(0)-arg0.tokenSet.nextSetBit(0);
    }
    
    @Override
    public String toString() {
        return String.format("%s(%.3f): %s", label, score, node.toString());
    }
}
