package clearcommon.util;

import gnu.trove.map.TObjectIntMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;

public class ChineseUtil extends LanguageUtil {

    public Map<String, PBFrame> frameMap;
	
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
        
        frameMap = new HashMap<String, PBFrame>();
        String frameDir = props.getProperty("frame_dir");
        if (frameDir != null)
            readFrameFiles(new File(frameDir));
        
        return true;
    }
    
    @Override
    public List<String> getPredicateAlternatives(String predicate, TObjectIntMap<String> predicateSet)
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
        /*
        for (TBNode node:predicateNode.getRoot().getTokenNodes())
            if (node.getPOS().matches("(SB|LB).*")) {
                System.err.println(predicateNode);
                System.err.println(predicateNode.getRoot().toParse());
                System.err.println(predicateNode.getRoot().toDependence(true));
                break;
            }*/
        if (predicateNode.getHeadOfHead()!=null && predicateNode.getHeadOfHead().getPOS().equals("LB"))
            return 2;
        for (TBNode dep:predicateNode.getDependentNodes())
            if (dep.getPOS().equals("SB"))
                return 1;

        /*
        
        for (TBNode node:predicateNode.getRoot().getTokenNodes()) {
            if (node.getPOS().matches("(SB|LB).*")) {
                TBNode beiParent = node.getParent();
                if (beiParent==null || !beiParent.getPOS().matches("VP.*")|| predicateNode.getTokenIndex()<node.getTokenIndex())
                    continue;
                if (predicateNode.isDecendentOf(beiParent)) {
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
        }*/
        
        return 0;
    }
    
    class FrameParseHandler extends DefaultHandler {
        PBFrame frame;
        PBFrame.Roleset roleset;
        
        boolean predicate;
        
        public FrameParseHandler(PBFrame frame) {
            this.frame = frame; 
            predicate = false;
        }
        
        @Override
        public void startElement(String uri,  String localName, String qName, Attributes atts) throws SAXException {
        	if (localName.equals("id")) {
        		predicate = true;
        	} else if (localName.equals("frameset")) {
        		String rolesetId = atts.getValue("id");
        		rolesetId = frame.predicate+'.'+(rolesetId.length()==2?"0":"")+rolesetId.substring(1);
                roleset = frame.new Roleset(rolesetId);
                frame.rolesets.put(roleset.getId(), roleset);
            } else if (localName.equals("role")) {
            	roleset.addRole("arg"+atts.getValue("argnum").toLowerCase(), null);
            }
        }
        
        @Override
        public void characters(char ch[], int start, int length) throws SAXException {
            if (predicate) {
                frame.predicate=new String(ch, start, length).replaceAll("[\\s「」]", "");
                predicate = false;
            }
        }
    }
    
    void readFrameFiles(final File dir) {
    	logger.info("Reading frame files from "+dir.getPath());
        XMLReader parser=null;
        try {
            parser = XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser");
            parser.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId)
                        throws SAXException, IOException {
                    if (systemId.contains("verb.dtd")) {
                        return new InputSource(new FileReader(new File(dir, "verb.dtd")));
                    } else {
                        return null;
                    }
                }
            });
        } catch (SAXException e) {
            e.printStackTrace();
            return;
        }
        
        File zippedFrames = new File(dir, "allframes.zip");
        if (zippedFrames.exists()) {
        	try (ZipFile zipIn = new ZipFile(zippedFrames)) {
        		logger.info(""+zipIn.size()+" frame files found");
        		for (Enumeration<? extends ZipEntry> e = zipIn.entries(); e.hasMoreElements();) {
        			ZipEntry entry = e.nextElement();
        			readFrameFile(parser, entry.getName(), new InputSource(zipIn.getInputStream(entry)));
        		}
            } catch (IOException e) {
	            e.printStackTrace();
            }
        } else {
        	List<String> fileNames = FileUtil.getFiles(dir, ".+\\.xml");
            logger.info(""+fileNames.size()+" frame files found");
            for (String fileName:fileNames)
	            try {
	                readFrameFile(parser, fileName, new InputSource(new InputStreamReader(new FileInputStream(new File(dir, fileName)), "UTF8")));
                } catch (IOException e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                }
        }
        logger.info(""+frameMap.size()+" frames read");
    }
    
    void readFrameFile(XMLReader parser, String fName, InputSource source) {
        String key = fName;
        key = key.substring(0, key.length()-4);
        String predicate = key.substring(0, key.length()-2);
        //System.out.println(file.getName());
        PBFrame frame = new PBFrame(predicate, LanguageUtil.POS.VERB);
        try {
            parser.setContentHandler(new FrameParseHandler(frame));
            parser.parse(source);
            frameMap.put(frame.getPredicate(), frame);
            //logger.info("Added "+frame.getPredicate()+key.substring(key.length()-2)+ " "+frameMap.size());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    @Override
    public PBFrame getFrame(String key) {
        return frameMap==null?null:frameMap.get(key);
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
    
    /*
    @Override
    public String convertPBLabelTrain(String label) {
    	if (label.endsWith("-PSR"))
    		return label;
        return super.convertPBLabelPredict(label);
   }
    */
    
    @Override
    public boolean isExplicitSupport(String label) {
    	return "Sup".equals(label);
    }
    
    @Override
    public boolean isAdjective(String POS) {
        return POS.equals("JJ");
    }

    @Override
    public boolean isAdverb(String POS) {
        return POS.equals("AD");
    }

    @Override
    public boolean isClause(String POS) {
        return POS.matches("IP|CP");
    }

    @Override
    public boolean isPredicateCandidate(String POS) {
        return isVerb(POS) || POS.equals("NN");
    }

    @Override
    public boolean isRelativeClause(String POS) {
        // TODO: this is very termporary 
        return POS.equals("CP");
    }

	@Override
    public List<String> getConstructionTypes(TBNode predicateNode) {
		List<String> constructions = new ArrayList<String>();
		
	    int passive = getPassive(predicateNode);
	    if (passive>0) {
	    	constructions.add("passive");
	    	constructions.add(passive==1?"SB":"LB");
	    } else if (passive==0 && (predicateNode.getHeadOfHead()!=null && predicateNode.getHeadOfHead().getPOS().equals("BA")))
	    	constructions.add("BA");

	    // identifies predicate in a complementizer
	    TBNode modifiedHead = constructions.contains("LB")||constructions.contains("BA")?predicateNode.getHeadOfHead():predicateNode;
	    if (modifiedHead.getConstituentByHead().getPOS().equals("CP") 
	    		|| modifiedHead.getHeadOfHead()!=null 
	    		&& modifiedHead.getHeadOfHead().getConstituentByHead().getPOS().equals("CP")) {
	    	if (constructions.isEmpty())
		    	constructions.add("active");
	    	int count = constructions.size();
	    	for (int i=0; i<count; ++i)
	    		constructions.add(constructions.get(i)+"-CP");
	    	constructions.add("CP");
	    } else if (constructions.isEmpty())
	    	constructions.add("active");
	    
	    return constructions;
    }
	
	public static void main(String[] args) throws IOException {
		Properties props = new Properties();
        FileInputStream in = new FileInputStream(args[0]);
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        Properties langProps = PropertyUtil.filterProperties(props, "chinese.");
        ChineseUtil langUtil = new ChineseUtil();
        langUtil.init(langProps);
        System.out.printf("%d frames read\n", langUtil.frameMap.size());
	}

}
