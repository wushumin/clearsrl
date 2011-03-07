package clearsrl.align;

import gnu.trove.TIntArrayList;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sentence {
	
	static final Pattern sentPattern = Pattern.compile("(\\d+)~(\\d+)");
	   
    public String   tbFile;
    public String[] tokens;
    public int[]    indices;
    PBInstance[]    pbInstances; 
    
	public static Sentence parseSentence(TBTree tree, List<PBInstance> pbInstanceList)
	{
		Sentence sentence = new Sentence();
		sentence.tbFile = tree.getFilename();
	    List<TBNode> nodes = tree.getRootNode().getTokenNodes();
	    sentence.indices = new int[nodes.size()];
	    sentence.tokens = new String[nodes.size()];
	    for (int i=0; i<nodes.size();++i)
	    {
	    	sentence.indices[i] = (tree.getIndex()<<16)|nodes.get(i).getTerminalIndex();
	    	sentence.tokens[i] = nodes.get(i).getWord();
	    }
	                        
	    sentence.pbInstances = pbInstanceList==null?new PBInstance[0]:pbInstanceList.toArray(new PBInstance[pbInstanceList.size()]);
	    return sentence;
	}
	
	public void parseSentence(String line, Map<String, TBTree[]> tbData, Map<String, SortedMap<Integer, List<PBInstance>>> pbData)
	{
		StringTokenizer tok=new StringTokenizer(line); // Chinese treebank
		Matcher matcher;
		tbFile = tok.nextToken();
	
		TBTree[] trees = tbData.get(tbFile);
		SortedMap<Integer, List<PBInstance>> pbMap = pbData.get(tbFile);
		
		int sIdx,tIdx;
		TIntArrayList a_idx = new TIntArrayList();
		ArrayList<String> a_token = new ArrayList<String>();
		
		while (tok.hasMoreTokens())
		{
			matcher = sentPattern.matcher(tok.nextToken());
			if (matcher.matches())
			{
				sIdx = Integer.parseInt(matcher.group(1));
				tIdx = Integer.parseInt(matcher.group(2));
				
				a_idx.add((sIdx<<16)|tIdx);
				a_token.add(trees[sIdx].getRootNode().getNodeByTerminalIndex(tIdx).getWord());
				
				//System.out.print(" "+sIdx+"-"+tIdx);
			}
		}

		indices = a_idx.toNativeArray();
		tokens = a_token.toArray(new String[a_token.size()]);
		
		
		ArrayList<PBInstance> pbList = new ArrayList<PBInstance>();
		
		List<PBInstance> instances;
		for (int i:indices)
		{
		    sIdx = i>>16;
			tIdx = i & 0xffff;
			if ((instances=pbMap.get(sIdx))!=null)
			{
			    for (PBInstance instance:instances)
			        if (instance.getPredicate().getTerminalIndex()==tIdx) pbList.add(instance);
				//System.out.print(instance);
				//System.out.println("------------------------");
			}
		}
		pbInstances = pbList.toArray(new PBInstance[pbList.size()]);
		
	}

}
