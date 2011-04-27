package clearsrl;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

import gnu.trove.TObjectIntHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class LanguageUtil {
    
    enum POS
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
    
    public abstract int getPassive(TBNode predicateNode);
    
    
    public abstract TBHeadRules getHeadRules();
}
