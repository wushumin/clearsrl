package clearcommon.propbank;

import java.io.Serializable;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Logger;

import clearcommon.treebank.*;

public class PBInstance implements Comparable<PBInstance>, Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = -1966998836839085182L;
    
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    TBNode      predicateNode;
	String      rolesetId;
	PBArg[]     args;
	PBArg[]     emptyArgs;
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
	 * Gets empty (word) arguments 
	 * @return empty (word) arguments 
	 */
	public PBArg[] getEmptyArgs()
	{
		return emptyArgs;
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
    	return toText(false);
    }
    
    public String toText(boolean includeTerminals)
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(" ");
        buffer.append(tree.getIndex()); buffer.append(" ");
        
        List<TBNode> nodes = includeTerminals?tree.getRootNode().getTerminalNodes():tree.getRootNode().getTokenNodes();
        String[] tokens = new String[nodes.size()];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes.get(i).getWord();
        
        for (PBArg arg:includeTerminals?allArgs:args)
        {
            BitSet bits = includeTerminals?arg.node.getTerminalSet():arg.node.getTokenSet();//arg.getTokenSet();
            if (bits.nextSetBit(0)<0) continue;
            if (bits.nextSetBit(0)>= nodes.size())
            	logger.warning(nodes.toString()+"\n"+arg.label+": "+bits);
            
            tokens[bits.nextSetBit(0)] = '['+arg.label+' '+tokens[bits.nextSetBit(0)];
            tokens[bits.length()-1] = tokens[bits.length()-1]+"]";
            
            for (PBArg carg:arg.nestedArgs)
            {
                bits = includeTerminals?carg.node.getTerminalSet():carg.getTokenSet();
                if (bits.nextSetBit(0)<0) continue;
                if (bits.nextSetBit(0)>= nodes.size())
                	logger.warning(nodes.toString()+"\n"+carg.label+": "+bits);
                
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
