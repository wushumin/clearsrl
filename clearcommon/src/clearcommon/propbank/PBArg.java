package clearcommon.propbank;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import gnu.trove.TIntArrayList;
import clearcommon.treebank.*;

public class PBArg
{
    public static final Pattern ARG_PATTERN = Pattern.compile("(([RC]-)?(A[A-Z]*\\d))(\\-[A-Za-z]+)?");
    
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
	    linkingArg = null;
	    nestedArgs = NO_ARGS;
	}
	
	boolean processNodes(List<TBNode> nodeList)
	{
	    ArrayList<TBNode> tnodes = new ArrayList<TBNode>();
        tnodes.addAll(node.getTokenNodes());
        
        for (PBArg nestedArg:nestedArgs)
            tnodes.addAll(nestedArg.node.getTokenNodes());
        
        tokenNodes = tnodes.toArray(new TBNode[tnodes.size()]);
        return true;
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
        return terminalSet;
    }

    public BitSet getTokenSet() {
        return tokenSet;
    }

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
}
