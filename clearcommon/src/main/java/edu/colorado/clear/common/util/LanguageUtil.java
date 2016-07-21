package edu.colorado.clear.common.util;

import edu.colorado.clear.common.propbank.PBFileReader;
import edu.colorado.clear.common.treebank.TBHeadRules;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.PBFrame.Roleset;
import gnu.trove.map.TObjectIntMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public abstract class LanguageUtil {
	
	protected static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    Map<String, PBFrame> frameMap;
	
    public enum POS {
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB
    }
    
    public abstract boolean init(Properties props);
    
    public boolean init(Properties props, Map<String, PBFrame> frameMap) {
    	this.frameMap = frameMap;
    	return init(props);
    }
    
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
    
    /**
     * Language specific constructions based on the predicate. These can 
     * include active/passive voice in English, BA/BEI constructions in 
     * Chinese. 
     * @param predicateNode
     * @return a list of all found predicate constructions
     */
    public abstract List<String> getConstructionTypes(TBNode predicateNode);

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
        return getFrame(makePBFrameKey(node));
    }
    
    public Map<String, PBFrame> getFrameMap() {
    	return Collections.unmodifiableMap(frameMap);
    }
    
    public Roleset getRoleSet(TBNode node, String roleSetId) {
    	PBFrame frame = getFrame(node);
    	if (frame==null)
    		return null;
    	return frame.getRolesets().get(roleSetId);
    }
    
    public Roleset getRoleSet(String roleSetId, POS pos) {
    	PBFrame frame = getFrame(makePBFrameKey(roleSetId.substring(0, roleSetId.lastIndexOf('.')), pos));
    	if (frame==null)
    		return null;
    	return frame.getRolesets().get(roleSetId);
    }

    public POS getPOS(String pos) {
        if (isAdjective(pos)) return POS.ADJECTIVE;
        if (isAdverb(pos)) return POS.ADVERB;
        if (isNoun(pos)) return POS.NOUN;
        if (isVerb(pos)) return POS.VERB;
        return null;
    }
    
    public String makePBFrameKey(TBNode node) {
    	return makePBFrameKey(findStems(node).get(0), getPOS(node.getPOS()));
    }
    
    public String makePBFrameKey(String lemma, POS pos) {
    	return lemma;
    }

    static final Pattern ARG_PATTERN = Pattern.compile("(([RC]-)?(A[A-Z]*\\d))(\\-[A-Za-z]+)?");
    public static String removePBLabelModifier(String label) {
        Matcher matcher = ARG_PATTERN.matcher(label);
        if (matcher.matches())
            return matcher.group(1);
        return label;
    }
    
    public String convertPBLabelTrain(String label) {
         return removePBLabelModifier(label);
    }
    
    public String convertPBLabelPredict(String label) {
    	return removePBLabelModifier(label);
    }
    
    public boolean isExplicitSupport(String label) {
    	return false;
    }
    
    protected abstract void readFrameFile(XMLReader parser, String fName, InputSource source);
    
    protected void readFrameFiles(final File dir) {
    	logger.info("Reading frame files from "+dir.getPath());
    	ZipFile tmpZipIn=null;
    	if (dir.isFile() && dir.getName().endsWith(".zip"))
    		try {
    			tmpZipIn = new ZipFile(dir);
    		} catch (IOException e) {
	            e.printStackTrace();
            }
    	
    	final ZipFile zipIn = tmpZipIn;

        XMLReader parser=null;
        try {
            parser = XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser");
            parser.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId)
                        throws SAXException, IOException {
                	
                	if (zipIn!=null) {
                		File file = new File(systemId);
                		
                		ZipEntry entry = zipIn.getEntry(file.getName());
                		if (entry!=null)
                			return new InputSource(new InputStreamReader(zipIn.getInputStream(entry), StandardCharsets.UTF_8));
                	} else {
	                	File dtdFile = new File(systemId);
	                    if (dtdFile.isFile())
	                        return new InputSource(new InputStreamReader(new FileInputStream(dtdFile), StandardCharsets.UTF_8));
                	}

                    return null;

                }
            });
        } catch (SAXException e) {
            e.printStackTrace();
            return;
        }

        if (zipIn!=null) {
        	for (Enumeration<? extends ZipEntry> e = zipIn.entries(); e.hasMoreElements();) {
    			ZipEntry entry = e.nextElement();
    			if (entry.getName().endsWith(".xml"))
					try {
						readFrameFile(parser, entry.getName(), new InputSource(new InputStreamReader(zipIn.getInputStream(entry), StandardCharsets.UTF_8)));
					} catch (IOException ex) {
						logger.log(Level.WARNING, "Error reading frame file "+entry.getName(), ex);
					}
    		}
        } else {
        	List<String> fileNames = FileUtil.getFiles(dir, ".+\\.xml");
            logger.info(""+fileNames.size()+" frame files found");
            for (String fileName:fileNames)
				try {
					readFrameFile(parser, fileName, new InputSource(new InputStreamReader(new FileInputStream(new File(dir, fileName)), StandardCharsets.UTF_8)));
				} catch (IOException e) {
					logger.log(Level.WARNING, "Error reading frame file "+fileName, e);
				}
        }
        
        logger.info(Integer.toString(frameMap.size())+" frames read");
        
    }
    
    
    public abstract boolean isAdjective(String POS);
    
    public abstract boolean isAdverb(String POS);

    public abstract boolean isClause(String POS);

    public abstract boolean isRelativeClause(String POS);
    
    public abstract boolean isPredicateCandidate(String POS); 
    
}
