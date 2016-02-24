package edu.colorado.clear.srl.align;

import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBTree;
import gnu.trove.list.array.TLongArrayList;

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

    static final Pattern sentPattern = Pattern.compile("(\\d+)[~-](\\d+)");
       
    public String               tbFile;
    public Map<Integer, TBTree> treeMap;
    public TBNode[]             tokens;
    public TBNode[]             terminals;
    public long[]               indices;
    public long[]               terminalIndices;
    public int[]                tokenToTerminalMap;
    public int[]                terminalToTokenMap;
    public PBInstance[]         pbInstances; 
    
    
    
    public static Sentence parseSentence(TBTree tree, List<PBInstance> pbInstanceList)
    {
        Sentence sentence = new Sentence();
        sentence.tbFile = tree.getFilename();
        sentence.treeMap = new TreeMap<Integer, TBTree>();
        sentence.treeMap.put(tree.getIndex(), tree);
        
        TBNode[] nodes = tree.getTokenNodes();
        sentence.indices = new long[nodes.length];
        sentence.tokens = new TBNode[nodes.length];
        for (int i=0; i<nodes.length;++i)
        {
            sentence.indices[i] = makeIndex(tree.getIndex(),nodes[i].getTerminalIndex());
            sentence.tokens[i] = nodes[i];
        }
        sentence.findTerminals();
 
        sentence.pbInstances = pbInstanceList==null?new PBInstance[0]:pbInstanceList.toArray(new PBInstance[pbInstanceList.size()]);
        return sentence;
    }
    
    public static long makeIndex(int treeIndex, int terminalIndex)
    {
        return (((long)treeIndex)<<32)|terminalIndex;
    }
    
    public static int getTreeIndex(long index)
    {
        return (int)(index>>>32);
    }
    
    public static int getTerminalIndex(long index)
    {
        return (int)(index&0xffffffff);
    }
    
    public static Sentence parseSentence(String line, Map<String, TBTree[]> tbData, Map<String, SortedMap<Integer, List<PBInstance>>> pbData, boolean tokenIndexed)
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
                
                TBNode node = tokenIndexed?trees[treeIdx].getNodeByTokenIndex(terminalIdx):trees[treeIdx].getNodeByTerminalIndex(terminalIdx);
                
                a_idx.add(makeIndex(treeIdx,node.getTerminalIndex()));
                a_token.add(node);
                
                //System.out.print(" "+sIdx+"-"+tIdx);
            }
        }

        sentence.indices = a_idx.toArray();
        sentence.tokens = a_token.toArray(new TBNode[a_token.size()]);
        sentence.findTerminals();
        
        sentence.addPBInstances(pbMap);
        
        return sentence;
        
    }
    
    public static Sentence parseSentence(long[] tokenIndices, String tbFile, TBTree[] trees, SortedMap<Integer, List<PBInstance>> pbMap)
    {
        Sentence sentence = new Sentence();

        sentence.tbFile = tbFile;
        sentence.treeMap = new TreeMap<Integer, TBTree>();
        
        int treeIdx,tokenIdx;
        TLongArrayList a_idx = new TLongArrayList();
        List<TBNode> a_token = new ArrayList<TBNode>();
        
        for (long idx:tokenIndices)
        {
            treeIdx = (int) (idx>>32);
            tokenIdx = (int) (idx&0xffffffff);
            
            sentence.treeMap.put(treeIdx, trees[treeIdx]);
            a_token.add(trees[treeIdx].getNodeByTokenIndex(tokenIdx));
            a_idx.add(makeIndex(treeIdx,a_token.get(a_token.size()-1).getTerminalIndex()));
        }

        sentence.indices = a_idx.toArray();
        sentence.tokens = a_token.toArray(new TBNode[a_token.size()]);
        
        sentence.addPBInstances(pbMap);
        sentence.findTerminals();
        
        return sentence;
        
    }

    void findTerminals()
    {
        if (tokens.length<=1){
            terminals = tokens;
            terminalIndices = indices;
        }
        else
        {
        
            TLongArrayList terminalIndexList = new TLongArrayList();
            List<TBNode> terminalList = new ArrayList<TBNode>();
            
            TBTree startTree = treeMap.get((int)(indices[0]>>32));
            TBTree endTree = treeMap.get((int)(indices[indices.length-1]>>32));
            
            TBNode startToken = tokens[0];
            TBNode endToken = tokens[tokens.length-1];
            
            int startTerminalIdx = startToken.getTerminalIndex();
            int endTerminalIdx = endToken.getTerminalIndex();
            
            
            TBNode[] startTerminals = startTree.getTerminalNodes();
            TBNode[] endTerminals = endTree.getTerminalNodes();
            
            while (startTerminalIdx>0)
            {
                if (startTerminals[startTerminalIdx-1].isToken()) break;
                TBNode ancestor = startTerminals[startTerminalIdx-1].getLowestCommonAncestor(startToken);
                if (ancestor.getTokenNodes().get(0)!=startToken) break;
                --startTerminalIdx;
            }
    
            while (endTerminalIdx<endTerminals.length-1)
            {
                if (endTerminals[endTerminalIdx+1].isToken()) break;
                TBNode ancestor = endTerminals[endTerminalIdx+1].getLowestCommonAncestor(endToken);
                List<TBNode> nodes = ancestor.getTokenNodes();
                if (nodes.get(nodes.size()-1)!=endToken) break;
                ++endTerminalIdx;
            }
            
            for (int i=startTree.getIndex(); i<=endTree.getIndex(); ++i)
            {
                TBTree tree = treeMap.get(i);
                if (tree==null) continue;
                for (TBNode terminal : tree.getTerminalNodes())
                {
                    if (tree==startTree && terminal.getTerminalIndex()<startTerminalIdx) continue;
                    if (tree==endTree && terminal.getTerminalIndex()>endTerminalIdx) continue;
                    terminalList.add(terminal);
                    terminalIndexList.add(makeIndex(i, terminal.getTerminalIndex()));
                }
            }
    
            terminals = terminalList.toArray(new TBNode[terminalList.size()]);
            terminalIndices = terminalIndexList.toArray();
        }
        tokenToTerminalMap = new int[indices.length];
        terminalToTokenMap = new int[terminals.length];
        int count = 0;
        
        for (int i=0; i<terminals.length; ++i)
        {
            if (terminals[i].isToken()) {
            	terminalToTokenMap[i] = count;
                tokenToTerminalMap[count++] = i;
            } else
            	terminalToTokenMap[i] = -1;
        }
    }
    
    void addPBInstances(SortedMap<Integer, List<PBInstance>> pbMap)
    {
        List<PBInstance> pbList = new ArrayList<PBInstance>();
        
        List<PBInstance> instances;
        for (long i:indices)
        {
            int treeIdx = getTreeIndex(i);
            int terminalIdx = getTerminalIndex(i);
            if (pbMap!=null && (instances=pbMap.get(treeIdx))!=null)
            {
                for (PBInstance instance:instances)
                    if (instance.getPredicate().getTerminalIndex()==terminalIdx) pbList.add(instance);
                //System.out.print(instance);
                //System.out.println("------------------------");
            }
        }
        pbInstances = pbList.toArray(new PBInstance[pbList.size()]);
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
    
    @Override
	public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(tbFile+":");
        for (TBNode token:terminals)
        {
            builder.append(" "+token.getWord());
        }
        builder.append("\n");
        for (int i=0; i<pbInstances.length; ++i)
        	builder.append(""+(i+1)+" "+pbInstances[i].toText(true)+"\n");
        return builder.toString();
    }

}
