package clearsrl;

import java.io.Serializable;
import java.util.BitSet;

import clearcommon.treebank.TBNode;

public class SRArg implements Comparable<SRArg>, Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -6336607317441187664L;
	
	boolean flat;
	TBNode node;
	BitSet tokenSet;
	String label;
	double  score;
	
	public double getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public SRArg(String label, TBNode node)
	{
        this(label, node, 0);
	}
    
    public SRArg(String label, TBNode node, double score)
    {
        this.node = node;
        tokenSet = new BitSet();
        for (TBNode aNode:node.getTokenNodes())
            tokenSet.set(aNode.getTokenIndex());
        this.label = label;
        this.score = score;  
    }
	
	public SRArg(String label, BitSet tokenSet)
	{
		this.node = null;
		this.tokenSet = (BitSet)tokenSet.clone();
		this.label = label;
		this.score = 0;
	}
	
	String getLabel()
	{
		return label;
	}
	
	BitSet getTokenSet()
	{
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
    public String toString()
	{
		return String.format("%s(%.3f): %s", label, score, node.toString());
	}
}
