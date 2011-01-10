package clearsrl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


import edu.mit.jwi.Dictionary;
import edu.mit.jwi.morph.WordnetStemmer;

import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;

public class EnglishUtil extends LanguageUtil {

    Dictionary dict;
    WordnetStemmer stemmer;
    TBHeadRules headRules;
    
    @Override
    public boolean init(Properties props) {
        try {
            URL url = new URL("file", null, props.getProperty("wordnet_dic"));
            // construct the dictionary object and open it
            dict = new Dictionary(url);
            dict.getCache().setMaximumCapacity(5000);
            dict.open();
            stemmer = new WordnetStemmer(dict);
            headRules = new TBHeadRules(props.getProperty("headrules"));
        } catch (MalformedURLException e)
        {
            return false;
        }
        return true;
    }
    
    protected void finalize() throws Throwable {
        dict.close();
    }

    public List<String> findStems(String word, POS pos)
    {
        return stemmer.findStems(word, edu.mit.jwi.item.POS.valueOf(pos.toString()));
    }
    
    @Override
    public int getPassive(TBNode predicate) {
        if (!predicate.pos.matches("VBN.*"))
            return 0;
        
        // Ordinary passive:
        // 1. Parent is VP, closest verb sibling of any VP ancestor is passive auxiliary (be verb)
        {
            TBNode currNode = predicate;
            while (currNode.getParent()!=null && currNode.getParent().pos.matches("VP.*"))
            {
                ArrayList<TBNode> children = currNode.getParent().getChildren();
                
                for (int i=currNode.childIndex-1; i>=0; --i)
                {
                    if (!children.get(i).isToken()) continue;
                    
                    // find auxiliary verb if verb, if not, stop
                    if (children.get(i).pos.matches("V.*|AUX.*"))
                    {
                        List<String> stems = findStems(children.get(i).word, POS.VERB);
                        if (!stems.isEmpty() && (stems.get(0).matches("be|get")))
                            return 1;
                        else
                            break;
                    }
                }
                currNode = currNode.getParent();
            }
        }
        
        // 2. ancestor path is (ADVP->)*VP, closest verb sibling of the VP is passive auxiliary (be verb)
        {
            TBNode currNode = predicate;
            while (currNode.getParent()!=null && currNode.getParent().pos.matches("ADJP.*"))
                currNode = currNode.getParent();
            
            if (currNode!=predicate && currNode.pos.matches("VP.*"))
            {
                ArrayList<TBNode> children = currNode.getParent().getChildren();
                    
                for (int i=currNode.childIndex-1; i>=0; --i)
                {
                    if (!children.get(i).isToken()) continue;
                    
                    // find auxiliary verb if verb, if not, stop
                    if (children.get(i).pos.matches("V.*|AUX.*"))
                    {
                        List<String> stems = findStems(children.get(i).word, POS.VERB);
                        if (!stems.isEmpty() && (stems.get(0).matches("be|get")))
                            return 2;
                        else
                            break;
                    }
                }
            }
        }
        
        //Reduced Passive:
        //1. Parent and nested ancestors are VP, 
        //   none of VP ancestor's preceding siblings is verb
        //   parent of oldest VP ancestor is NP
        {
            TBNode currNode = predicate;
            boolean found = true;
            while (currNode.getParent()!=null && currNode.getParent().pos.matches("VP.*"))
            {
                ArrayList<TBNode> children = currNode.getParent().getChildren();

                for (int i=currNode.childIndex-1; i>=0; --i)
                {
                    if (!children.get(i).isToken()) continue;
                    if (children.get(i).pos.matches("V.*|AUX.*"))
                    {
                        found = false;
                        break;
                    }
                }
                if (!found) break;
                currNode = currNode.getParent();
            }
                
            if (found && currNode!=predicate && currNode.getParent()!=null && currNode.getParent().pos.matches("NP.*"))
                return 3;
        }
        
        //2. Parent is PP
        {
            if (predicate.getParent()!=null && predicate.getParent().pos.matches("PP.*"))
                return 4;
        }
        
        //3. Parent is VP, grandparent is clause, and great grandparent is clause, NP, VP or PP
        {
            if (predicate.getParent()!=null && predicate.getParent().pos.matches("VP.*") &&
                predicate.getParent().getParent()!=null && predicate.getParent().getParent().pos.matches("S.*") &&
                predicate.getParent().getParent().getParent()!=null && 
                predicate.getParent().getParent().getParent().pos.matches("(S|NP|VP|PP).*"))
                return 5;
        }
        
        //4. ancestors are ADVP, no preceding siblings of oldest ancestor is DET,
        //   no following siblings is a noun or NP
        {
            TBNode currNode = predicate;
            while (currNode.getParent()!=null && currNode.getParent().pos.matches("ADJP.*"))
                currNode = currNode.getParent();
            if (currNode != predicate)
            {
                boolean found = true;
                ArrayList<TBNode> children = currNode.getParent().getChildren();
                
                for (int i=currNode.childIndex-1; i>=0; --i)
                {
                    if (children.get(i).pos.matches("DT.*"))
                    {
                        found = false;
                        break;
                    }
                }
                for (int i=currNode.childIndex+1; i<children.size(); ++i)
                {
                    if (children.get(i).pos.matches("N.*"))
                    {
                        found = false;
                        break;
                    }
                }
                if (found) return 6;
            }
        }
        
        return 0;

    }

    @Override
    public TBHeadRules getHeadRules() {
        return headRules;
    }


}
