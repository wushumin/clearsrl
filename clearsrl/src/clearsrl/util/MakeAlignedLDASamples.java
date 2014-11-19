package clearsrl.util;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PBFrame;
import clearcommon.util.PBFrame.Roleset;
import clearcommon.util.PropertyUtil;
import clearsrl.align.Aligner;
import clearsrl.align.Alignment;
import clearsrl.align.ArgAlignmentPair;
import clearsrl.align.Sentence;
import clearsrl.align.SentencePair;
import clearsrl.align.SentencePair.BadInstanceException;

public class MakeAlignedLDASamples {
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-outDir",usage="output directory")
    private File outDir = null; 
    
    @Option(name="-wt",usage="threshold")
    private int wCntThreshold = 10;
    
    @Option(name="-dt",usage="threshold")
    private int docSizeThreshold = 25;
    
    @Option(name="-recall",usage="enable certain recall features")
    private double recallVal = 1.1;
    
    @Option(name="-precision",usage="enable certain precision features")
    private double precisionVal = 1;
    
    @Option(name="-fw",usage="full match weight")
    private int fmWeight = 10;
    
    @Option(name="-pw",usage="part match weight")
    private int pmWeight = 3;
    
    @Option(name="-prob",usage="argument probability")
	private double prob = -1; 
 
    @Option(name="-h",usage="help message")
    private boolean help = false;
    

    static final class ArgWeight {
    	public ArgWeight(String label, String head, int weight) {
    		this.label = label;
    		this.head = head;
    		this.weight = weight;
    	}
    	
    	public String toString() {
    		return label+' '+head+' '+weight;
    	}
    	
    	String label;
    	String head;
    	int weight;
    }
    
    public static String[] readAlignment(File file) {
    	List<String> lines = new ArrayList<String>();
    	try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
    		String line=null;
    		while ((line=reader.readLine())!=null)
    			lines.add(line.trim());
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	return lines.toArray(new String[lines.size()]);
    }
    
    public static SentencePair makeSentencePair(int id, TBTree srcTree, List<PBInstance> srcProp, TBTree dstTree, List<PBInstance> dstProp, String wa, boolean reverse) throws BadInstanceException {
    	SentencePair sp = new SentencePair(id);
    	sp.src = Sentence.parseSentence(srcTree, srcProp);
    	sp.dst = Sentence.parseSentence(dstTree, dstProp);
    	sp.parseAlign(wa, true, reverse);
    	return sp;
    }
    
    static void updateMap(String roleset, List<ArgWeight> args, Map<String, Map<String, TObjectDoubleMap<String>>> argMap) {
    	String predWord = roleset.substring(0,roleset.lastIndexOf('.'));
    	logger.fine(predWord+": "+args);
    	
    	Map<String, TObjectDoubleMap<String>> wordMap = argMap.get(predWord);
    	if (wordMap==null)
    		argMap.put(predWord, wordMap = new HashMap<String, TObjectDoubleMap<String>>());
    	
    	for (ArgWeight arg:args) {
    		if (arg.label.equals("rel"))
				continue;
			
			TObjectDoubleMap<String> innerMap = wordMap.get(arg.label);
			if (innerMap==null)
				wordMap.put(arg.label, innerMap=new TObjectDoubleHashMap<String>());
			innerMap.adjustOrPutValue(arg.head, arg.weight, arg.weight);
    	}
    }
    
    static void updateMap(PBInstance instance, Map<String, Map<String, TObjectDoubleMap<String>>> argMap, double prob) {
    	List<ArgWeight> args = new ArrayList<ArgWeight>();
    	for (PBArg arg:instance.getArgs()) {
			if (arg.getLabel().equals("rel"))
				continue;
			if (arg.getScore()<prob)
				continue;
			String head = Topics.getTopicHeadword(arg.getNode());
			if (head==null)
				continue;
			args.add(new ArgWeight(arg.getLabel(), head, 1));
		}
    	updateMap(instance.getRoleset(), args, argMap);
    }
    
    static boolean hasWA(SentencePair sp, TBNode srcNode, TBNode dstNode) {
    	if (srcNode==null || dstNode==null) return false;
    	
    	for (int idx: sp.dstAlignment.get(sp.dst.indices[dstNode.getTokenIndex()]))
    		if (idx==srcNode.getTokenIndex())
    			return true;
    	return false;
    }
    
    static int labelCompatible(String chLabel, String enLabel, Roleset chRoles, Roleset enRoles) {
    	if (chLabel.equals("ARGM-ADV") && enLabel.startsWith("ARGM") && !enLabel.matches("ARGM-(TMP|LOC)")) return 0;
    	if (enLabel.equals("ARGM-ADV") && chLabel.startsWith("ARGM") && !chLabel.matches("ARGM-(TMP|LOC)")) return 0;
    	if (chLabel.matches("ARG\\d") && enLabel.matches("ARG\\d")) {
    		if (chRoles!=null && chRoles.hasRole("ARG0") && 
    			enRoles!=null && !enRoles.hasRole("ARG0")) {
    			if (chLabel.charAt(3)+1==enLabel.charAt(3))
    	    		return 1;
    			return -1;
    		} 
    		if (chLabel.equals(enLabel)) 
    			return 1;
    		return -1;
    	}
    	if (chLabel.equals(enLabel)) return 1;
    	
    	return -1;
    }
    
    static String findAlignedHead(SentencePair sp, TBNode srcNode, LanguageUtil chUtil) {
    	int[] wa = sp.srcAlignment.get(sp.src.indices[srcNode.getTokenIndex()]);
    	
    	if (wa==null || wa.length==0)
    		return null;
    	if (wa.length==1) {
    		if (!chUtil.isNoun(sp.dst.tokens[wa[0]].getPOS()) && !chUtil.isVerb(sp.dst.tokens[wa[0]].getPOS())) {
    			if (sp.dst.tokens[wa[0]].getHeadOfHead()!=null && 
    					(chUtil.isNoun(sp.dst.tokens[wa[0]].getHeadOfHead().getPOS()) || chUtil.isVerb(sp.dst.tokens[wa[0]].getHeadOfHead().getPOS())))
    				return sp.dst.tokens[wa[0]].getHeadOfHead().getWord();
    		} else 
    			return sp.dst.tokens[wa[0]].getWord();
    		return null;
    	}
    	
    	for (int i:wa) {
    		BitSet bs = sp.dst.tokens[i].getConstituentByHead().getTokenSet();
    		boolean found = true;
    		for (int j:wa)
    			if (!bs.get(sp.dst.tokens[j].getTokenIndex())) {
    				found = false;
    				break;
    			}
    		if (found)
    			return sp.dst.tokens[i].getWord();
    	}
    	return null;
    }
    
    static void processAlignment(Alignment a, Map<String, Map<String, TObjectDoubleMap<String>>> argMap, LanguageUtil chUtil, LanguageUtil enUtil, MakeAlignedLDASamples opt) {
    	PBInstance chProp = a.getDstPBInstance();
    	PBArg[] chArgs = chProp.getArgs();
    	
    	PBInstance enProp = a.getSrcPBInstance();
    	PBArg[] enArgs = enProp.getArgs();
    	
    	boolean found = false;
    	for (ArgAlignmentPair ap: a.getArgAlignmentPairs())
    		if (enArgs[ap.srcArgIdx].getLabel().equals("rel") && chArgs[ap.dstArgIdx].getLabel().equals("rel")) {
    			found=true;
    			break;
    		}
    	if (!found) {
    		//if (chProp.getRoleset().endsWith(".XX"))
    		//	return;
    		updateMap(chProp, argMap, opt.prob);
    		return;
    	}
    	
    	PBFrame chFrame = chUtil.getFrame(chProp.getPredicate());
    	PBFrame enFrame = enUtil.getFrame(enProp.getPredicate());
    	
    	Roleset chRoles = chFrame==null?null:chFrame.getRolesets().get(chProp.getRoleset());
    	Roleset enRoles = enFrame==null?null:enFrame.getRolesets().get(enProp.getRoleset());
    	
    	List<ArgWeight> args = new ArrayList<ArgWeight>();
    	
    	int[] weights = new int[chArgs.length];
    	for (int i=0; i<chArgs.length; ++i)
    		if (chArgs[i].getScore()<opt.prob)
    			weights[i]=0;
    		else
    			weights[i]=1;

    	String[] labelMod = new String[chArgs.length];
    	
    	BitSet enArgBitSet = new BitSet(enArgs.length);
    	
    	for (ArgAlignmentPair ap: a.getArgAlignmentPairs()) {
    		enArgBitSet.set(ap.srcArgIdx);
    		if (enArgs[ap.srcArgIdx].getLabel().equals("rel") || chArgs[ap.dstArgIdx].getLabel().equals("rel")) 
    			continue;
    		//double cProb = 1-(1-enArgs[ap.srcArgIdx].getScore())*(1-chArgs[ap.dstArgIdx].getScore());
    		//if (cProb<opt.prob)
    		//	continue;
    		
    		if (hasWA(a.sentence, Topics.getTopicHeadNode(enArgs[ap.srcArgIdx].getNode()), Topics.getTopicHeadNode(chArgs[ap.dstArgIdx].getNode()))) {
    			int compatible = labelCompatible(chArgs[ap.dstArgIdx].getLabel(),  enArgs[ap.srcArgIdx].getLabel(), chRoles, enRoles);
    			if (compatible>=0) {
    				//weights[ap.dstArgIdx] = chArgs[ap.dstArgIdx].getScore()>=opt.prob && enArgs[ap.srcArgIdx].getScore()>=opt.eprob?opt.fmWeight:opt.pmWeight;
    				if (chArgs[ap.dstArgIdx].getScore()>=opt.prob && enArgs[ap.srcArgIdx].getScore()>=opt.prob)
    					weights[ap.dstArgIdx] = opt.fmWeight;
    				else if (enArgs[ap.srcArgIdx].getScore()>=opt.recallVal && compatible>0)
    					weights[ap.dstArgIdx] = opt.pmWeight;
    			} else if (enArgs[ap.srcArgIdx].getScore()-chArgs[ap.dstArgIdx].getScore()>=opt.precisionVal) {
    				if (enArgs[ap.srcArgIdx].getLabel().equals("ARGM-TMP")) {
	    				weights[ap.dstArgIdx] = 0;
	    			} else if (enArgs[ap.srcArgIdx].getLabel().equals("ARG0") && chArgs[ap.dstArgIdx].getLabel().startsWith("ARGM") && enArgs[ap.srcArgIdx].getScore()>=opt.prob) {
	    				boolean hasArg0 = false; 
	    				for (PBArg arg:chArgs)
	    					if (arg.getLabel().equals("ARG0")) {
	    						hasArg0 = true;
	    						break;
	    					}
	    				if (!hasArg0)
	    					weights[ap.dstArgIdx] = 0;
	    			}
    			}
    		}
    	}

    	if (chRoles!=null&&chRoles.hasRole("ARG0")) {
    		boolean foundArg=false;
    		for (PBArg arg:chArgs)
				if (arg.getLabel().equals("ARG0")) {
					foundArg=true;
					break;
				}
			if (!foundArg)
				for (int i=enArgBitSet.nextClearBit(0); i<enArgs.length; i=enArgBitSet.nextClearBit(i+1)) {
		    		if (enArgs[i].getScore()>=opt.recallVal &&
		    				(enArgs[i].getLabel().equals("ARG0") || enArgs[i].getLabel().equals("ARG1") && enRoles!=null && !enRoles.hasRole("ARG0"))) {	    			
		    			String head = findAlignedHead(a.sentence, Topics.getTopicHeadNode(enArgs[i].getNode()), chUtil);
		    			if (head!=null) 
		    				args.add(new ArgWeight("ARG0", head, opt.pmWeight));
		    		}
		    	}
    	}
    	

    	for (int i=0; i<chArgs.length; ++i) {
			if (chArgs[i].getLabel().equals("rel"))
				continue;
			String head = Topics.getTopicHeadword(chArgs[i].getNode());
			if (head==null)
				continue;
			if (weights[i]!=0)
				args.add(new ArgWeight(labelMod[i]==null?chArgs[i].getLabel():labelMod[i], head, weights[i]));
		}
    	updateMap(chProp.getRoleset(), args, argMap);
    	StringBuilder builder = new StringBuilder(enProp.getPredicate()+": [");
		for (PBArg arg:enArgs) {
			if (arg.getLabel().equals("rel")) continue;
			String head = Topics.getTopicHeadword(arg.getNode());
			if (head==null) continue;
			builder.append(arg.getLabel()+' '+head+',');
		}
		builder.append("]\n");
		logger.fine(builder.toString());
    }
    
    public static void main(String[] args) throws Exception {

    	Map<String, Map<String, TObjectDoubleMap<String>>> argMap = new HashMap<String, Map<String, TObjectDoubleMap<String>>>();
    	
    	MakeAlignedLDASamples options = new MakeAlignedLDASamples();
    	CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e)
        {
            logger.severe("invalid options:"+e);
            parser.printUsage(System.err);
            System.exit(0);
        }
        if (options.help){
            parser.printUsage(System.err);
            System.exit(0);
        }

        Properties props = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(options.propFile), "UTF-8");
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props); 
        
        {
	        String logLevel = props.getProperty("logger.level");
	        if (logLevel!=null) {
		        ConsoleHandler ch = new ConsoleHandler();
		        ch.setLevel(Level.parse(logLevel));
		        logger.addHandler(ch);
		        logger.setLevel(Level.parse(logLevel));
	        }
        }
        
        LanguageUtil chUtil = (LanguageUtil) Class.forName(props.getProperty("chinese.util-class")).newInstance();
        if (!chUtil.init(PropertyUtil.filterProperties(props,"chinese."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("chinese.util-class")));
            System.exit(-1);
        }
        
        LanguageUtil enUtil = (LanguageUtil) Class.forName(props.getProperty("english.util-class")).newInstance();
        if (!enUtil.init(PropertyUtil.filterProperties(props,"english."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("english.util-class")));
            System.exit(-1);
        }

        String chParseDir = props.getProperty("chinese.parseDir");
        String chPropDir = props.getProperty("chinese.propDir");
        String enParseDir = props.getProperty("english.parseDir");
        String enPropDir = props.getProperty("english.propDir");
        String alignDir = props.getProperty("alignment.dir");
        
        int aCnt=0;
        int tCnt=0;
        
        int cnt = 0;
        
        Aligner aligner = new Aligner(0.4f);
        
        BufferedReader idReader = new BufferedReader(new FileReader(options.inFileList));        
        String id = null;
        while ((id = idReader.readLine())!=null) {
        	id = id.trim();
        	String chParseName = "ch-"+id+".parse";
        	String chPropName = "ch-"+id+".prop";
        	String enParseName = "en-"+id+".parse";
        	String enPropName = "en-"+id+".prop";
        	String alignName = "align-"+id;
        	
        	TBTree[] chTrees = TBUtil.readTBFile(chParseDir, chParseName, chUtil.getHeadRules());
        	Map<String, TBTree[]> tb = new TreeMap<String, TBTree[]>();
        	tb.put(chParseName, chTrees);
        	SortedMap<Integer, List<PBInstance>> chProps = PBUtil.readPBDir(Arrays.asList(new File(chPropDir, chPropName).getCanonicalPath()), new TBReader(tb),  new DefaultPBTokenizer()).values().iterator().next();
        	
        	TBTree[] enTrees = TBUtil.readTBFile(enParseDir, enParseName, enUtil.getHeadRules());
        	tb = new TreeMap<String, TBTree[]>();
        	tb.put(enParseName, enTrees);
        	SortedMap<Integer, List<PBInstance>> enProps = PBUtil.readPBDir(Arrays.asList(new File(enPropDir, enPropName).getCanonicalPath()), new TBReader(tb),  new DefaultPBTokenizer()).values().iterator().next();

        	String[] wa = readAlignment(new File(alignDir, alignName));
        	
        	for (int i=0; i<chTrees.length; ++i) {
        		SentencePair sp = makeSentencePair(cnt++, enTrees[i], enProps.get(i), chTrees[i], chProps.get(i), wa[i], false);
        		Alignment[] al = aligner.align(sp);
        		if (al!=null) {
        			logger.fine(String.format("************** %d/%d ********************", sp.src.pbInstances.length, sp.dst.pbInstances.length));
        			logger.fine(sp.toAlignmentString()+"\n");
        			for (int p=0; p<sp.dst.pbInstances.length; ++p) {
        				boolean found = false;
        				for (Alignment alignment:al) {
        					if (alignment.dstPBIdx==p) {
        						logger.fine(p+" "+sp.dst.pbInstances[p].toText());
        						logger.fine(alignment.srcPBIdx+" "+sp.src.pbInstances[alignment.srcPBIdx].toText());
        						logger.fine(alignment.toString());
        						
        						processAlignment(alignment, argMap, chUtil, enUtil, options);

        						found = true;
        						break;
        					}
        				}
        				if (!found) {
        					logger.fine(p+" "+sp.dst.pbInstances[p].toText());
        					//if (!sp.dst.pbInstances[p].getRoleset().endsWith(".XX"))
        					updateMap(sp.dst.pbInstances[p], argMap, options.prob);
        				}
        				//System.out.print('\n');
        			}
            		aCnt += al.length;
        		}
        		tCnt += sp.dst.pbInstances.length;
        	}
        	
        }
        idReader.close();
        System.out.printf("Counts: %d/%d\n", aCnt, tCnt);
        
        /*
        List<String> fileList = FileUtil.getFileList(new File(options.inPropDir), options.inFileList, true);
        for (String fName:fileList) {
        	
        	
        	
        	Map<String, SortedMap<Integer, List<PBInstance>>> pb = PBUtil.readPBDir(Arrays.asList(fName), new TBReader(options.inParseDir, true),  new DefaultPBTokenizer());
        	for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:pb.entrySet())
        		for (Map.Entry<Integer, List<PBInstance>> e2:entry.getValue().entrySet()) {
        			TBUtil.linkHeads(e2.getValue().get(0).getTree(), chineseUtil.getHeadRules());
        			for (PBInstance instance:e2.getValue()) {
        				String predicate = instance.getPredicate().getWord().toLowerCase();
        				for (PBArg arg:instance.getArgs()) {
        					if (arg.getLabel().equals("rel"))
        						continue;
        					String head = Topics.getTopicHeadword(arg.getNode());
        					if (head==null)
        						continue;
        					Map<String, TObjectIntMap<String>> wordMap = argMap.get(arg.getLabel());
        					if (wordMap==null) {
        						wordMap = new TreeMap<String, TObjectIntMap<String>>();
        						argMap.put(arg.getLabel(), wordMap);
        					}
        					
        					TObjectIntMap<String> innerMap = wordMap.get(predicate);
        					if (innerMap==null) {
        						innerMap = new TObjectIntHashMap<String>();
        						wordMap.put(predicate, innerMap);
        					}
        					innerMap.adjustOrPutValue(head, 1, 1);
        				}
        			}
        		}
        }
        
                
        if (!options.outDir.exists())
        	options.outDir.mkdirs();
        
        */
        MakeLDASamples.makeArgOutput(argMap, options.outDir, options.wCntThreshold, options.docSizeThreshold);
	}
}
