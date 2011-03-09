package clearcommon.propbank;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import clearcommon.treebank.*;

public class PBArg implements Comparable<PBArg>, Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 3167382729194529990L;
    
    static final String LABEL_PATTERN = "(R-)?((A[A-Z]*\\d)(\\-[A-Za-z]+)?|[A-Za-z]+(\\-[A-Za-z]+)?)";
    static final String ARG_PATTERN = "\\d+:\\d+([\\*,;&]\\d+:\\d+)*-[A-Za-z].*";
    
    static final PBArg[] NO_ARGS = new PBArg[0];
    
	String       label;
	TBNode       node;
	PBArg        linkingArg;
	PBArg[]      nestedArgs;
	   
	TBNode[]     tokenNodes;

	BitSet       terminalSet;
	BitSet       tokenSet;

	public PBArg(String label)
	{
	    this.label = label;
	    node       = null;
	    linkingArg = null;
	    nestedArgs = NO_ARGS;
	}
	
	void processNodes() throws PBFormatException
	{  	    
	    //System.out.print(label+":");
	    //for (TBNode node:tokenNodes)
	    //    System.out.print(" "+node.toParse());
	    //System.out.print("\n");
	    
	    // link traces
	    List<TBNode> mainNodes = new ArrayList<TBNode>(Arrays.asList(tokenNodes));

	    TBNode traceNode;
	    boolean addedNodes;
	    do {
	        addedNodes = false;
    	    for (int i=0; i<mainNodes.size();++i)
    	    {
    	        if ((traceNode=mainNodes.get(i).getTrace())!=null)
    	        {
    	            boolean found = false;
    	            for (TBNode mainNode:mainNodes)
    	                if (mainNode == traceNode)
    	                {
    	                    found = true;
    	                    break;
    	                }
    	            // if it's a reference arg, make sure the trace node doesn't trace back to the main argument
    	            if (linkingArg!=null && traceNode==linkingArg.node)
    	                found = true;
    	            if (!found)
    	            {
    	                mainNodes.add(traceNode);
    	                addedNodes = true;
    	                break;
    	            }
    	        }
    	    }
	    } while (addedNodes);

	    // assign single node to represent this argument
	    node = null;
	    for (TBNode mainNode:mainNodes)
	        if (!mainNode.isEC() && !mainNode.getTokenSet().isEmpty())
	        {
	            if (node!=null)
	            {
	                StringBuilder builder = new StringBuilder();
	                for (TBNode node:mainNodes)
	                    builder.append("\n    "+node.toParse());
	                throw new PBFormatException(label+": multiple non-EC node detected"+builder.toString());
	            }
	            node = mainNode;
	        }
	    // We have an empty argument
	    if (node==null) node = mainNodes.get(0);
	    
	    // populate token nodes, etc
        terminalSet = node.getTerminalSet();
        tokenSet = node.getTokenSet();
        
        for (PBArg nestedArg:nestedArgs)
            nestedArg.processNodes();
        
        List<PBArg> nestedArgList = new ArrayList<PBArg>(Arrays.asList(nestedArgs));
        for (int i=0; i<nestedArgList.size()-1; ++i)
            for (int j=i+1; j<nestedArgList.size();)
            {
                if (nestedArgList.get(i).node==nestedArgList.get(j).node)
                    nestedArgList.remove(j);
                else
                    ++j;
            }
        
        nestedArgs = nestedArgList.toArray(NO_ARGS);
        
        for (PBArg nestedArg:nestedArgs)
        {
            if (terminalSet.intersects(nestedArg.terminalSet))
            {
                StringBuilder builder = new StringBuilder();
                builder.append("\n    "+node.toParse());
                for (PBArg arg: nestedArgs)
                {
                    builder.append("\n    "+arg.node.toParse());
                }
                throw new PBFormatException(label+": terminal overlap detected"+builder.toString());
            }
            terminalSet.or(nestedArg.terminalSet);
            tokenSet.or(nestedArg.tokenSet);
        }
        Arrays.sort(nestedArgs);
        
	    List<TBNode>tNodes = node.getTokenNodes();
        for (PBArg nestedArg:nestedArgs)
            tNodes.addAll(Arrays.asList(nestedArg.tokenNodes));
        
        tokenNodes = tNodes.toArray(new TBNode[tNodes.size()]);
	}

	public boolean isLabel(String label)
	{
	    return this.label.equals(label);
	}
	
	public String getLabel() {
        return label;
    }
	
	public boolean isPredicate()
	{
		return label.equals("rel");
	}
	
	public boolean isMainArg()
	{
		return isPredicate() || label.equals("ARG0") || label.equals("ARG1");
	}
	
	public boolean hasTerminal(int terminalIndex)
	{
	    return terminalSet.get(terminalIndex);
	}
	
    public boolean hasToken(int tokenIndex)
    {
        return false;
    }
	
	public TBNode getNode()
	{
		return node;
	}
	
	public PBArg[] getNestedArgs()
	{
	    return nestedArgs;
	}
	
	public TBNode[] getTerminalNodes()
	{
		ArrayList<TBNode> tnodes = new ArrayList<TBNode>();
		tnodes.addAll(node.getTerminalNodes());

		for (PBArg nestedArg:nestedArgs)
			tnodes.addAll(nestedArg.node.getTerminalNodes());
		
		return tnodes.toArray(new TBNode[tnodes.size()]);
	}
	
	public TBNode[] getTokenNodes()
	{
	    return tokenNodes;
	}

	public BitSet getTerminalSet() {
        return (BitSet)terminalSet.clone();
    }

    public BitSet getTokenSet() {
        return (BitSet)tokenSet.clone();
    }

    @Override
    public String toString()
	{
		StringBuilder str = new StringBuilder(label + ": ");
		
		if (tokenNodes.length>0)
		{
		    str.append(tokenNodes[0].getWord()+" ");
	        for (int i=1; i<tokenNodes.length; ++i)
	        {
	            if (tokenNodes[i-1].getTokenIndex()+1!=tokenNodes[i].getTokenIndex()) 
	                str.append("|");
	            str.append(tokenNodes[i].getWord()+" ");
	        }
		}
		return str.toString();
	}
	
	public boolean isEmpty()
	{
		return tokenSet.isEmpty();
	}

    @Override
    public int compareTo(PBArg o) {
        return terminalSet.nextSetBit(0)-o.terminalSet.nextSetBit(0);
    }
}
