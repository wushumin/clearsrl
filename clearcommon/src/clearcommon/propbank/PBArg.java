package clearcommon.propbank;

import java.util.ArrayList;
import java.util.Iterator;

import gnu.trove.TIntArrayList;
import clearcommon.treebank.*;

public class PBArg
{
	public  String            label;
	public  ArrayList<TBNode> a_nodes;
	ArrayList<int[]>  a_locs;
	
	public PBArg()
	{
		a_nodes = new ArrayList<TBNode>();
		a_locs = new ArrayList<int[]>();
	}
	
	public void addLoc(int[] loc)
	{
		a_locs.add(loc);
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
		for (int[] locs:a_locs)
			for (int aloc:locs)
				if (terminalIndex == aloc)
					return true;
		return false;
	}
	
   public boolean hasToken(int tokenIndex)
    {
        return false;
    }
	
	public void addNode(TBNode node)
	{
		a_nodes.add(node);
	}
	
	public ArrayList<TBNode> getNodes()
	{
		return a_nodes;
	}
	
	public TBNode[] getTerminalNodes()
	{
		ArrayList<TBNode> tnodes = new ArrayList<TBNode>();
		for (TBNode node:a_nodes)
			tnodes.addAll(node.getTerminalNodes());
		
		return tnodes.toArray(new TBNode[tnodes.size()]);
	}
	
	public TBNode[] getTokenNodes()
	{
		ArrayList<TBNode> tnodes = new ArrayList<TBNode>();
		for (TBNode node:a_nodes)
			tnodes.addAll(node.getTokenNodes());
		
		return tnodes.toArray(new TBNode[tnodes.size()]);
	}
	/*
	public int[] getTokenLocs()
	{
		TBNode[] nodes = getTokenNodes();
		int[] locs = new int[nodes.length];
		for (int i=0;i<locs.length;++i)
			locs[i] = nodes[i].terminalIndex;
		return locs;
	}
	*/
	public String toString()
	{
		StringBuilder str = new StringBuilder(label + ": ");
		
		for (TBNode node : a_nodes)
		{
			str.append(node.toString());
			str.append(" |");
		}
		
		for (int[] locs : a_locs)
		{
			for (int loc : locs)	str.append(" "+loc);
			str.append(" |");
		}
		
		return str.toString();
	}
	
	public boolean isEmpty()
	{
		return a_locs.size() == 0;
	}
}
