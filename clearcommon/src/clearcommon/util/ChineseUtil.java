package clearcommon.util;

import gnu.trove.TObjectIntHashMap;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

public class ChineseUtil extends LanguageUtil {

	static final Set<String> NOUN_POS = new HashSet<String>();
	static {
		NOUN_POS.add("NR");
		NOUN_POS.add("NT");
		NOUN_POS.add("NN");
	};
	
	static final Set<String> VERB_POS = new HashSet<String>();
	static {
		VERB_POS.add("VA");
		VERB_POS.add("VC");
		VERB_POS.add("VE");
		VERB_POS.add("VV");
	};
	
    TBHeadRules headRules;
    
    @Override
    public boolean init(Properties props) {
        try {
            headRules = new TBHeadRules(props.getProperty("headrules"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    @Override
    public List<String> getPredicateAlternatives(String predicate, TObjectIntHashMap<String> predicateSet)
    {
    	if (predicateSet==null)
    		return super.getPredicateAlternatives(predicate, predicateSet);
    	ArrayList<String> alternatives = new ArrayList<String>();
    	alternatives.add(predicate);
    	if (predicate.length()>1)
    	{
    		if (predicateSet.containsKey(predicate.substring(0,1)))
    			alternatives.add(predicate.substring(0,1));
    		if (predicateSet.containsKey(predicate.substring(predicate.length()-1)))
    			alternatives.add(predicate.substring(predicate.length()-1));
    	}
    	return alternatives;
    }
    
    @Override
    public int getPassive(TBNode predicateNode) {
        
        int retCode = 0;
        
        TBNode root = predicateNode.getRoot();
        List<TBNode> nodes = root.getTokenNodes();

        for (TBNode node:nodes)
        {
            if (node.getPOS().matches("(SB|LB).*"))
            {
                TBNode beiParent = node.getParent();
                if (beiParent==null || !beiParent.getPOS().matches("VP.*")|| predicateNode.getTokenIndex()<node.getTokenIndex())
                    continue;
                if (predicateNode.isDecendentOf(beiParent))
                {
                    TBNode predicateParent = predicateNode.getParent();
                    //System.out.println(predicateParent.getPathToAncestor(beiParent));
                    if (predicateParent==beiParent) // short bei?
                        return 1;
                    int count = countConstituents("VP", new LinkedList<TBNode>(predicateParent.getPathToAncestor(beiParent)), false, 0);
                    if (count <= 2)
                        return 2;
                    else
                        retCode = 1-count;
                }
            }
        }
        
        return retCode;
    }
    
    int countConstituents(String pos, Deque<TBNode> nodes, boolean left, int depth)
    {   
        if (nodes.isEmpty())
            return 0;
        
        TBNode node = nodes.pop();
        int count = node.getPOS().startsWith(pos)?1:0;
        
        ++depth;
        
        if (left)
            for (int i=node.getChildIndex()+1; i<node.getParent().getChildren().length;++i)
                count += countConstituents(pos, node.getParent().getChildren()[i], depth);
        else
            for (int i=0; i<node.getChildIndex()-1;++i)
                count += countConstituents(pos, node.getParent().getChildren()[i], depth);
        
        return count + countConstituents(pos, nodes, left, depth);
    }
     
    int countConstituents(String pos, TBNode node, int depth)
    {   
        int count = node.getPOS().startsWith(pos)?1:0;
        
        if (node.isTerminal() || depth == 0)
            return count;
        
        for (TBNode cNode:node.getChildren())
            count += countConstituents(pos, cNode, depth-1);
        
        return count;
    }
    
    @Override
    public TBHeadRules getHeadRules() {
        return headRules;
    }

	@Override
	public boolean isAdjective(String POS) {
		return POS.equals("JJ");
	}

	@Override
	public boolean isAdverb(String POS) {
		return POS.equals("AD");
	}

}
