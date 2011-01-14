package clearsrl;

import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;

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
    
    public abstract int getPassive(TBNode predicateNode);
    
    public abstract TBHeadRules getHeadRules();
}
