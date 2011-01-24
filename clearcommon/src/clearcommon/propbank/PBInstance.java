package clearcommon.propbank;

import clearcommon.treebank.*;

public class PBInstance implements Comparable<PBInstance>
{
	TBNode      predicateNode;
	String      rolesetId;
	PBArg[]     args;
	PBArg[]     allArgs;
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
	
	/**
	 * Gets non empty (word) arguments 
	 * @return non empty (word) arguments 
	 */
	public PBArg[] getArgs()
	{
		return args;
	}
	
    /**
     * Gets all arguments, including ones without words
     * @return all arguments, including ones without words
     */
    public PBArg[] getAllArgs()
    {
        return allArgs;
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
		
		ret = tree.getIndex()-rhs.tree.getIndex();
		if (ret!=0) return ret;
		
		return predicateNode.getTerminalIndex()-rhs.predicateNode.getTerminalIndex();
	}
}
