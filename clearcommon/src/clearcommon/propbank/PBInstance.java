package clearcommon.propbank;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import clearcommon.treebank.*;

public class PBInstance implements Comparable<PBInstance>
{
	TBNode      predicateNode;
	String      rolesetId;
	PBArg[]     args;
	TBTree      tree;

	public PBInstance()
	{
		args = null;
	}
	
	public TBNode getPredicate()
	{
	    return predicateNode;
	}
	
	public String getRoleset()
	{
	    return rolesetId;
	}
	
	public TBTree getTree()
	{
	    return tree;
	}
	
	public PBArg[] getArgs()
	{
		return args;
	}

	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(rolesetId+": ");
		
		str.append(predicateNode.getWord()+"\n");
		
		for (PBArg arg: args)
			str.append(arg+"\n");
		
		return str.toString();
	}

	@Override
	public int compareTo(PBInstance rhs) {
		int ret = tree.getFilename()==null?0:tree.getFilename().compareTo(rhs.tree.getFilename());
		if (ret!=0) return ret;
		
		ret = tree.getIndex()==rhs.tree.getIndex()?0:(tree.getIndex()<rhs.tree.getIndex()?-1:1);
		if (ret!=0) return ret;
		
		return predicateNode.getTerminalIndex()==rhs.predicateNode.getTerminalIndex()?0:(predicateNode.getTerminalIndex()<rhs.predicateNode.getTerminalIndex()?-1:1);
	}
}
