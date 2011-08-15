package clearcommon.propbank;

import java.io.Serializable;
import java.util.BitSet;
import java.util.List;

import clearcommon.treebank.*;

public class PBInstance implements Comparable<PBInstance>, Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = -1966998836839085182L;
    
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

    public String toText()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(" ");
        buffer.append(tree.getIndex()); buffer.append(" ");
        
        List<TBNode> nodes = tree.getRootNode().getTokenNodes();
        String[] tokens = new String[nodes.size()];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes.get(i).getWord();
        
        for (PBArg arg:args)
        {
            BitSet bits = arg.node.getTokenSet();//arg.getTokenSet();
            if (bits.nextSetBit(0)<0) continue;
            if (bits.nextSetBit(0)>= nodes.size())
            {
                System.err.println(nodes);
                System.err.println(arg.label+": "+bits);
            }
            
            tokens[bits.nextSetBit(0)] = '['+arg.label+' '+tokens[bits.nextSetBit(0)];
            tokens[bits.length()-1] = tokens[bits.length()-1]+"]";
            
            for (PBArg carg:arg.nestedArgs)
            {
                bits = carg.getTokenSet();
                if (bits.nextSetBit(0)<0) continue;
                if (bits.nextSetBit(0)>= nodes.size())
                {
                    System.err.println(nodes);
                    System.err.println(carg.label+": "+bits);
                }
                
                tokens[bits.nextSetBit(0)] = '['+carg.label+' '+tokens[bits.nextSetBit(0)];
                tokens[bits.length()-1] = tokens[bits.length()-1]+"]";
            }
        }
        
        for (String token:tokens)
            buffer.append(token+' ');
        
        return buffer.toString();
    }

    
    
	@Override
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
