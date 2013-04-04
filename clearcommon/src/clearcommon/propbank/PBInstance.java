package clearcommon.propbank;

import java.io.Serializable;
import java.util.Arrays;
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
	String      verbnetId;
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
	
	public String getVerbnetId() {
		return verbnetId;
	}

	public void setVerbnetId(String verbnetId) {
		this.verbnetId = verbnetId;
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
    
	/**
	 * Returns the Node Id in PropBank format id[0] is the terminal index of the
	 * left most terminal id[1] is the tree level (from the left most terminal)
	 * @param node the input treebank node
	 * @return the Node Id in PropBank format
	 */
    public static int[] getNodeId(TBNode node) {
		int[] id = new int[2];
		while (node.getChildren().length != 0) {
			++id[1];
			node = node.getChildren()[0];
		}
		id[0] = node.getTerminalIndex();
		return id;
    }
    
	static void markNode(TBNode node, String[] preMarkup, String[] postMarkup, String pre, String post,  boolean printEC)
	{
		List<TBNode> nodes = printEC?node.getTerminalNodes():node.getTokenNodes();
		preMarkup[printEC?nodes.get(0).getTerminalIndex():nodes.get(0).getTokenIndex()] = pre;
		postMarkup[printEC?nodes.get(nodes.size()-1).getTerminalIndex():nodes.get(nodes.size()-1).getTokenIndex()] = post;		
	}
	
	public static void markArg(PBArg arg, String[] preMarkup, String[] postMarkup, String pre, String post, boolean printEC)
	{
		if (printEC)
			for (TBNode node:arg.getAllNodes())
				markNode(node, preMarkup, postMarkup, pre, post, printEC);
		else
			markNode(arg.getNode(), preMarkup, postMarkup, pre, post, printEC);
		
		for (PBArg narg:arg.getNestedArgs())
			markArg(narg, preMarkup, postMarkup, pre, post, printEC);
	}

	public String toText(boolean printEC)
	{
		StringBuilder buffer = new StringBuilder();

		TBNode[] nodes = printEC?tree.getTerminalNodes() : tree.getTokenNodes();
		
		String[] preMarkup = new String[nodes.length];
		String[] postMarkup = new String[nodes.length];
		
		Arrays.fill(preMarkup, "");
		Arrays.fill(postMarkup, "");
		
		String color;
		for (PBArg arg : (printEC?getAllArgs():getArgs()))	
			markArg(arg, preMarkup, postMarkup, "["+arg.getLabel()+" ", "]", printEC);
		
		for (int i=0; i<nodes.length; ++i)
		{
			buffer.append(preMarkup[i]);
			buffer.append(nodes[i].getWord());
			buffer.append(postMarkup[i]);
			buffer.append(" ");
		}
		return buffer.toString();
	}

    public String toText()
    {
    	return toText(false);
    }
    /*
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

    */
    
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
