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
    
	SortedMap<String, CountProb<String>> srcPredDstPredProb;
	SortedMap<String, CountProb<String>> dstPredSrcPredProb;
    
	SortedMap<String, CountProb<String>> srcArgDstArgProb;
	SortedMap<String, CountProb<String>> dstArgSrcArgProb;
    
	SortedMap<String, CountProb<String>> srcPredArgDstPredArgProb;
	SortedMap<String, CountProb<String>> dstPredArgSrcPredArgProb;

    CountProb<String> srcPredProb;
    CountProb<String> dstPredProb;
    
    CountProb<String> srcArgProb;
    CountProb<String> dstArgProb;
    
    public AlignmentProb() {

    	srcPredDstPredProb = new TreeMap<String, CountProb<String>>();
        dstPredSrcPredProb = new TreeMap<String, CountProb<String>>();
        
        srcArgDstArgProb = new TreeMap<String, CountProb<String>>();
        dstArgSrcArgProb = new TreeMap<String, CountProb<String>>();
        
        srcPredArgDstPredArgProb = new TreeMap<String, CountProb<String>>();
        dstPredArgSrcPredArgProb = new TreeMap<String, CountProb<String>>();
        
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
    
    static void updateCnt(Map<String, CountProb<String>> map, String key, String innerKey) {
    	CountProb<String> cntProb = map.get(key);
        if (cntProb==null) {
        	cntProb = new CountProb<String>();
            map.put(key, cntProb);
        }
        cntProb.addCount(innerKey, false);
    }

    static void updateCnt(Map<String, CountProb<String>> map, String key, Collection<String> innerKeys) {
    	CountProb<String> cntProb = map.get(key);
        if (cntProb==null) {
        	cntProb = new CountProb<String>();
            map.put(key, cntProb);
        }
        cntProb.addCount(innerKeys, false);
    }
    
    String makeKey(PBInstance pred) {
    	return pred.getRoleset().substring(0,pred.getRoleset().lastIndexOf('.'));
    	//return pred.getRoleset().substring(0, pred.getRoleset().indexOf('.'));
    }
    
    String makeKey(PBArg arg) {
    	return AlignmentStat.convertLabel(arg.getLabel());
    }
    
    String makeKey(String pred1, String pred2, String arg) {
    	return pred1+'_'+pred2+'_'+arg;
    }
    
    public void addSentence(SentencePair sentPair, Alignment[] alignments) {
    	for (Alignment alignment:alignments) {
            String srcPredKey = makeKey(alignment.getSrcPBInstance());
            String dstPredKey = makeKey(alignment.getDstPBInstance());
            
            updateCnt(srcPredDstPredProb, srcPredKey, dstPredKey);
            updateCnt(dstPredSrcPredProb, dstPredKey, srcPredKey);

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
            		updateCnt(srcArgDstArgProb, srcArgKey, dstArgAlignment[i]);
            		updateCnt(srcPredArgDstPredArgProb, makeKey(srcPredKey, dstPredKey, srcArgKey), dstArgAlignment[i]);
            	}
            for (int i=0; i<srcArgAlignment.length;++i)
            	if (srcArgAlignment[i]!=null) {
            		String dstArgKey = AlignmentStat.convertLabel(alignment.getDstPBArg(i).getLabel());
            		updateCnt(dstArgSrcArgProb, dstArgKey, srcArgAlignment[i]);
            		updateCnt(dstPredArgSrcPredArgProb, makeKey(dstPredKey, srcPredKey, dstArgKey), srcArgAlignment[i]);
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

    public void makeProb() {

    	srcPredProb.makeMKYProb(srcPredDstPredProb);
    	dstPredProb.makeMKYProb(dstPredSrcPredProb);
    	
    	srcArgProb.makeMKYProb(srcArgDstArgProb);
    	dstArgProb.makeMKYProb(dstArgSrcArgProb);
    	
    	for (Map.Entry<String, CountProb<String>> entry:srcPredDstPredProb.entrySet()) {
    		entry.getValue().setUnseenProbMap(dstPredProb.getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}
    	for (Map.Entry<String, CountProb<String>> entry:dstPredSrcPredProb.entrySet()) {
    		entry.getValue().setUnseenProbMap(srcPredProb.getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}

    	for (Map.Entry<String, CountProb<String>> entry:srcArgDstArgProb.entrySet()) {
    		entry.getValue().setUnseenProbMap(dstArgProb.getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}
    	for (String key:srcArgProb.getKeySet())
			if (!srcArgDstArgProb.containsKey(key))
				srcArgDstArgProb.put(key, dstArgProb);
    	
    	for (Map.Entry<String, CountProb<String>> entry:dstArgSrcArgProb.entrySet()) {
    		entry.getValue().setUnseenProbMap(srcArgProb.getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}
    	for (String key:dstArgProb.getKeySet())
			if (!dstArgSrcArgProb.containsKey(key))
				dstArgSrcArgProb.put(key, srcArgProb);
	
    	for (Map.Entry<String, CountProb<String>> entry:srcPredArgDstPredArgProb.entrySet()) {
    		String srcArg = entry.getKey().substring(entry.getKey().lastIndexOf('_')+1);
    		entry.getValue().setUnseenProbMap(srcArgDstArgProb.get(srcArg).getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}
    	for (Map.Entry<String, CountProb<String>> entry:dstPredArgSrcPredArgProb.entrySet()) {
    		String dstArg = entry.getKey().substring(entry.getKey().lastIndexOf('_')+1);    		
    		entry.getValue().setUnseenProbMap(dstArgSrcArgProb.get(dstArg).getWholeProbMap());
    		entry.getValue().makeSGTProb();
    	}

    }
    
    // return P(dst_p|src_p) or P(src_p|dst_p)
    public double getPredProb(boolean isSrc, PBInstance inst1, PBInstance inst2, boolean weighted) {
    	String outerKey = makeKey(inst1);
    	String innerKey = makeKey(inst2);
    	CountProb<String> probs = isSrc?srcPredDstPredProb.get(outerKey):dstPredSrcPredProb.get(outerKey);
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
    		String outerKey = makeKey(pred1Key, pred2Key, arg1Key);
    		CountProb<String> probs = isSrc?srcPredArgDstPredArgProb.get(outerKey):dstPredArgSrcPredArgProb.get(outerKey);
    		if (probs==null)
    			probs = isSrc?srcArgDstArgProb.get(arg1Key):dstArgSrcArgProb.get(arg1Key);
    		
    		for (int j=0; i<inst2.getArgs().length; ++j)
    			argProbs[i][j] = probs.getProb(makeKey(inst2.getArgs()[j]), weighted);
    	}
    	
    	return argProbs;
    }
}
