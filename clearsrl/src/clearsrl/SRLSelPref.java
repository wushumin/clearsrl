package clearsrl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import clearcommon.treebank.TBNode;
import clearcommon.util.LanguageUtil;
import gnu.trove.map.TObjectFloatMap;
import gnu.trove.map.hash.TObjectFloatHashMap;

public abstract class SRLSelPref implements Serializable{
	
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    
	Map<String, Map<String, TObjectFloatMap<String>>> predSPDB;
	Map<String, TObjectFloatMap<String>> argSPDB;

	SRLSelPref() {
		predSPDB = new HashMap<String, Map<String, TObjectFloatMap<String>>>();
		argSPDB = new HashMap<String, TObjectFloatMap<String>>();
	}
	
	public TObjectFloatMap<String> getSP(TBNode node, LanguageUtil langUtil) {
		String headword = getArgHeadword(node, langUtil);
		return headword==null?null:argSPDB.get(headword);
	}
	
	public List<TObjectFloatMap<String>> getSP(TBNode predNode, String roleset, List<TBNode> nodeList, LanguageUtil langUtil) {
		Map<String, TObjectFloatMap<String>> argDB = predSPDB.get(getPredicateKey(predNode, roleset, langUtil));
		
		List<TObjectFloatMap<String>> retList = new ArrayList<TObjectFloatMap<String>>(nodeList.size());
		for (TBNode node:nodeList) {
			String headword = getArgHeadword(node, langUtil);
			if (headword==null)
				retList.add(null);
			TObjectFloatMap<String> sp = null;
			if (argDB != null)
				sp = argDB.get(headword);
			if (sp==null)
				sp = argSPDB.get(headword);
			retList.add(sp);
		}
		
		return retList;
	}

	public String getPredicateKey(TBNode predNode, String roleset, LanguageUtil langUtil) {
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
	
	public static String getArgHeadword(TBNode node, LanguageUtil langUtil) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return head.getWord().toLowerCase();
	}
	
	public Map<String, Map<String, TObjectFloatMap<String>>> readTrainingDB(File file) {
		Map<String, Map<String, TObjectFloatMap<String>>> db = new HashMap<String, Map<String, TObjectFloatMap<String>>>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))){
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
					innerMap.put(tokens[1],cntMap=new TObjectFloatHashMap<String>());
				for (int i=2; i<tokens.length; ++i) {
					int splitter = tokens[2].indexOf(':');
					float cnt = Float.parseFloat(tokens[i].substring(splitter+1));
					cntMap.adjustOrPutValue(tokens[i].substring(0,splitter), cnt, cnt);
				}	
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return db;
	}
	
}
