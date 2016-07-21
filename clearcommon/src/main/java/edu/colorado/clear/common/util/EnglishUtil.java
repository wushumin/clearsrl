package edu.colorado.clear.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import edu.colorado.clear.common.treebank.TBHeadRules;
import edu.colorado.clear.common.treebank.TBNode;
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.Pointer;
import edu.mit.jwi.morph.WordnetStemmer;

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
        abbreviations.put("n't RB", "not");
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        if (frameMap==null) {
	        frameMap = new HashMap<String, PBFrame>();
	        String frameDir = props.getProperty("frame_dir");
	        if (frameDir != null)
	            readFrameFiles(new File(frameDir));
        }
        
        return true;
    }
    
    @Override
    protected void finalize() throws Throwable {
        dict.close();
    }
    
    @Override
    public List<String> findStems(String word, POS pos) {
        return findStems(word, edu.mit.jwi.item.POS.valueOf(pos.toString()));
    }
    
    @Override
    public List<String> findStems(TBNode node) {
    	String word = abbreviations.get(node.getWord()+' '+node.getPOS());
    	word = word==null?node.getWord():word;
        
        return findStems(word, convertPOS(node.getPOS()));
    }
    
    edu.mit.jwi.item.POS convertPOS(String input) {
        if (isNoun(input)) return edu.mit.jwi.item.POS.NOUN;
        if (isVerb(input)) return edu.mit.jwi.item.POS.VERB;
        if (isAdjective(input)) return edu.mit.jwi.item.POS.ADJECTIVE;
        if (isAdverb(input)) return edu.mit.jwi.item.POS.ADVERB;
        return null;
    }

    List<String> findStems(String word, edu.mit.jwi.item.POS pos) {    	
        List<String> stems = null;
        try {
        	synchronized (stemmer) {
        		stems = stemmer.findStems(word, pos);
        	}
    	} catch (Exception e) {
    	    System.err.println("Exception caught trying to find stem of "+word+"/"+pos);
    	    e.printStackTrace();
    	}
        return (stems==null||stems.isEmpty()||stems.get(0).isEmpty())?Arrays.asList(word):stems;
    }
    
    class FrameParseHandler extends DefaultHandler {
        PBFrame frame;
        PBFrame.Roleset roleset;
        public FrameParseHandler(PBFrame frame) {
            this.frame = frame; 
        }
        
        String aliasPOS=null;
        StringBuilder aliasBuilder=null; 
        
        @Override
        public void startElement(String uri,  String localName, String qName, Attributes atts) throws SAXException {
            if (localName.equals("roleset")) {
            	roleset = frame.new Roleset(atts.getValue("id"));
            	if (atts.getValue("vncls") != null) 
            		for (String clsId:atts.getValue("vncls").trim().split("\\s+"))
                		if (!clsId.isEmpty() && !clsId.equals("-"))
                			roleset.addClass("vncls-"+clsId);
                frame.rolesets.put(roleset.getId(), roleset);
            } else if (localName.equals("role")) {
            	roleset.addRole("arg"+atts.getValue("n").toLowerCase(), atts.getValue("f")==null?null:atts.getValue("f").toLowerCase());
            } else if (localName.equals("vnrole")) {
            	if (atts.getValue("vncls") != null) 
            		for (String clsId:atts.getValue("vncls").trim().split("\\s+"))
                		if (!clsId.isEmpty() && !clsId.equals("-"))
                			roleset.addClass("vncls-"+clsId);
            } else if (localName.equals("alias")) {
            	aliasPOS = atts.getValue("pos");
            	if (aliasBuilder==null)
            		aliasBuilder = new StringBuilder();
            	else
            		aliasBuilder.setLength(0);
            	if (atts.getValue("verbnet") != null)
	            	for (String clsId:atts.getValue("verbnet").trim().split("\\s+"))
	            		if (!clsId.isEmpty() && !clsId.equals("-"))
	            			roleset.addClass("vncls-"+clsId);
            }
        }
        
        @Override
        public void characters(char[] ch, int start, int length) {
            if (aliasPOS!=null) {
            	aliasBuilder.append(ch, start, length);
            }
        }
        
        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("alias")) {
            	roleset.addAlias(aliasBuilder.toString().trim()+'-'+aliasPOS);
                aliasPOS = null;
            }
        }
        
    }
    
    protected void readFrameFile(XMLReader parser, String fName, InputSource source) {
        String key = fName.substring(0, fName.lastIndexOf('.'));
        
        /*
        key = key.substring(0, key.length()-4);
       
        String predicate = key;
        char type = 'v';
        if (key.matches(".+-[jnv]")) {
        	predicate = key.substring(0, key.length()-2);
        	type = key.charAt(key.length()-1);
        }
        key = predicate+'-'+type;*/
        //PBFrame frame = new PBFrame(predicate, type=='n'?LanguageUtil.POS.NOUN:(type=='v'?LanguageUtil.POS.VERB:LanguageUtil.POS.ADJECTIVE));
        PBFrame frame = new PBFrame(key);
        try {
            parser.setContentHandler(new FrameParseHandler(frame));
            parser.parse(source);
            
            Set<String> aliases = new HashSet<String>();
            if (frame.rolesets!=null)
            	for (PBFrame.Roleset role:frame.rolesets.values())
            		if (role.getAliases()!=null)
            			aliases.addAll(role.getAliases());
            if (aliases.isEmpty())
            	frameMap.put(key, frame);
            else
            	for (String alias:aliases)
            		frameMap.put(alias, frame);
        } catch (IOException e) {
            // TODO Auto-generated catch block
        	System.err.println("Exception reading: "+fName);
            e.printStackTrace();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
        	System.err.println("Exception reading: "+fName);
            e.printStackTrace();
        }
    }
    
    @Override
    public PBFrame getFrame(String key) {
        return frameMap==null?null:frameMap.get(key);
    }
    
    public String findDerivedVerb(TBNode node) {
        
        edu.mit.jwi.item.POS pos = convertPOS(node.getPOS());
        if (pos==null) return null;
        
        String stem = findStems(node).get(0);
        
        IIndexWord idxWord = dict.getIndexWord(stem, pos);
        if (idxWord==null) return null;
        IWordID wordID = idxWord.getWordIDs().get(0);
        IWord word = dict.getWord(wordID);
        
        List<IWordID> wordIDs = word.getRelatedWords(Pointer.DERIVATIONALLY_RELATED);
        List<String> candidates = new ArrayList<String>();
        for (IWordID wID:wordIDs)
            if (wID.getPOS() == edu.mit.jwi.item.POS.VERB) candidates.add(dict.getWord(wID).getLemma());
        return candidates.isEmpty()?null:candidates.get(0);
    }
    
    
    @Override
    public String resolveAbbreviation(String word, String POS) {
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
                        List<String> stems = findStems(children[i]);
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
                        List<String> stems = findStems(children[i]);
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

    public TBNode getPPHead(TBNode ppNode) {
        TBNode head = null;
        int i = ppNode.getChildren().length-1;
        for (; i>=0; --i) {
            if (ppNode.getChildren()[i].getPOS().matches("NP.*")) {
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
    
    //@Override
    //public String makePBFrameKey(TBNode node) {
    //	String lemma = findStems(node).get(0);
    //    String pos = isVerb(node.getPOS())?"-v":(isNoun(node.getPOS())?"-n":(isAdjective(node.getPOS())?"-j":""));
    //    return lemma+pos;
    //}
    
    @Override
    public String makePBFrameKey(String lemma, POS pos) {
    	String suffix = pos==null?"":(pos.equals(POS.VERB)?"-v":(pos.equals(POS.NOUN)?"-n":(pos.equals(POS.ADJECTIVE)?"-j":"")));
    	return lemma+suffix;
    }
    
    @Override
    public boolean isExplicitSupport(String label) {
    	return "ARGM-LVB".equals(label);
    }
    
    @Override
    public boolean isAdjective(String POS) {
        return POS.charAt(0)=='J';
    }

    @Override
    public boolean isAdverb(String POS) {
        return POS.startsWith("RB");
    }

    @Override
    public boolean isClause(String POS) {
        return POS.matches("S|SBARQ|SINV|SQ");
    }

    @Override
    public boolean isPredicateCandidate(String POS) {
        return isVerb(POS) || isAdjective(POS) || POS.matches("NN|NNS");
    }

    @Override
    public boolean isRelativeClause(String POS) {
        return POS.equals("SBAR");
    }

	@Override
    public List<String> getConstructionTypes(TBNode predicateNode) {
	    int passive = getPassive(predicateNode);
	    return passive==0?Arrays.asList("active"):(passive>3?Arrays.asList("reduced_passive", "passive"):Arrays.asList("passive"));
    }
}
