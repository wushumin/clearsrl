package clearcommon.util;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

import gnu.trove.map.TObjectIntMap;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class LanguageUtil {
    
    public enum POS {
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB
    }
    
    public abstract boolean init(Properties props);
    
    public List<String> findStems(String word, POS pos) {
        return Arrays.asList(word);
    }

    public List<String> findStems(TBNode node) {
        return Arrays.asList(node.getWord());
    }
    
    public List<String> getPredicateAlternatives(String predicate, TObjectIntMap<String> predicateSet) {
    	return Arrays.asList(predicate);
    }
    
    public String resolveAbbreviation(String word, String POS) {
        return word;
    }
    
    public abstract int getPassive(TBNode predicateNode);

    public abstract TBHeadRules getHeadRules();

	public boolean isNoun(String POS) {
		return POS.charAt(0)=='N';
	}

	public boolean isVerb(String POS) {
		return POS.charAt(0)=='V';
	}

	public PBFrame getFrame(String key) {
		return null;
	}
	
	public PBFrame getFrame(TBNode node) {
		return getFrame(PBFrame.makeKey(node, this));
	}

	public POS getPOS(String pos) {
		if (isAdjective(pos)) return POS.ADJECTIVE;
		if (isAdverb(pos)) return POS.ADVERB;
		if (isNoun(pos)) return POS.NOUN;
		if (isVerb(pos)) return POS.VERB;
		return null;
	}
	
	public abstract boolean isAdjective(String POS);
	
	public abstract boolean isAdverb(String POS);

	public abstract boolean isClause(String POS);

	public abstract boolean isRelativeClause(String POS);
	
	public abstract boolean isPredicateCandidate(String POS); 
	
}
