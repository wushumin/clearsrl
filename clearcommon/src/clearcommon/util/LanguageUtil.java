package clearcommon.util;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

import gnu.trove.TObjectIntHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class LanguageUtil {
    
    public enum POS
    {
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB
    }
    
    public abstract boolean init(Properties props);
    
    public List<String> findStems(String word, POS pos)
    {
        return Arrays.asList(word);
    }

    public List<String> getPredicateAlternatives(String predicate, TObjectIntHashMap<String> predicateSet)
    {
    	return Arrays.asList(predicate);
    }
    
    public String resolveAbbreviation(String word, String POS)
    {
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
	public abstract boolean isAdjective(String POS);
	
	public abstract boolean isAdverb(String POS);
}
