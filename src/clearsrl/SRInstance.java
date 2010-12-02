package clearsrl;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Map.Entry;

import harvest.propbank.PBArg;
import harvest.propbank.PBInstance;
import harvest.treebank.TBNode;
import harvest.treebank.TBTree;

public class SRInstance {

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
		this(instance.predicateNode, instance.tree);
		for (Entry<String, PBArg> entry : instance.getArgs().entrySet())
		{
			for (TBNode argNode:entry.getValue().getNodes())
			{
				BitSet tokenSet = new BitSet(tree.getTokenCount());
				for (TBNode node:argNode.getTokenNodes())
					if (node.tokenIndex>=0) tokenSet.set(node.tokenIndex);
				if (tokenSet.isEmpty()) continue;
				addArg(new SRArg(SRLUtil.removeArgModifier(entry.getKey()), tokenSet));
			}
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
	
	public String toString()
	{
		StringBuilder buffer = new StringBuilder();
		tree.moveToRoot();
		ArrayList<TBNode> nodes = tree.getRootNode().getTokenNodes();
		String[] tokens = new String[nodes.size()];
		for (int i=0; i<tokens.length; ++i)
			tokens[i] = nodes.get(i).word;
		
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
	
}
