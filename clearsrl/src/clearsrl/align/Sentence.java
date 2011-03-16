package clearsrl.align;

import gnu.trove.TLongArrayList;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sentence implements Serializable{
	
	/**
     * 
     */
    private static final long serialVersionUID = 1L;

    static final Pattern sentPattern = Pattern.compile("(\\d+)~(\\d+)");
	   
    public String   tbFile;
    public String[] tokens;
    public TBNode[] terminals;
    public long[]   indices;
    PBInstance[]    pbInstances; 
    
	public static Sentence parseSentence(TBTree tree, List<PBInstance> pbInstanceList)
	{
		Sentence sentence = new Sentence();
		sentence.tbFile = tree.getFilename();
	    List<TBNode> nodes = tree.getRootNode().getTokenNodes();
	    sentence.terminals = tree.getRootNode().getTerminalNodes().toArray(new TBNode[0]);
	    sentence.indices = new long[nodes.size()];
	    sentence.tokens = new String[nodes.size()];
	    for (int i=0; i<nodes.size();++i)
	    {
	    	sentence.indices[i] = makeIndex(tree.getIndex(),nodes.get(i).getTerminalIndex());
	    	sentence.tokens[i] = nodes.get(i).getWord();
	    }
	                        
	    sentence.pbInstances = pbInstanceList==null?new PBInstance[0]:pbInstanceList.toArray(new PBInstance[pbInstanceList.size()]);
	    return sentence;
	}
	
	public static long makeIndex(int treeIndex, int terminalIndex)
	{
	    return (((long)treeIndex)<<32)|((long)terminalIndex);
	}
	
	public static int getTreeIndex(long index)
	{
	    return (int)(index>>>32);
	}
	
    public static int getTerminalIndex(long index)
    {
        return (int)(index&0xffffffff);
    }
	
	public void parseSentence(String line, Map<String, TBTree[]> tbData, Map<String, SortedMap<Integer, List<PBInstance>>> pbData)
	{
		StringTokenizer tok=new StringTokenizer(line); // Chinese treebank
		Matcher matcher;
		tbFile = tok.nextToken();
	
		TBTree[] trees = tbData.get(tbFile);
		SortedMap<Integer, List<PBInstance>> pbMap = pbData.get(tbFile);
		
		int treeIdx,terminalIdx;
		TLongArrayList a_idx = new TLongArrayList();
		List<String> a_token = new ArrayList<String>();
		List<TBNode> a_terminals = new ArrayList<TBNode>();
		
		while (tok.hasMoreTokens())
		{
			matcher = sentPattern.matcher(tok.nextToken());
			if (matcher.matches())
			{
				treeIdx = Integer.parseInt(matcher.group(1));
				terminalIdx = Integer.parseInt(matcher.group(2));
				
				a_idx.add(makeIndex(treeIdx,terminalIdx));
				a_terminals.add(trees[treeIdx].getRootNode().getNodeByTerminalIndex(terminalIdx));
				a_token.add(a_terminals.get(a_token.size()).getWord());
				
				//System.out.print(" "+sIdx+"-"+tIdx);
			}
		}

		indices = a_idx.toNativeArray();
		tokens = a_token.toArray(new String[a_token.size()]);
		terminals = a_terminals.toArray(new TBNode[a_terminals.size()]);
		
		List<PBInstance> pbList = new ArrayList<PBInstance>();
		
		List<PBInstance> instances;
		for (long i:indices)
		{
		    treeIdx = getTreeIndex(i);
			terminalIdx = getTerminalIndex(i);
			if ((instances=pbMap.get(treeIdx))!=null)
			{
			    for (PBInstance instance:instances)
			        if (instance.getPredicate().getTerminalIndex()==terminalIdx) pbList.add(instance);
				//System.out.print(instance);
				//System.out.println("------------------------");
			}
		}
		pbInstances = pbList.toArray(new PBInstance[pbList.size()]);
		
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(tbFile+":");
		for (String token:tokens)
			builder.append(" "+token);
		builder.append("\n");
		for (PBInstance instance:pbInstances)
			builder.append(instance.toText()+"\n");
		return builder.toString();
	}

}
