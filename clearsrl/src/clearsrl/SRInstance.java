package clearsrl;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

public class SRInstance {

    enum OutputFormat {
        TEXT,
        PROPBANK
    };
    
	TBNode predicateNode;
	TBTree tree;
	ArrayList<SRArg> args;
	
	String rolesetId;
	
	public SRInstance(TBNode predicateNode, TBTree tree)
	{
		this.predicateNode = predicateNode;
		this.tree = tree;
		args = new ArrayList<SRArg>();
	}
	/*
	public SRInstance(PBInstance instance) {
		this(instance.predicateNode, instance.tree);
		for (Entry<String, PBArg> entry : instance.getArgs().entrySet())
		{
			BitSet tokenSet = new BitSet(tree.getTokenCount());
			for (TBNode node:entry.getValue().getTokenNodes())
				if (node.tokenIndex>=0) tokenSet.set(node.tokenIndex);
			if (tokenSet.isEmpty()) continue;
			addArg(new SRArg(SRLUtil.removeArgModifier(entry.getKey()), tokenSet));
		}
	}
	*/
	public SRInstance(PBInstance instance) {
		this(instance.getPredicate(), instance.getTree());
		for (PBArg pbArg: instance.getArgs())
		{
		    if (!pbArg.getTokenSet().isEmpty())
		        addArg(new SRArg(SRLUtil.removeArgModifier(pbArg.getLabel()), pbArg.getNode()));

			for (PBArg nestedArg:pbArg.getNestedArgs())
			    if (!nestedArg.getTokenSet().isEmpty())
	                addArg(new SRArg(SRLUtil.removeArgModifier(nestedArg.getLabel()), nestedArg.getNode()));
		}
	}

	public void addArg(SRArg arg)
	{
		if (!arg.getTokenSet().isEmpty() && tree.getTokenCount() >= arg.getTokenSet().length())
			args.add(arg);
	}
	
	public TBNode getPredicateNode()
	{
		return predicateNode;
	}
	
	public TBTree getTree()
	{
		return tree;
	}
	
	public ArrayList<SRArg> getArgs()
	{
		return args;
	}
	
	public String getRolesetId() {
		return rolesetId;
	}

	public void setRolesetId(String rolesetId) {
		this.rolesetId = rolesetId;
	}
	
	public String toPropbankString()
	{
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(' ');
        buffer.append(tree.getIndex()); buffer.append(' ');
        buffer.append(predicateNode.getTerminalIndex()); buffer.append(' ');
        buffer.append("system "); buffer.append(predicateNode.getWord());
        buffer.append(" ----- ");
        
        TreeMap<String, TreeSet<SRArg>> argMap = new TreeMap<String, TreeSet<SRArg>>();
        
        for (SRArg arg:args)
        {
           TreeSet<SRArg> argSet;
           if ((argSet = argMap.get(arg.label))==null)
           {
               argSet = new TreeSet<SRArg>();
               argMap.put(arg.label, argSet);
           }
           argSet.add(arg);
        }
        
        for (Map.Entry<String, TreeSet<SRArg>> entry:argMap.entrySet())
        {
            String argStr = "";
            for (SRArg arg:entry.getValue())
            {
                int depth=0;
                TBNode node = arg.node;
                while (!node.isTerminal())
                {
                    ++depth;
                    node=node.getChildren()[0];
                }
                argStr+=node.getTerminalIndex()+":"+depth+"*";
            }
            buffer.append(argStr.substring(0,argStr.length()-1));
            buffer.append('-');
            buffer.append(entry.getKey()); buffer.append(' ');   
        }
        
        return buffer.toString();
	}
	
	public String toString()
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(tree.getFilename()); buffer.append(" ");
		buffer.append(tree.getIndex()); buffer.append(" ");
		
		List<TBNode> nodes = tree.getRootNode().getTokenNodes();
		String[] tokens = new String[nodes.size()];
		for (int i=0; i<tokens.length; ++i)
			tokens[i] = nodes.get(i).getWord();
		
		for (SRArg arg:args)
		{
			BitSet bits = arg.getTokenSet();
			
			if (bits.nextSetBit(0)>= nodes.size())
			{
				System.err.println(nodes);
				System.err.println(arg.label+": "+bits);
			}
			
			tokens[bits.nextSetBit(0)] = '['+arg.label+' '+tokens[bits.nextSetBit(0)];
			tokens[bits.length()-1] = tokens[bits.length()-1]+"]";
		}
		
		for (String token:tokens)
			buffer.append(token+' ');
		
		return buffer.toString();
	}
	
    public String toString(OutputFormat outputFormat) {
        switch (outputFormat)
        {
        case TEXT:
            return toString();
        case PROPBANK:
            return toPropbankString();
        }
        return toString();
    }
	
}
