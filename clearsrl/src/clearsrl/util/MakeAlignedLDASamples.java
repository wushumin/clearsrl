package clearsrl.util;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.FileUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.align.Aligner;
import clearsrl.align.Alignment;
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
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
	
    static String[] readAlignment(File file) {
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
    
    static SentencePair makeSentencePair(int id, TBTree chTree, List<PBInstance> chProp, TBTree enTree, List<PBInstance> enProp, String wa) throws BadInstanceException {
    	SentencePair sp = new SentencePair(id);
    	sp.dst = Sentence.parseSentence(chTree, chProp);
    	sp.src = Sentence.parseSentence(enTree, enProp);
    	sp.parseAlign(wa, true);
    	return sp;
    }
    
    public static void main(String[] args) throws Exception {

    	Map<String, Map<String, TObjectIntMap<String>>> argMap = new TreeMap<String, Map<String, TObjectIntMap<String>>>();
    	
    	MakeAlignedLDASamples options = new MakeAlignedLDASamples();
    	CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e)
        {
            System.err.println("invalid options:"+e);
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
        
        LanguageUtil chineseUtil = (LanguageUtil) Class.forName(props.getProperty("chinese.util-class")).newInstance();
        if (!chineseUtil.init(PropertyUtil.filterProperties(props,"chinese."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("chinese.util-class")));
            System.exit(-1);
        }
        
        LanguageUtil englishUtil = (LanguageUtil) Class.forName(props.getProperty("english.util-class")).newInstance();
        if (!englishUtil.init(PropertyUtil.filterProperties(props,"english."))) {
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
        
        BufferedReader idReader = new BufferedReader(new FileReader(options.inFileList));        
        String id = null;
        while ((id = idReader.readLine())!=null) {
        	id = id.trim();
        	String chParseName = "ch-"+id+".parse";
        	String chPropName = "ch-"+id+".prop";
        	String enParseName = "en-"+id+".parse";
        	String enPropName = "en-"+id+".prop";
        	String alignName = "align-"+id;
        	
        	TBTree[] chTrees = TBUtil.readTBFile(chParseDir, chParseName, chineseUtil.getHeadRules());
        	Map<String, TBTree[]> tb = new TreeMap<String, TBTree[]>();
        	tb.put(chParseName, chTrees);
        	SortedMap<Integer, List<PBInstance>> chProps = PBUtil.readPBDir(Arrays.asList(new File(chPropDir, chPropName).getCanonicalPath()), new TBReader(tb),  new DefaultPBTokenizer()).values().iterator().next();
        	
        	TBTree[] enTrees = TBUtil.readTBFile(enParseDir, enParseName, englishUtil.getHeadRules());
        	tb = new TreeMap<String, TBTree[]>();
        	tb.put(enParseName, enTrees);
        	SortedMap<Integer, List<PBInstance>> enProps = PBUtil.readPBDir(Arrays.asList(new File(enPropDir, enPropName).getCanonicalPath()), new TBReader(tb),  new DefaultPBTokenizer()).values().iterator().next();

        	String[] wa = readAlignment(new File(alignDir, alignName));
        	
        	for (int i=0; i<chTrees.length; ++i) {
        		SentencePair sp = makeSentencePair(cnt++, chTrees[i], chProps.get(i), enTrees[i], enProps.get(i), wa[i]);
        		Alignment[] al = Aligner.align(sp, 0.4f);
        		if (al!=null && al.length>0) {
        			System.out.printf("************** %d/%d ********************\n", sp.src.pbInstances.length, sp.dst.pbInstances.length);
        			
        			for (int p=0; p<sp.dst.pbInstances.length; ++p) {
        				boolean found = false;
        				for (Alignment alignment:al) {
        					if (alignment.dstPBIdx==p) {
        						System.out.println(p+" "+sp.dst.pbInstances[p].toText());
        						System.out.println(alignment.srcPBIdx+" "+sp.src.pbInstances[alignment.srcPBIdx].toText());
        						System.out.println(alignment);
        						found = true;
        						break;
        					}
        				}
        				if (!found)
        					System.out.println(p+" "+sp.dst.pbInstances[p].toText());
        				System.out.print('\n');
        			}
        			System.out.println(sp.toAlignmentString());
            		aCnt += al.length;
        		}
        		tCnt += sp.dst.pbInstances.length;
        	}
        	
        }
        
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
        
        
        MakeLDASamples.makeOutput(argMap, options.outDir, options.wCntThreshold, options.docSizeThreshold);*/
	}
}
