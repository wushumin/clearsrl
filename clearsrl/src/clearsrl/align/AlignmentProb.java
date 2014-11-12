package clearsrl.align;

import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearsrl.util.SimpleGoodTuring;

public class AlignmentProb implements Serializable{
    /**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    
    private static final int COUNT_THRESHOLD=5;
    private static final int COUNT_THRESHOLD_2=25;
    
	SortedMap<String, CountProb<String>> srcPredDstPredProbMap;
	SortedMap<String, CountProb<String>> dstPredSrcPredProbMap;
    
	SortedMap<String, CountProb<String>> srcArgDstArgProbMap;
	SortedMap<String, CountProb<String>> dstArgSrcArgProbMap;
    
	SortedMap<String, CountProb<String>> srcPredArgDstPredArgProbMap;
	SortedMap<String, CountProb<String>> dstPredArgSrcPredArgProbMap;

	SortedMap<String, CountProb<String>> srcPredArgDstArgProbMap;
	SortedMap<String, CountProb<String>> dstPredSrcArgDstArgProbMap;
	
	SortedMap<String, CountProb<String>> dstPredArgSrcArgProbMap;
	SortedMap<String, CountProb<String>> srcPredDstArgSrcArgProbMap;

    CountProb<String> srcPredProb;
    CountProb<String> dstPredProb;
    
    CountProb<String> srcArgProb;
    CountProb<String> dstArgProb;
    
    public AlignmentProb() {

    	srcPredDstPredProbMap = new TreeMap<String, CountProb<String>>();
        dstPredSrcPredProbMap = new TreeMap<String, CountProb<String>>();
        
        srcArgDstArgProbMap = new TreeMap<String, CountProb<String>>();
        dstArgSrcArgProbMap = new TreeMap<String, CountProb<String>>();
        
        srcPredArgDstPredArgProbMap = new TreeMap<String, CountProb<String>>();
        dstPredArgSrcPredArgProbMap = new TreeMap<String, CountProb<String>>();
        
        srcPredArgDstArgProbMap = new TreeMap<String, CountProb<String>>();
    	dstPredSrcArgDstArgProbMap = new TreeMap<String, CountProb<String>>();

        dstPredArgSrcArgProbMap = new TreeMap<String, CountProb<String>>();
        srcPredDstArgSrcArgProbMap = new TreeMap<String, CountProb<String>>();
        
        srcPredProb = new CountProb<String>();
        dstPredProb = new CountProb<String>();
        
        srcArgProb = new CountProb<String>();
        dstArgProb = new CountProb<String>();

    }
    
    static <T> TObjectDoubleMap<T> convertToProb(TObjectDoubleMap<T> map) {
    	TIntIntMap cntMap = new TIntIntHashMap();
    	
    	int tCnt = 0;
    	for(TObjectDoubleIterator<T> iter=map.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		cntMap.adjustOrPutValue((int)iter.value(), 1, 1);
    		tCnt+=(int)iter.value();
    	}
    	
    	int[] keys = cntMap.keys(new int[cntMap.size()]);
    	Arrays.sort(keys);
    	int[] values = new int[keys.length];
    	for (int i=0; i<keys.length; ++i)
    		values[i] = cntMap.get(keys[i]);
    		
    	SimpleGoodTuring sgt = new SimpleGoodTuring(keys, values);
    	double[] probs = sgt.getProbabilities();
    	
    	TIntDoubleMap probMap  = new TIntDoubleHashMap();
    	
    	for (int i=0; i<keys.length; ++i)
    		probMap.put(keys[i], probs[i]);
    	
    	TObjectDoubleMap<T> retMap = new TObjectDoubleHashMap<T>(map.size()*2, 0.5f, sgt.getProbabilityForUnseen()/tCnt);
    	for(TObjectDoubleIterator<T> iter=map.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		retMap.put(iter.key(), probMap.get((int)iter.value()));
    	}

    	return retMap;
    }
    
    static void trimMap(Map<String, CountProb<String>> cntMap, int cnt) {
    	for (Iterator<Map.Entry<String, CountProb<String>>> iter=cntMap.entrySet().iterator(); iter.hasNext(); )
    		if (iter.next().getValue().getTotalCnt()<cnt)
    			iter.remove();
    }
    
    static void updateCnt(Map<String, CountProb<String>> map, String key, String innerKey) {
    	CountProb<String> cntProb = map.get(key);
        if (cntProb==null) {
        	cntProb = new CountProb<String>();
            map.put(key, cntProb);
        }
        cntProb.addCount(innerKey);
    }

    static void updateCnt(Map<String, CountProb<String>> map, String key, Collection<String> innerKeys) {
    	CountProb<String> cntProb = map.get(key);
        if (cntProb==null) {
        	cntProb = new CountProb<String>();
            map.put(key, cntProb);
        }
        cntProb.addCount(innerKeys);
    }
    
    String makeKey(PBInstance pred) {
    	return pred.getRoleset().substring(0,pred.getRoleset().lastIndexOf('.'));
    	//return pred.getRoleset().substring(0, pred.getRoleset().indexOf('.'));
    }
    
    String makeKey(PBArg arg) {
    	return AlignmentStat.convertLabel(arg.getLabel());
    }
    
    String makeKey(String... args) {
    	String retStr = null;
    	for (String arg:args)
    		if (retStr==null)
    			retStr = arg;
    		else
    			retStr = retStr+'_'+arg;
    	return retStr;
    }
    
    String[] splitKeys(String key) {
    	return key.split("_");
    }
    
    //String makeKey(String pred1, String pred2, String arg) {
    //	return pred1+'_'+pred2+'_'+arg;
    //}
    
    public void addSentence(SentencePair sentPair, Alignment[] alignments) {
    	for (Alignment alignment:alignments) {
            String srcPredKey = makeKey(alignment.getSrcPBInstance());
            String dstPredKey = makeKey(alignment.getDstPBInstance());
            
            updateCnt(srcPredDstPredProbMap, srcPredKey, dstPredKey);
            updateCnt(dstPredSrcPredProbMap, dstPredKey, srcPredKey);

            @SuppressWarnings("unchecked")
            List<String>[] dstArgAlignment = new List[alignment.getSrcPBInstance().getArgs().length];
            @SuppressWarnings("unchecked")
            List<String>[] srcArgAlignment = new List[alignment.getDstPBInstance().getArgs().length];
            
            for (ArgAlignmentPair argPair: alignment.getArgAlignmentPairs()) {
            	if (dstArgAlignment[argPair.srcArgIdx]==null)
            		dstArgAlignment[argPair.srcArgIdx]=new ArrayList<String>();
            	dstArgAlignment[argPair.srcArgIdx].add(AlignmentStat.convertLabel(alignment.getDstPBArg(argPair.dstArgIdx).getLabel()));
            	if (srcArgAlignment[argPair.dstArgIdx]==null)
            		srcArgAlignment[argPair.dstArgIdx]=new ArrayList<String>();
            	srcArgAlignment[argPair.dstArgIdx].add(AlignmentStat.convertLabel(alignment.getSrcPBArg(argPair.srcArgIdx).getLabel()));
            }
            for (int i=0; i<dstArgAlignment.length;++i)
            	if (dstArgAlignment[i]!=null) {
            		String srcArgKey = AlignmentStat.convertLabel(alignment.getSrcPBArg(i).getLabel());
            		updateCnt(srcArgDstArgProbMap, srcArgKey, dstArgAlignment[i]);
            		updateCnt(srcPredArgDstPredArgProbMap, makeKey(srcPredKey, srcArgKey, dstPredKey), dstArgAlignment[i]);
            		updateCnt(srcPredArgDstArgProbMap, makeKey(srcPredKey, srcArgKey), dstArgAlignment[i]);
            		updateCnt(dstPredSrcArgDstArgProbMap, makeKey(dstPredKey, srcArgKey), dstArgAlignment[i]);
            	}
            for (int i=0; i<srcArgAlignment.length;++i)
            	if (srcArgAlignment[i]!=null) {
            		String dstArgKey = AlignmentStat.convertLabel(alignment.getDstPBArg(i).getLabel());
            		updateCnt(dstArgSrcArgProbMap, dstArgKey, srcArgAlignment[i]);
            		updateCnt(dstPredArgSrcPredArgProbMap, makeKey(dstPredKey, dstArgKey, srcPredKey), srcArgAlignment[i]);
            		updateCnt(dstPredArgSrcArgProbMap, makeKey(dstPredKey, dstArgKey), srcArgAlignment[i]);
            		updateCnt(srcPredDstArgSrcArgProbMap, makeKey(srcPredKey, dstArgKey), srcArgAlignment[i]);
            	}
            
            /*
            for (ArgAlignmentPair argPair: alignment.getArgAlignmentPairs()) {
                String srcArgKey = AlignmentStat.convertLabel(alignment.getSrcPBArg(argPair.srcArgIdx).getLabel());
                String dstArgKey = AlignmentStat.convertLabel(alignment.getDstPBArg(argPair.dstArgIdx).getLabel());
                
                updateCnt(srcArgDstArgProb, srcArgKey, dstArgKey);
                updateCnt(dstArgSrcArgProb, dstArgKey, srcArgKey);
                
                updateCnt(srcPredArgDstPredArgProb, makeKey(srcPredKey, dstPredKey, srcArgKey), dstArgKey);
                updateCnt(dstPredArgSrcPredArgProb, makeKey(dstPredKey, srcPredKey, dstArgKey), srcArgKey);
            }*/
    	}
    	
    	for (PBInstance pbInst:sentPair.src.pbInstances) {
    		srcPredProb.addCount(makeKey(pbInst));
    		for (PBArg arg:pbInst.getArgs())
    			srcArgProb.addCount(makeKey(arg));
    	}
    	
    	for (PBInstance pbInst:sentPair.dst.pbInstances) {
    		dstPredProb.addCount(makeKey(pbInst));
    		for (PBArg arg:pbInst.getArgs())
    			dstArgProb.addCount(makeKey(arg));
    	}
    }
    
    CountProb<String> chooseBackoff(CountProb<String> prob1, CountProb<String> prob2, CountProb<String> backoff) {
    	if (prob1.getTotalCnt()<COUNT_THRESHOLD_2 && prob2.getTotalCnt()<COUNT_THRESHOLD_2)
    		return prob1.getTotalCnt()>prob2.getTotalCnt()?prob1:prob2;
    	if (prob1.getTotalCnt()<COUNT_THRESHOLD_2 && prob1.getTotalCnt()*2<=prob2.getTotalCnt())
    		return prob2;
    	if (prob2.getTotalCnt()<COUNT_THRESHOLD_2 && prob2.getTotalCnt()*2<=prob1.getTotalCnt())
    		return prob1;
    	
    	double prob1Dist = prob1.getDistance(backoff);
    	double prob2Dist = prob2.getDistance(backoff);
    	
    	return prob1Dist>prob2Dist?prob1:prob2;
    }
    
    public void makeProb() {

    	srcPredProb.makeMKYProb(srcPredDstPredProbMap);
    	dstPredProb.makeMKYProb(dstPredSrcPredProbMap);
    	
    	srcArgProb.makeMKYProb(srcArgDstArgProbMap);
    	dstArgProb.makeMKYProb(dstArgSrcArgProbMap);
    	
    	for (Map.Entry<String, CountProb<String>> entry:srcPredDstPredProbMap.entrySet())
    		entry.getValue().makeSGTProb(dstPredProb.getProbMap(), true);

    	for (Map.Entry<String, CountProb<String>> entry:dstPredSrcPredProbMap.entrySet())
    		entry.getValue().makeSGTProb(srcPredProb.getProbMap(), true);

    	for (Map.Entry<String, CountProb<String>> entry:srcArgDstArgProbMap.entrySet())
    		entry.getValue().makeSGTProb(dstArgProb.getProbMap(), false);
    	
    	for (String key:srcArgProb.getKeySet())
			if (!srcArgDstArgProbMap.containsKey(key))
				srcArgDstArgProbMap.put(key, dstArgProb);
    	
    	for (Map.Entry<String, CountProb<String>> entry:dstArgSrcArgProbMap.entrySet())
    		entry.getValue().makeSGTProb(srcArgProb.getProbMap(), false);

    	for (String key:dstArgProb.getKeySet())
			if (!dstArgSrcArgProbMap.containsKey(key))
				dstArgSrcArgProbMap.put(key, srcArgProb);
    	
    	// these all have reasonable back-offs, so just use those if we have very few counts
    	trimMap(srcPredArgDstArgProbMap, COUNT_THRESHOLD);
    	trimMap(dstPredSrcArgDstArgProbMap, COUNT_THRESHOLD);
    	trimMap(dstPredArgSrcArgProbMap, COUNT_THRESHOLD);
    	trimMap(srcPredDstArgSrcArgProbMap, COUNT_THRESHOLD);
    	
    	trimMap(srcPredArgDstPredArgProbMap, COUNT_THRESHOLD);
    	trimMap(dstPredArgSrcPredArgProbMap, COUNT_THRESHOLD);
    	
    	for (Map.Entry<String, CountProb<String>> entry:srcPredArgDstArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		entry.getValue().makeBackoffMixProb(srcArgDstArgProbMap.get(keys[1]).getProbMap());
    	}
    	for (Map.Entry<String, CountProb<String>> entry:dstPredSrcArgDstArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		entry.getValue().makeBackoffMixProb(srcArgDstArgProbMap.get(keys[1]).getProbMap());
    	}
    	
    	for (Map.Entry<String, CountProb<String>> entry:dstPredArgSrcArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		entry.getValue().makeBackoffMixProb(dstArgSrcArgProbMap.get(keys[1]).getProbMap());
    	}
    	for (Map.Entry<String, CountProb<String>> entry:srcPredDstArgSrcArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		entry.getValue().makeBackoffMixProb(dstArgSrcArgProbMap.get(keys[1]).getProbMap());
    	}
    	
    	for (Map.Entry<String, CountProb<String>> entry:srcPredArgDstPredArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		CountProb<String> backoff = chooseBackoff(srcPredArgDstArgProbMap.get(makeKey(keys[0], keys[1])), 
    				dstPredSrcArgDstArgProbMap.get(makeKey(keys[2],keys[1])), 
    				srcArgDstArgProbMap.get(keys[1]));
    		entry.getValue().makeBackoffMixProb(backoff.getProbMap());
    	}
    	for (Map.Entry<String, CountProb<String>> entry:dstPredArgSrcPredArgProbMap.entrySet()) {
    		String[] keys = splitKeys(entry.getKey());
    		CountProb<String> backoff = chooseBackoff(dstPredArgSrcArgProbMap.get(makeKey(keys[0], keys[1])), 
    				srcPredDstArgSrcArgProbMap.get(makeKey(keys[2],keys[1])), 
    				dstArgSrcArgProbMap.get(keys[1]));
    		entry.getValue().makeBackoffMixProb(backoff.getProbMap());
    	}

    }
    
    // return P(dst_p|src_p) or P(src_p|dst_p)
    public double getPredProb(boolean isSrc, PBInstance inst1, PBInstance inst2, boolean weighted) {
    	String outerKey = makeKey(inst1);
    	String innerKey = makeKey(inst2);
    	CountProb<String> probs = isSrc?srcPredDstPredProbMap.get(outerKey):dstPredSrcPredProbMap.get(outerKey);
    	if (probs==null) 
    		probs = isSrc?dstPredProb:srcPredProb;
    	
    	return probs.getProb(innerKey, weighted);
    }
    
    // return P(dst_a|src_p,dst_p,src_a) or P(src_a|src_p,dst_p,dst_a)
    public double[][] getArgProbs(boolean isSrc, PBInstance inst1, PBInstance inst2, boolean weighted) {
    	
    	String pred1Key = makeKey(inst1);
    	String pred2Key = makeKey(inst1);
    	
    	double[][] argProbs = new double[inst1.getArgs().length][inst2.getArgs().length];
    	
    	for (int i=0; i<inst1.getArgs().length; ++i) {
    		String arg1Key = makeKey(inst1.getArgs()[i]);
    		String outerKey = makeKey(pred1Key, arg1Key, pred2Key);
    		CountProb<String> probs = isSrc?srcPredArgDstPredArgProbMap.get(outerKey):dstPredArgSrcPredArgProbMap.get(outerKey);
    		if (probs==null) {
    			CountProb<String> prob1 = isSrc?srcPredArgDstArgProbMap.get(makeKey(pred1Key, arg1Key)):dstPredArgSrcArgProbMap.get(makeKey(pred1Key, arg1Key));
    			CountProb<String> prob2 = isSrc?dstPredSrcArgDstArgProbMap.get(makeKey(pred2Key, arg1Key)):srcPredDstArgSrcArgProbMap.get(makeKey(pred2Key, arg1Key));
    			CountProb<String> backoff = isSrc?srcArgDstArgProbMap.get(arg1Key):dstArgSrcArgProbMap.get(arg1Key);
    			
    			if (backoff==null)
    				probs = isSrc?dstArgProb:srcArgProb;
    			else if (prob1==null && prob2==null)
    				probs = backoff;
    			else if (prob1==null && prob2!=null)
    				probs = prob2;   			
        		else if (prob1!=null && prob2==null)
        			probs = prob1;
        		else {
        			probs = chooseBackoff(prob1, prob2, backoff);
    				// cache the choice
        			if (isSrc)
    					srcPredArgDstPredArgProbMap.put(outerKey,probs);
    				else
    					dstPredArgSrcPredArgProbMap.put(outerKey,probs);
    			}
    		}
    		
    		for (int j=0; j<argProbs[i].length; ++j)
    			argProbs[i][j] = probs.getProb(makeKey(inst2.getArgs()[j]), weighted);
    	}
    	
    	return argProbs;
    }
}
