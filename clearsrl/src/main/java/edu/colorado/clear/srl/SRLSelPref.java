package edu.colorado.clear.srl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.LanguageUtil;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.TObjectFloatMap;
import gnu.trove.map.hash.TObjectFloatHashMap;

public abstract class SRLSelPref implements Serializable{
	
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    

    transient LanguageUtil langUtil;
    
	protected SRLSelPref() {
	}

	public abstract void initialize(Properties props) throws IOException; 
	
	public void setLangUtil(LanguageUtil langUtil) {
		this.langUtil = langUtil;
	}
	
	public abstract TObjectFloatMap<String> getSP(String headword, boolean discount);
	
	public List<TObjectFloatMap<String>> getSP(List<String> headwords, boolean discount) {
		List<TObjectFloatMap<String>> ret = new ArrayList<TObjectFloatMap<String>>();
		for (String headword:headwords)
			ret.add(getSP(headword, discount));
		return ret;
	}
	
	public TObjectFloatMap<String> getSP(TBNode node, boolean discount) {
		String headword = getArgHeadword(node);
		if (headword==null)
			return null;
		return getSP(headword, discount);
	}
	
	public abstract List<TObjectFloatMap<String>> getSP(String predKey, List<String> headWords, boolean discount);

	public List<TObjectFloatMap<String>> getSP(TBNode predNode, String roleset, List<TBNode> nodeList, boolean discount) {
		List<String> headwords = new ArrayList<String>();
		for (TBNode node:nodeList)
			headwords.add(getArgHeadword(node));
		
		return getSP(getPredicateKey(predNode, roleset), headwords, discount);
	}

	public String getArgHeadword(TBNode node) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return head.getWord();
	}

	public String getPredicateKey(TBNode predNode, String roleset) {
		if (roleset!=null)
			return roleset.substring(0, roleset.lastIndexOf('.'));
		return langUtil.findStems(predNode).get(0);
	}

	public static TBNode getHeadNode(TBNode node) {
		TBNode head = node.getHead();
		if (head.getPOS().equals("PU"))
			return null;
		if (node.getPOS().equals("PP")) {
			for (TBNode child:node.getChildren())
            	if (child.getHead()!=node.getHead()) {
            		if (child.getPOS().equals("LCP")) {
            			for (TBNode grandChild:child.getChildren())
            				if (grandChild.getHead()!=child.getHead()) {
            					head = grandChild.getHead();
            					break;
            				}
            		} else
            			head = child.getHead();
            		break;
            	}
		}
		return head;
	}

	
	public static Map<String, Map<String, TObjectFloatMap<String>>> readTrainingCount(File file) {
		Map<String, Map<String, TObjectFloatMap<String>>> db = new HashMap<String, Map<String, TObjectFloatMap<String>>>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))){
			String line = null;
			Map<String, TObjectFloatMap<String>> innerMap=null;
			String predKey = null;
			while ((line=reader.readLine())!=null) {
				String[] tokens = line.trim().split(" ");
				if (tokens.length<3) continue;
				if (!tokens[0].equals(predKey)) {
					predKey = tokens[0];
					innerMap = db.get(predKey);
					if (innerMap==null)
						db.put(predKey,innerMap=new HashMap<String, TObjectFloatMap<String>>());
				}
				TObjectFloatMap<String> cntMap = innerMap.get(tokens[1]);
				if (cntMap==null)
					innerMap.put(tokens[1].intern(),cntMap=new TObjectFloatHashMap<String>());
				for (int i=2; i<tokens.length; ++i) {
					int splitter = tokens[i].indexOf(':');
					float cnt = Float.parseFloat(tokens[i].substring(splitter+1));
					cntMap.adjustOrPutValue(tokens[i].substring(0,splitter), cnt, cnt);
				}	
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return db;
	}
	
	public abstract void makeSP(Map<String, Map<String, TObjectFloatMap<String>>> inputCntMap);
	
	static float computeCosine(float[] lhs, float[] rhs) {

		return 0;
		/*
		if (lhs.length!=rhs.length || lhs[lhs.length-1]==0 || rhs[rhs.length-1]==0)
			return 0f;
		double val = 0;
		
		for (int i=0; i<lhs.length-1; ++i)
			val+=lhs[i]*rhs[i];
		return (float)Math.pow(val, 200);
		/*
		for (int i=0; i<lhs.length-1; ++i) {
			float tmp=lhs[i]-rhs[i];
			val+=tmp*tmp;
		}
		return (float)Math.exp(1000*(val-1));*/
	}

	protected TObjectFloatMap<String> getSP(String headword, Map<String, float[]> spModelMap, Map<String, TObjectFloatMap<String>> cntMap, boolean discount) {
		TObjectFloatMap<String> sp = new TObjectFloatHashMap<String>();
		float[] headwordSP = spModelMap.get(headword);
		if (headwordSP==null)
			headwordSP = spModelMap.get(headword.toLowerCase());
		
		TObjectFloatMap<String> tCntMap = cntMap.get(null);
		
		for (Map.Entry<String, TObjectFloatMap<String>> entry:cntMap.entrySet()) {
			if (entry.getKey()==null)
				continue;
			
			float factor = 1;
			float highSPVal = Float.NEGATIVE_INFINITY;
			for (TObjectFloatIterator<String> iter=entry.getValue().iterator(); iter.hasNext(); ) {
				iter.advance();
				if (headword.equals(iter.key())) {
					if (discount) {
						if (iter.value()<=1)
							continue;
						factor=(iter.value()-1)/(tCntMap.get(iter.key())-1);
					} else
						factor=iter.value()/tCntMap.get(iter.key());
					highSPVal = 1f;
					break;
				}
				if (headwordSP==null)
					continue;
				float[] cntwordSP = spModelMap.get(iter.key());
				if (cntwordSP==null)
					continue;
				float spVal = computeCosine(headwordSP, cntwordSP);
				if (spVal>highSPVal) {
					highSPVal = spVal;
					factor = iter.value()/tCntMap.get(iter.key());
				}
			}
			if (highSPVal>0)
				sp.put(entry.getKey(), highSPVal*factor);
		}
			
		return sp.isEmpty()?null:sp;
	}
	
	/*
	protected TObjectFloatMap<String> getSP(String headword, Map<String, float[]> spModelMap, Map<String, TObjectFloatMap<String>> cntMap, boolean discount) {
		TObjectFloatMap<String> sp = new TObjectFloatHashMap<String>();
		float[] headwordSP = spModelMap.get(headword);
		
		TObjectFloatMap<String> tCntMap = cntMap.get(null);
		
		for (Map.Entry<String, TObjectFloatMap<String>> entry:cntMap.entrySet()) {
			if (entry.getKey()==null)
				continue;
			
			float total = 0;
			float spVal = 0;
			for (TObjectFloatIterator<String> iter=entry.getValue().iterator(); iter.hasNext(); ) {
				iter.advance();
				if (headword.equals(iter.key())) {
					if (discount) {
						if (iter.value()<=1)
							continue;
						spVal+=(iter.value()-1)/(tCntMap.get(iter.key())-1);
					} else {
						spVal+=iter.value()/tCntMap.get(iter.key());
					}
					++total;
					continue;
				}
				if (headwordSP==null)
					continue;
				float[] cntwordSP = spModelMap.get(iter.key());
				if (cntwordSP==null)
					continue;
				float simScore = computeCosine(headwordSP, cntwordSP);
				if (simScore!=0) {
					spVal += iter.value()/tCntMap.get(iter.key())*simScore;
					total+=simScore;
				}
			}
			if (total!=0 && spVal!=0)
				sp.put(entry.getKey(), spVal/total);
		}
			
		return sp.isEmpty()?null:sp;
	}
*/	
	
	public static String getHighLabel(TObjectFloatMap<String> sp) {
		if (sp==null)
			return null;
		String ret = null;
		float highSPVal = Float.MIN_VALUE;
		for (TObjectFloatIterator<String> iter=sp.iterator(); iter.hasNext(); ) {
			iter.advance();
			if (iter.value()>=highSPVal) {
				highSPVal=iter.value();
				ret = iter.key();
			}		
		}
		return ret;
	}
	
}
