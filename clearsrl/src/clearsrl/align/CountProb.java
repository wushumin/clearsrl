package clearsrl.align;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import clearsrl.util.SimpleGoodTuring;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

public class CountProb<T> implements Serializable {
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    
	transient TObjectIntMap<T> cntMap;
	transient TObjectIntMap<T> unseenCntMap;
	
	TObjectDoubleMap<T> probMap;
	TObjectDoubleMap<T> unseenProbMap;
	
	TObjectDoubleMap<T> wholeProbMap;
	
	double weightFactor = 0;
	double unseenFactor = 0;

	boolean dirty = false;
	boolean unseenDirty = false;
	
	public CountProb() {
		this(null);
	}
	
	public CountProb(TObjectDoubleMap<T> unseenProbMap) {
		cntMap = new TObjectIntHashMap<T>();
		unseenCntMap = new TObjectIntHashMap<T>();
		this.unseenProbMap = unseenProbMap;
	}
	
	public void addCount(T dstKey) {
		addCount(dstKey, false);
	}
	
	public void addCount(T dstKey, boolean unseen) {
		if (unseen) {
			unseenCntMap.adjustOrPutValue(dstKey, 1, 1);
			unseenDirty = true;
		} else {
			cntMap.adjustOrPutValue(dstKey, 1, 1);
			dirty = true;
		}
	}
	
	public void setUnseenProbMap(TObjectDoubleMap<T> unseenProbMap) {
		this.unseenProbMap = unseenProbMap;
	}
	
	public TObjectDoubleMap<T> getWholeProbMap() {
		if (wholeProbMap!=null)
			return wholeProbMap;
		if (unseenProbMap==null || unseenProbMap.isEmpty() || unseenFactor==0)
			return probMap;
		
		wholeProbMap = probMap;
		probMap = null;
		for (TObjectDoubleIterator<T> iter=unseenProbMap.iterator(); iter.hasNext(); ) {
			iter.advance();
			wholeProbMap.putIfAbsent(iter.key(), iter.value()*unseenFactor);
		}
		if (!unseenCntMap.isEmpty())
			unseenProbMap=null;
		
		return wholeProbMap;
	}
	
	public Set<T> getKeySet() {
		if (dirty || unseenDirty)
			makeSGTProb();
		if (wholeProbMap!=null)
			return wholeProbMap.keySet();
		if (unseenFactor==0)
			return probMap.keySet();
		Set<T> keySet = new HashSet<T>(unseenProbMap.keySet());
		keySet.addAll(probMap.keySet());
		return keySet;
	}
	
	public double getProb(T key, boolean weighted) {
		if (dirty || unseenDirty)
			makeSGTProb();
		double prob = 0;
		if (wholeProbMap!=null)
			prob = wholeProbMap.get(key);
		else if (probMap==null)
			return 0;
		else {
			prob = probMap.get(key);
			if (prob==0 && unseenFactor!= 0)
				prob = unseenProbMap.get(key)*unseenFactor;
		}
		return weighted?prob*weightFactor:prob;
	}
	
	public void makeMKYProb(Map<String, CountProb<String>> extMap) {
		if (!dirty) return;
		TObjectIntMap<T> cntofCntMap = new TObjectIntHashMap<T>();

		int unseenCnt = 0;
		for (T key:cntMap.keySet()) {
			CountProb<String> cntProbMap = extMap.get(key);
			if (cntProbMap==null)
				unseenCnt++;
			else
				cntofCntMap.put(key, cntProbMap.cntMap.size());
		}
		
		double unseenProb = smoothSGT(cntofCntMap, probMap=new TObjectDoubleHashMap<T>(), unseenCnt);
		if (unseenCnt>0) {
			unseenProb /= unseenCnt;
			for (T key:cntMap.keySet())
				if (!cntofCntMap.containsKey(key))
					probMap.put(key, unseenProb);
		}
		{
			//debug
			double total = 0;
			for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext();) {
				iter.advance();
				total += iter.value();
			}
			if (total>1+0.0001 || total<1-0.0001)
				System.err.println(total);
		}
		dirty = false;
	}
	
	public void makeSGTProb() {
		if (unseenDirty) {
			smoothSGT(unseenCntMap, unseenProbMap=new TObjectDoubleHashMap<T>(), 0);
			unseenDirty = false;
		}
		if (!dirty) return;

		double unseenProbSum = 0;
		int unseenCnt = 0;
		if (unseenProbMap!=null)
			for (TObjectDoubleIterator<T> iter=unseenProbMap.iterator(); iter.hasNext(); ) {
				iter.advance();
				if (!cntMap.containsKey(iter.key())) {
					unseenProbSum += iter.value();
					unseenCnt++;
				}
			}
		
		unseenFactor = smoothSGT(cntMap, probMap=new TObjectDoubleHashMap<T>(), unseenCnt);
    	if (unseenProbSum!=0)
    		unseenFactor /= unseenProbSum;
    	
    	if (unseenFactor==0 && !unseenCntMap.isEmpty())
    		unseenProbMap = null;
    		
    	weightFactor = 0;
    	for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext(); ) {
			iter.advance();
			weightFactor += iter.value()*iter.value();
    	}
    	if (unseenFactor!=0)
	    	for (TObjectDoubleIterator<T> iter=unseenProbMap.iterator(); iter.hasNext(); ) {
				iter.advance();
				if (!cntMap.containsKey(iter.key()))
					weightFactor += Math.pow(unseenFactor*iter.value(), 2);
			}
    	weightFactor = weightFactor==0?0:1/weightFactor;

    	wholeProbMap = null;

    	dirty = false;
	}
	
	public static <T> double smoothAddN(TObjectIntMap<T> keyedCntMap, TObjectDoubleMap<T> keyedProbMap, int unseenCnt, double N) {
		
		double unseenTotal = unseenCnt*N;
		
		double total = unseenTotal;
		for(TObjectIntIterator<T> iter=keyedCntMap.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		total += iter.value()+N;
    	}
		
		for(TObjectIntIterator<T> iter=keyedCntMap.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		keyedProbMap.put(iter.key(), (iter.value()+N)/total);
    	}
		
		return unseenTotal/total;
	}
	
    public static <T> double smoothSGT(TObjectIntMap<T> keyedCntMap, TObjectDoubleMap<T> keyedProbMap, int unseenCnt) {
    	keyedProbMap.clear();
    	if (keyedCntMap.isEmpty())
    		return unseenCnt>0?1:0;
    	
    	double unseenProb = 0;
    	
    	TIntIntMap cntMap = new TIntIntHashMap();
    	double tCnt = 0;
    	for(TObjectIntIterator<T> iter=keyedCntMap.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		cntMap.adjustOrPutValue(iter.value(), 1, 1);
    		tCnt += iter.value();
    	}
    	
    	int[] keys = cntMap.keys(new int[cntMap.size()]);
    	Arrays.sort(keys);
    	int[] values = new int[keys.length];
    	for (int i=0; i<keys.length; ++i)
    		values[i] = cntMap.get(keys[i]);
    	
    	if (cntMap.size()<5) {
    		// too small to apply SGT smoothing, default to add-n smoothing
    		// TODO: need a principled way to estimate n
    		return smoothAddN(keyedCntMap, keyedProbMap, unseenCnt, 0.5*Math.sqrt(tCnt/(tCnt+unseenCnt)));
    		
    		
    		/*
    		double factor = 1/tCnt;
    		if (unseenCnt>0 && keys[0]==1) {
    			unSeenProb = values[0]/tCnt;
    			factor *= 1-unSeenProb;
    		}

    		for(TObjectIntIterator<T> iter=keyedCntMap.iterator(); iter.hasNext(); ) {
        		iter.advance();
        		keyedProbMap.put(iter.key(), iter.value()*factor);
        	}
    		return unSeenProb;*/
    	}
	
    	SimpleGoodTuring sgt = new SimpleGoodTuring(keys, values);
    	double[] probs = sgt.getProbabilities();
    	
    	unseenProb = sgt.getProbabilityForUnseen();

    	double factor = 1;
    	if (unseenCnt==0 && unseenProb>0) {
    		// if we are not computing unseen probability, add it back
    		factor = 1/(1-unseenProb);
    		for (int i=0; i<probs.length; ++i)
    			probs[i] *= factor;
    	} else if (unseenCnt>0) {
    		// the unseen probability estimate of SGT is probably bad, adjust
    		if (unseenProb==0) {
    			unseenProb = unseenCnt*probs[0]/(keys[0]+1);
    			factor = 1/(1+unseenProb);
    			unseenProb *= factor;
    		} else if (probs[0]*(keys[0]+1)<unseenProb/unseenCnt) {
    			factor = unseenProb;
    			unseenProb = unseenCnt*probs[0]/(keys[0]+1);
    			factor = 1/(1+unseenProb-factor);
    			unseenProb *= factor;	
    		}
    	}
    			
    	if (factor!=1)
    		for (int i=0; i<probs.length; ++i)
    			probs[i] *= factor;
    	
    	TIntDoubleMap probMap  = new TIntDoubleHashMap();
    	for (int i=0; i<keys.length; ++i)
    		probMap.put(keys[i], probs[i]);
    	
    	keyedProbMap.clear();
    	for(TObjectIntIterator<T> iter=keyedCntMap.iterator(); iter.hasNext(); ) {
    		iter.advance();
    		keyedProbMap.put(iter.key(), probMap.get((int)iter.value()));
    	}

    	return unseenCnt>0?unseenProb:0;
    }
    
    public String toString() {
    	if (wholeProbMap!=null)
    		return wholeProbMap.toString();
    	if (probMap!=null)
    		return probMap.toString();
    	return cntMap.toString();
    }
    
}
