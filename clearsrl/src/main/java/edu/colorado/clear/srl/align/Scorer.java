package edu.colorado.clear.srl.align;

import gnu.trove.iterator.TDoubleDoubleIterator;
import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TDoubleDoubleMap;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TDoubleDoubleHashMap;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public class Scorer {

    public static SortedMap<Integer, SortedMap<Integer, Set<String>>> readScore(String filename) throws FileNotFoundException
    {
    	SortedMap<Integer, SortedMap<Integer, Set<String>>> scores = new TreeMap<Integer, SortedMap<Integer, Set<String>>>();

        Scanner scanner = new Scanner(new BufferedReader(new FileReader(filename)));
        
        int   sentenceId;
        int    srcSRLId;
        int    dstSRLId;
        
        int i=0, j=0;
        while (scanner.hasNextLine())
        {
            Set<String> argSet = new HashSet<String>();
            
            String[] segments = scanner.nextLine().trim().split(";");
            
            if (segments.length<1) continue;
            
            String[] tokens = segments[0].split(",");
        
            if (tokens.length<4) continue;
            try {
                sentenceId = Integer.parseInt(tokens[0].trim());
                srcSRLId = Short.parseShort(tokens[1].trim());
                dstSRLId = Short.parseShort(tokens[2].trim());
                
                int innerId = (srcSRLId<<16)|dstSRLId;
                
                SortedMap<Integer, Set<String>> innerMap = scores.get(sentenceId);
                if (innerMap==null)
                	scores.put(sentenceId, innerMap=new TreeMap<Integer, Set<String>>());
                
                innerMap.put(innerId, argSet);
            } 
            catch (NumberFormatException e)
            {
                System.err.println(e);
            }
            ++i;
            
            for (int s=1; s<segments.length; ++s)
            {
                if (segments[s].startsWith("[")) continue;
                
                tokens = segments[s].split(",");
                
                if (tokens.length<3) continue;
                argSet.add(tokens[0].trim()+"<->"+tokens[1].trim());
                ++j;
            }
            
        }
        System.out.println(filename+": "+i+" "+j);
        return scores;
    }
    
    public static float[][] score(SortedMap<Integer, SortedMap<Integer, Set<String>>> lhs, SortedMap<Integer, SortedMap<Integer, Set<String>>> rhs)
    {
        float[][] ret = new float[3][3];
        
        TreeSet<Integer> sentenceIds = new TreeSet<Integer>(lhs.keySet());
        sentenceIds.addAll(rhs.keySet());
        
        for (int s=1; s<=sentenceIds.last(); ++s) {
        	int sentenceId = s;
        	SortedMap<Integer, Set<String>> lhsMap = lhs.get(sentenceId);
        	SortedMap<Integer, Set<String>> rhsMap = rhs.get(sentenceId);
        	
        	int[][] cnts = new int[3][3];

        	if (lhsMap!=null) {
        		cnts[0][1] = lhsMap.size();
        		for (SortedMap.Entry<Integer, Set<String>> entry:lhsMap.entrySet()) {
        			for (String label:entry.getValue())
        				if (label.matches("ARG\\d(_\\d+)*<->ARG\\d(_\\d+)*"))
        					cnts[1][1]++;
        			cnts[2][1]+=entry.getValue().size();
        		}
        	}
        		
        		
        	if (rhsMap!=null) {
        		cnts[0][2] = rhsMap.size();
        		for (SortedMap.Entry<Integer, Set<String>> entry:rhsMap.entrySet()) {
        			for (String label:entry.getValue())
        				if (label.matches("ARG\\d(_\\d+)*<->ARG\\d(_\\d+)*"))
        					cnts[1][2]++;
        			cnts[2][2]+=entry.getValue().size();
        		}
        	}
        	
        	if (lhsMap!=null && rhsMap!=null) {
        		TreeSet<Integer> idMap = new TreeSet<Integer>(lhsMap.keySet());
        		idMap.retainAll(rhsMap.keySet());
        		cnts[0][0] = idMap.size();
        		
        		for (int id:idMap) {
        			Set<String> lhsSet = lhsMap.get(id);
        			Set<String> rhsSet = rhsMap.get(id);
        			
        			for (String label:lhsSet)
        				if (rhsSet.contains(label)) {
        					cnts[2][0]++;
        					if (label.matches("ARG\\d(_\\d+)*<->ARG\\d(_\\d+)*"))
        						cnts[1][0]++;
        				}
        		}
        	}
        	
        	System.out.printf("PRED_COUNTS: %d %d %d\n", cnts[0][0], cnts[0][1], cnts[0][2]);
        	System.out.printf("CORE_COUNTS: %d %d %d\n", cnts[1][0], cnts[1][1], cnts[1][2]);
        	System.out.printf("ARGA_COUNTS: %d %d %d\n", cnts[2][0], cnts[2][1], cnts[2][2]);
        	
        	for (int i=0; i<cnts.length; ++i)
        		for (int j=0; j<cnts[i].length; ++j)
        			ret[i][j]+=cnts[i][j];
        }

        return ret;
    }

    public static float score(TIntObjectMap<TIntDoubleMap> lhs, TIntObjectMap<TIntDoubleMap> rhs)
    {
        float score=0;
        float cnt=0;
        
        TIntDoubleMap sMap;
        
        TDoubleDoubleMap dMap = new TDoubleDoubleHashMap();
        TDoubleDoubleMap nMap = new TDoubleDoubleHashMap();
        
        for (TIntObjectIterator<TIntDoubleMap> iter=lhs.iterator(); iter.hasNext();)
        {
            iter.advance();
            cnt += iter.value().size();
            if ((sMap=rhs.get(iter.key()))==null) continue;
                
            for (TIntDoubleIterator sIter=iter.value().iterator(); sIter.hasNext();)
            {
                sIter.advance();
                if (sMap.containsKey(sIter.key()))
                {
                    ++score;
                    dMap.put(sIter.value(),dMap.get(sIter.value())+1);
                    nMap.put(sIter.value(),nMap.get(sIter.value())+sMap.get(sIter.key()));
                }
            }
        }
        
        if (dMap.size()<=10)
        {
            for (TDoubleDoubleIterator iter=dMap.iterator(); iter.hasNext();)
            {
                iter.advance();
                System.out.printf("%f: %d, %f\n",iter.key(), (int)(iter.value()), nMap.get(iter.key())/(iter.value()));
            }
        }
        
        return cnt==0?0:score/cnt;
    }

    
    public static void main(String[] args) throws IOException
    {
    	SortedMap<Integer, SortedMap<Integer, Set<String>>> goldLabel = Scorer.readScore(args[0]);
    	SortedMap<Integer, SortedMap<Integer, Set<String>>> systemLabel = Scorer.readScore(args[1]);
        float[][] cnts = Scorer.score(systemLabel, goldLabel);
        
        for (int i=0; i<cnts.length; ++i) {
        	cnts[i][1] = cnts[i][0]/cnts[i][1];
        	cnts[i][2] = cnts[i][0]/cnts[i][2];
        	cnts[i][0] = 2*cnts[i][1]*cnts[i][2]/(cnts[i][1]+cnts[i][2]);
        }
        
        System.out.printf("predicate precision: %.2f, recall: %.2f, f-score: %.2f\n", cnts[0][1]*100, cnts[0][2]*100, cnts[0][0]*100);
        System.out.printf("core argument precision: %.2f, recall: %.2f, f-score: %.2f\n", cnts[1][1]*100, cnts[1][2]*100, cnts[1][0]*100);
        System.out.printf("all argument precision: %.2f, recall: %.2f, f-score: %.2f\n", cnts[2][1]*100, cnts[2][2]*100, cnts[2][0]*100);
    }
}
