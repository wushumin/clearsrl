package clearcommon.util;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.morph.WordnetStemmer;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

public class EnglishUtil extends LanguageUtil {

    public Dictionary dict;
    public WordnetStemmer stemmer;
    TBHeadRules headRules;
    
    static Map<String, String> abbreviations = new HashMap<String, String>();
    static {
        abbreviations.put("& CC", "and");
        abbreviations.put("'n CC", "and");
        abbreviations.put("'n' CC", "and");
        abbreviations.put("'d MD", "would");
        abbreviations.put("'ll MD", "will");
        abbreviations.put("'m VBP", "am");
        abbreviations.put("'re VBP", "are");
        abbreviations.put("'ve VB", "have");
        abbreviations.put("'ve VBP", "have");
        abbreviations.put("'d VBD", "had");
        abbreviations.put("em PRP", "them");
        abbreviations.put("n't", "not");
        abbreviations.put("'til IN", "until");
        abbreviations.put("'til RB", "until");
    }
    
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
            e.printStackTrace();
            return false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    @Override
    protected void finalize() throws Throwable {
        dict.close();
    }

    @Override
    public List<String> findStems(String word, POS pos)
    {
        List<String> stems = stemmer.findStems(word, edu.mit.jwi.item.POS.valueOf(pos.toString()));
        return (stems.isEmpty()||stems.get(0).isEmpty())?Arrays.asList(word):stems;
    }
    
    @Override
    public List<String> findStems(TBNode node)
    {
        String pos = node.getPOS();
        List<String> stems = null;
        if (isNoun(pos))
            stems = stemmer.findStems(node.getWord(), edu.mit.jwi.item.POS.NOUN);
        else if (isVerb(pos))
            stems = stemmer.findStems(node.getWord(), edu.mit.jwi.item.POS.VERB);
        else if (isAdjective(pos))
            stems = stemmer.findStems(node.getWord(), edu.mit.jwi.item.POS.ADJECTIVE);
        else if (isAdverb(pos))
            stems = stemmer.findStems(node.getWord(), edu.mit.jwi.item.POS.ADVERB);
        
        return (stems==null||stems.isEmpty()||stems.get(0).isEmpty())?Arrays.asList(node.getWord()):stems;
    }
    
    @Override
    public String resolveAbbreviation(String word, String POS)
    {
        String full = abbreviations.get(word+" "+POS);
        return full==null?word:full;
    }
    
    @Override
    public int getPassive(TBNode predicate) {
        if (!predicate.getPOS().matches("VBN.*"))
            return 0;
        
        // Ordinary passive:
        // 1. Parent is VP, closest verb sibling of any VP ancestor is passive auxiliary (be verb)
        {
            TBNode currNode = predicate;
            while (currNode.getParent()!=null && currNode.getParent().getPOS().matches("VP.*"))
            {
                TBNode[] children = currNode.getParent().getChildren();
                
                for (int i=currNode.getChildIndex()-1; i>=0; --i)
                {
                    if (!children[i].isToken()) continue;
                    
                    // find auxiliary verb if verb, if not, stop
                    if (children[i].getPOS().matches("V.*|AUX.*"))
                    {
                        List<String> stems = findStems(children[i].getWord(), POS.VERB);
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
            while (currNode.getParent()!=null && currNode.getParent().getPOS().matches("ADJP.*"))
                currNode = currNode.getParent();
            
            if (currNode!=predicate && currNode.getPOS().matches("VP.*"))
            {
                TBNode[] children = currNode.getParent().getChildren();
                    
                for (int i=currNode.getChildIndex()-1; i>=0; --i)
                {
                    if (!children[i].isToken()) continue;
                    
                    // find auxiliary verb if verb, if not, stop
                    if (children[i].getPOS().matches("V.*|AUX.*"))
                    {
                        List<String> stems = findStems(children[i].getWord(), POS.VERB);
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
            while (currNode.getParent()!=null && currNode.getParent().getPOS().matches("VP.*"))
            {
                TBNode[] children = currNode.getParent().getChildren();

                for (int i=currNode.getChildIndex()-1; i>=0; --i)
                {
                    if (!children[i].isToken()) continue;
                    if (children[i].getPOS().matches("V.*|AUX.*"))
                    {
                        found = false;
                        break;
                    }
                }
                if (!found) break;
                currNode = currNode.getParent();
            }
                
            if (found && currNode!=predicate && currNode.getParent()!=null && currNode.getParent().getPOS().matches("NP.*"))
                return 3;
        }
        
        //2. Parent is PP
        {
            if (predicate.getParent()!=null && predicate.getParent().getPOS().matches("PP.*"))
                return 4;
        }
        
        //3. Parent is VP, grandparent is clause, and great grandparent is clause, NP, VP or PP
        {
            if (predicate.getParent()!=null && predicate.getParent().getPOS().matches("VP.*") &&
                predicate.getParent().getParent()!=null && predicate.getParent().getParent().getPOS().matches("S.*") &&
                predicate.getParent().getParent().getParent()!=null && 
                predicate.getParent().getParent().getParent().getPOS().matches("(S|NP|VP|PP).*"))
                return 5;
        }
        
        //4. ancestors are ADVP, no preceding siblings of oldest ancestor is DET,
        //   no following siblings is a noun or NP
        {
            TBNode currNode = predicate;
            while (currNode.getParent()!=null && currNode.getParent().getPOS().matches("ADJP.*"))
                currNode = currNode.getParent();
            if (currNode != predicate && currNode.getParent()!=null)
            {
                boolean found = true;
                TBNode[] children = currNode.getParent().getChildren();
                
                for (int i=currNode.getChildIndex()-1; i>=0; --i)
                {
                    if (children[i].getPOS().matches("DT.*"))
                    {
                        found = false;
                        break;
                    }
                }
                for (int i=currNode.getChildIndex()+1; i<children.length; ++i)
                {
                    if (children[i].getPOS().matches("N.*"))
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

    public TBNode getPPHead(TBNode ppNode)
    {
        TBNode head = null;
        int i = ppNode.getChildren().length-1;
        for (; i>=0; --i)
        {
            if (ppNode.getChildren()[i].getPOS().matches("NP.*"))
            {
                if (ppNode.getChildren()[i].getHead()!=null && ppNode.getChildren()[i].getHeadword()!=null)
                    head = ppNode.getChildren()[i].getHead();
                break;
            }
        }
        if (i<0 && ppNode.getChildren()[ppNode.getChildren().length-1].getHead()!=null && 
                ppNode.getChildren()[ppNode.getChildren().length-1].getHeadword()!=null)
            head = ppNode.getChildren()[ppNode.getChildren().length-1].getHead();
        return head;
    }
    
    @Override
    public TBHeadRules getHeadRules() {
        return headRules;
    }

	@Override
	public boolean isAdjective(String POS) {
		return POS.charAt(0)=='J';
	}

	@Override
	public boolean isAdverb(String POS) {
		return POS.startsWith("RB");
	}


}