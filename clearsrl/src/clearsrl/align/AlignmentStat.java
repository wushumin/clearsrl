package clearsrl.align;

import gnu.trove.TFloatArrayList;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AlignmentStat {
    
	int oneToOne;
	int total;
	
    Map<String, Map<String, TFloatArrayList>> srcDstPredicateMap;
    Map<String, Map<String, TFloatArrayList>> dstSrcPredicateMap;
    
    Map<String, Map<String, TFloatArrayList>> srcDstArgTypeMap;
    Map<String, Map<String, TFloatArrayList>> dstSrcArgTypeMap;
    
    Map<String, Map<String, TFloatArrayList>> srcDstCoreArgTypeMap;
    Map<String, Map<String, TFloatArrayList>> dstSrcCoreArgTypeMap;
    
    public AlignmentStat() {
        srcDstPredicateMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        dstSrcPredicateMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        
        srcDstArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        dstSrcArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        
        srcDstCoreArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        dstSrcCoreArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList>>();
        
        oneToOne = 0;
        total = 0;
        
    }
    
    class ObjectScore<T> implements Comparable<ObjectScore<T>>{
        
        public T object;
        public float score;
        public int number;
        public ObjectScore(){}
        
        public ObjectScore(T object, float score, int number) {
            this.object = object;
            this.score = score;
            this.number = number;
        }
        
        @Override
        public int compareTo(ObjectScore<T> rhs) {
            float diff = rhs.score-score;
            return diff<0?-1:(diff>0?1:0);
        }
    }
    
    static void insert(Map<String, Map<String, TFloatArrayList>> map, String key1, String key2, float score)
    {
        Map<String, TFloatArrayList> val1 = null;
        if ((val1=map.get(key1))==null)
        {
            val1 = new HashMap<String, TFloatArrayList>();
            map.put(key1, val1);
        }
        TFloatArrayList val2 = null;
        if ((val2=val1.get(key2))==null)
        {
            val2 = new TFloatArrayList();
            val1.put(key2, val2);
        }
        val2.add(score);
    }
    
    public void addAlignment(Alignment alignment)
    {
        insert(srcDstPredicateMap, alignment.getSrcPBInstance().getRoleset(), alignment.getDstPBInstance().getRoleset(), alignment.getCompositeScore());
        insert(dstSrcPredicateMap, alignment.getDstPBInstance().getRoleset(), alignment.getSrcPBInstance().getRoleset(), alignment.getCompositeScore());
        
        Set<String> srcArgSet = new HashSet<String>();
        Set<String> dstArgSet = new HashSet<String>();
        
        for (ArgAlignmentPair argPair: alignment.getArgAlignmentPairs())
        {
        	String srcLabel = convertLabel(alignment.getSrcPBArg(argPair.srcArgIdx).getLabel());
        	String dstLabel = convertLabel(alignment.getDstPBArg(argPair.dstArgIdx).getLabel());
        
        	srcArgSet.add(srcLabel);
        	dstArgSet.add(dstLabel);
        	
            insert(srcDstArgTypeMap, srcLabel, dstLabel, argPair.score);
            insert(dstSrcArgTypeMap, dstLabel, srcLabel, argPair.score);
            
            if (srcLabel.matches("A\\d") && dstLabel.matches("A\\d"))
            {
            	insert(srcDstCoreArgTypeMap, srcLabel, dstLabel, argPair.score);
                insert(dstSrcCoreArgTypeMap, dstLabel, srcLabel, argPair.score);
            }
            total += 2;
        }
        oneToOne += srcArgSet.size();
        oneToOne += dstArgSet.size();
    }
    
    static String convertLabel(String input)
    {
    	if (input.equals("ARG"))
    		return "A";
    	if (input.startsWith("ARG"))
    		return "A"+input.substring(3);
    	if (input.startsWith("rel"))
    		return "V";
    	return input;
    }
    
    long getTotal(Map<String, TFloatArrayList> map)
    {
        long total = 0;
        for (Map.Entry<String, TFloatArrayList> entry:map.entrySet())
            total += entry.getValue().size();
        return total;
    }
    
    List<ObjectScore<String>> makeStats(Map<String, TFloatArrayList> map, double total)
    {
        List<ObjectScore<String>> scores = new ArrayList<ObjectScore<String>>();
        
        for (Map.Entry<String, TFloatArrayList> entry:map.entrySet())
            scores.add(new ObjectScore<String>(entry.getKey(),(float)(entry.getValue().size()/total), entry.getValue().size()));
        
        return scores;
    }
    
    List<ObjectScore<String>> getTopScores(List<ObjectScore<String>> scores, int topN)
    {
        Collections.sort(scores);
        return scores.subList(0, topN>scores.size()?scores.size():topN);
    }
    
    void printStats(PrintStream out, Map<String, Map<String, TFloatArrayList>>  map, int topN)
    {
        for (Map.Entry<String, Map<String, TFloatArrayList>> entry:map.entrySet())
        {
            long total = getTotal(entry.getValue());
            if (total<=1) continue;
            out.print(entry.getKey()+":");
            List<ObjectScore<String>> scores = getTopScores(makeStats(entry.getValue(), total), topN);
            for (ObjectScore<String> score:scores)
                out.printf(" %s(%.4f/%d)", score.object, score.score, score.number);
            out.print("\n");
        }
    }

    void printMatrix(PrintStream out, Map<String, Map<String, TFloatArrayList>> map1, Map<String, Map<String, TFloatArrayList>> map2)
    {
    	Map<String, Map<String, TFloatArrayList>> mapR = map1.size()>map2.size()?map1:map2;
    	Map<String, Map<String, TFloatArrayList>> mapC = mapR==map1?map2:map1;
    	
    	
    	String[] keysR = mapR.keySet().toArray(new String[0]).clone();
    	Arrays.sort(keysR);
    	
    	String[] keysC = mapC.keySet().toArray(new String[0]).clone();
    	Arrays.sort(keysC);
    	
    	out.print("       ");
    	for (String keyC:keysC) out.printf("%6s&",keyC);
    	out.print("\n");
    	
    	for (String keyR:keysR)
    	{
    		out.printf("%6s&",keyR);
    		Map<String, TFloatArrayList> innerMap = mapR.get(keyR);
    		for (String keyC:keysC)
    			out.printf("%6d&",innerMap.get(keyC)==null?0:innerMap.get(keyC).size());
    		out.print("\n");
    	}
    }
    
    public void printStats(PrintStream out)
    {
        
        out.println("\nSrc->Dst Argument:");
        printStats(out, srcDstPredicateMap, 5);
        
        out.println("\nDst->Src Argument:");
        printStats(out, dstSrcPredicateMap, 5);   
        
        out.println("\nSrc->Dst Argument:");
        printStats(out, srcDstArgTypeMap, 5);
        
        out.println("\nDst->Src Argument:");
        printStats(out, dstSrcArgTypeMap, 5);
        
        printMatrix(out, srcDstArgTypeMap, dstSrcArgTypeMap);
        
        out.printf("one-to-one: %d, total: %d, %f\n", oneToOne, total, oneToOne*1.0/total);

    }
}
