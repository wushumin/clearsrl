package clearsrl.align;

import gnu.trove.TFloatArrayList;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AlignmentStat {
    
    Map<String, Map<String, TFloatArrayList> > srcDstPredicateMap;
    Map<String, Map<String, TFloatArrayList> > dstSrcPredicateMap;
    
    Map<String, Map<String, TFloatArrayList> > srcDstArgTypeMap;
    Map<String, Map<String, TFloatArrayList> > dstSrcArgTypeMap;
    
    public AlignmentStat() {
        srcDstPredicateMap = new TreeMap<String, Map<String, TFloatArrayList> >();
        dstSrcPredicateMap = new TreeMap<String, Map<String, TFloatArrayList> >();
        
        srcDstArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList> >();
        dstSrcArgTypeMap = new TreeMap<String, Map<String, TFloatArrayList> >();
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
    
    static void insert(Map<String, Map<String, TFloatArrayList> > map, String key1, String key2, float score)
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
        
        for (ArgAlignmentPair argPair: alignment.getArgAlignmentPairs())
        {
            insert(srcDstArgTypeMap, alignment.getSrcPBArg(argPair.srcArgIdx).getLabel(), alignment.getDstPBArg(argPair.dstArgIdx).getLabel(), argPair.score);
            insert(dstSrcArgTypeMap, alignment.getDstPBArg(argPair.dstArgIdx).getLabel(), alignment.getSrcPBArg(argPair.srcArgIdx).getLabel(), argPair.score);
        }
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
    
    void printStats(PrintStream out, Map<String, Map<String, TFloatArrayList> >  map, int topN)
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

    }
}
