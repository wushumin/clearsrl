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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sentence implements Serializable{
	
	/**
     * 
     */
    private static final long serialVersionUID = 1L;

    static final Pattern sentPattern = Pattern.compile("(\\d+)~(\\d+)");
	   
    public String   tbFile;
    public Map<Integer, TBTree> treeMap;
    public TBNode[] tokens;
    public long[]   indices;
    PBInstance[]    pbInstances; 
    
	public static Sentence parseSentence(TBTree tree, List<PBInstance> pbInstanceList)
	{
		Sentence sentence = new Sentence();
		sentence.tbFile = tree.getFilename();
		sentence.treeMap = new TreeMap<Integer, TBTree>();
		sentence.treeMap.put(tree.getIndex(), tree);
		
	    List<TBNode> nodes = tree.getRootNode().getTokenNodes();
	    sentence.indices = new long[nodes.size()];
	    sentence.tokens = new TBNode[nodes.size()];
	    for (int i=0; i<nodes.size();++i)
	    {
	    	sentence.indices[i] = makeIndex(tree.getIndex(),nodes.get(i).getTerminalIndex());
	    	sentence.tokens[i] = nodes.get(i);
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
	
	public static Sentence parseSentence(String line, Map<String, TBTree[]> tbData, Map<String, SortedMap<Integer, List<PBInstance>>> pbData)
	{
		Sentence sentence = new Sentence();
		
		StringTokenizer tok=new StringTokenizer(line);
		Matcher matcher;
		sentence.tbFile = tok.nextToken();
		sentence.treeMap = new TreeMap<Integer, TBTree>();
		
		TBTree[] trees = tbData.get(sentence.tbFile);
		SortedMap<Integer, List<PBInstance>> pbMap = pbData.get(sentence.tbFile);
		
		int treeIdx,terminalIdx;
		TLongArrayList a_idx = new TLongArrayList();
		List<TBNode> a_token = new ArrayList<TBNode>();
		
		while (tok.hasMoreTokens())
		{
			matcher = sentPattern.matcher(tok.nextToken());
			if (matcher.matches())
			{
				treeIdx = Integer.parseInt(matcher.group(1));
				sentence.treeMap.put(treeIdx, trees[treeIdx]);
				
				terminalIdx = Integer.parseInt(matcher.group(2));
				
				a_idx.add(makeIndex(treeIdx,terminalIdx));
				a_token.add(trees[treeIdx].getRootNode().getNodeByTerminalIndex(terminalIdx));
				
				//System.out.print(" "+sIdx+"-"+tIdx);
			}
		}

		sentence.indices = a_idx.toNativeArray();
		sentence.tokens = a_token.toArray(new TBNode[a_token.size()]);
		
		List<PBInstance> pbList = new ArrayList<PBInstance>();
		
		List<PBInstance> instances;
		for (long i:sentence.indices)
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
		sentence.pbInstances = pbList.toArray(new PBInstance[pbList.size()]);
		
		return sentence;
		
	}
	
	public String toTokenIdx()
	{
	    StringBuilder builder = new StringBuilder();
        builder.append(tbFile);
        for (long id:indices)
            builder.append(" "+getTreeIndex(id)+"~"+getTerminalIndex(id));
        return builder.toString();
	}
	
	public String toTokens()
	{
	    StringBuilder builder = new StringBuilder();
	    for (TBNode token:tokens)
            builder.append(token.getWord()+" ");
	    return builder.toString();
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(tbFile+":");
		for (TBNode token:tokens)
			builder.append(" "+token.getWord());
		builder.append("\n");
		for (PBInstance instance:pbInstances)
			builder.append(instance.toText()+"\n");
		return builder.toString();
	}

}
