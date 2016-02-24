package edu.colorado.clear.srl.align;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import edu.colorado.clear.srl.util.SimpleGoodTuring;
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
	
    public static <T> TObjectDoubleMap<T> smoothBackoffMix(TObjectIntMap<T> cntMap, long totalCnt, TObjectDoubleMap<T> backoffProbMap, double rate) {
    	
    	int cntSum=0;
    	for (TObjectIntIterator<T> iter=cntMap.iterator(); iter.hasNext();) {
    		iter.advance();
    		cntSum += iter.value();
    	}
    	
    	if (rate<=0)
    		//rate = 1-0.9/(Math.log10(cntSum)+1);
    		rate = 1-0.95/(Math.log1p(cntSum)+1);
    	TObjectDoubleMap<T> wholeProbMap = new TObjectDoubleHashMap<T>();
    	
    	for (TObjectDoubleIterator<T> iter=backoffProbMap.iterator(); iter.hasNext();) {
    		iter.advance();
    		wholeProbMap.put(iter.key(), iter.value()*(1-rate));
    	}
    	for (TObjectIntIterator<T> iter=cntMap.iterator(); iter.hasNext();) {
    		iter.advance();
    		wholeProbMap.adjustValue(iter.key(), iter.value()*rate/totalCnt);
    	}
    	
    	return wholeProbMap;
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
    	
    	if (cntMap.size()<1000) {
    		// too small to apply SGT smoothing, default to add-n smoothing
    		// TODO: need a principled way to estimate n
    		return smoothAddN(keyedCntMap, keyedProbMap, unseenCnt, Math.sqrt(tCnt/(tCnt+unseenCnt)));
    		
    		
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
    		keyedProbMap.put(iter.key(), probMap.get(iter.value()));
    	}

    	return unseenCnt>0?unseenProb:0;
    }
	
	transient TObjectIntMap<T> cntMap;

	long totalCnt = 0;
	
	TObjectDoubleMap<T> probMap;	
	TObjectDoubleMap<T> unseenProbMap;
	
	double weightFactor = 0;
	double unseenFactor = 0;
	
	public CountProb() {
		this(null);
	}
	
	public CountProb(TObjectDoubleMap<T> unseenProbMap) {
		cntMap = new TObjectIntHashMap<T>();
		totalCnt = 0;
		this.unseenProbMap = unseenProbMap;
	}
	
	public void addCount(Collection<T> dstKeys) {
		for (T dstKey:dstKeys)
			cntMap.adjustOrPutValue(dstKey, 1, 1);
		totalCnt++;
	}

	public void addCount(T dstKey) {
		cntMap.adjustOrPutValue(dstKey, 1, 1);
		totalCnt++;
	}
	
	double getDistance(CountProb<T> rhs) {
		double distance = 0;
		for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext();) {
    		iter.advance();
    		distance += Math.pow(iter.value()-rhs.probMap.get(iter.key()), 2);
		}
		return distance;
	}
	
	public Set<T> getKeySet() {
		if (unseenProbMap==null)
			return probMap.keySet();
		Set<T> keySet = new HashSet<T>(unseenProbMap.keySet());
		keySet.addAll(probMap.keySet());
		return keySet;
	}
	
	public double getProb(T key, boolean weighted) {
		double prob = probMap.get(key);

		if (prob==0 && unseenProbMap!=null)
			prob = unseenProbMap.get(key)*unseenFactor;

		return weighted?(prob==0?1:prob*weightFactor):prob;
	}
	
	public long getTotalCnt() {
		return totalCnt;
	}
	
	public TObjectDoubleMap<T> getProbMap() {
		if (unseenProbMap==null || unseenProbMap.isEmpty() || unseenFactor==0)
			return probMap;
		
		TObjectDoubleMap<T> wholeProbMap = new TObjectDoubleHashMap<T>(probMap);
		for (TObjectDoubleIterator<T> iter=unseenProbMap.iterator(); iter.hasNext(); ) {
			iter.advance();
			wholeProbMap.putIfAbsent(iter.key(), iter.value()*unseenFactor);
		}
		
		return wholeProbMap;
	}
	
	public void makeBackoffMixProb(TObjectDoubleMap<T> backoffProbMap) {
		probMap = smoothBackoffMix(cntMap, totalCnt, backoffProbMap, -1);
		weightFactor = 0;
    	for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext(); ) {
			iter.advance();
			weightFactor += iter.value()*iter.value();
    	}
    	weightFactor = weightFactor==0?0:1/weightFactor;
	}

	public void makeMKYProb(Map<String, CountProb<String>> extMap) {
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
			if (total>1+1e-8 || total<1e-8)
				System.err.println(total);
		}
	}
	
	public void makeSGTProb(TObjectDoubleMap<T> unseenProbMap, boolean cache) {

		if (cache)
			this.unseenProbMap = unseenProbMap;
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
    	
    	if (unseenFactor==0)
    		this.unseenProbMap = null;

    	weightFactor = 0;
    	for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext(); ) {
			iter.advance();
			weightFactor += iter.value()*iter.value();
    	}
    	if (unseenFactor!=0) {
	    	for (TObjectDoubleIterator<T> iter=unseenProbMap.iterator(); iter.hasNext(); ) {
				iter.advance();
				if (!cntMap.containsKey(iter.key())) {
					if (!cache)
						probMap.put(iter.key(), unseenFactor*iter.value());
					weightFactor += Math.pow(unseenFactor*iter.value(), 2);
				}	
			}
    	}
    	weightFactor = weightFactor==0?0:1/weightFactor;

    	/*
		long totalInstances = 0;
		for (TObjectIntIterator<T> iter=cntMap.iterator(); iter.hasNext();) {
			iter.advance();
			totalInstances+=iter.value();
		}
		if (totalInstances!=totalCnt) {
			double factor = 1.0*totalInstances/totalCnt;
			for (TObjectDoubleIterator<T> iter=probMap.iterator(); iter.hasNext(); ) {
				iter.advance();
				iter.setValue(iter.value()*factor);
	    	}
			unseenFactor *= factor;
		}*/
	}
    
    @Override
	public String toString() {
    	if (probMap!=null)
    		return probMap.toString();
    	return cntMap.toString();
    }
    
}
