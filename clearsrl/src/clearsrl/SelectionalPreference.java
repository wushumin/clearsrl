package clearsrl;

import java.util.Map;

import clearcommon.treebank.TBNode;
import clearcommon.util.LanguageUtil;
import gnu.trove.map.TObjectDoubleMap;

public abstract class SelectionalPreference {
	
	Map<String, Map<String, TObjectDoubleMap<String>>> predSPDB;
	Map<String, TObjectDoubleMap<String>> argSPDB;

	TObjectDoubleMap<String> getSP(Map<String, TObjectDoubleMap<String>> argSPDB, TBNode node,  LanguageUtil langUtil) {
		if (argSPDB!=null) {
			String headword = getArgHeadword(node, langUtil);
			return headword==null?null:argSPDB.get(headword);
		}
		return null;
	}
	
	public TObjectDoubleMap<String> getSP(TBNode node, LanguageUtil langUtil) {
		return getSP(argSPDB, node, langUtil);
	}
	
	public TObjectDoubleMap<String>[] getSP(TBNode predNode, String roleset, TBNode[] nodes, LanguageUtil langUtil) {
		Map<String, TObjectDoubleMap<String>> argDB = predSPDB.get(getPredicateKey(predNode, roleset, langUtil));
		
		
		return null;
		
	}

	public String getPredicateKey(TBNode predNode, String roleset, LanguageUtil langUtil) {
		if (roleset!=null)
			return roleset.substring(0, roleset.lastIndexOf('.'));
		return langUtil.findStems(predNode).get(0);
	}

	TBNode getHeadNode(TBNode node) {
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
	
	public String getArgHeadword(TBNode node, LanguageUtil langUtil) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return head.getWord().toLowerCase();
	}
	
}
