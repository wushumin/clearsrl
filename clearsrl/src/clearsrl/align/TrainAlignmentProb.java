package clearsrl.align;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.util.MakeAlignedLDASamples;

public class TrainAlignmentProb {
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
	
	static AlignmentProb computeProb(Aligner aligner , SentencePairReader sentencePairReader) 
    		throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException  {   

		AlignmentProb probMap = new AlignmentProb();
		
        sentencePairReader.initialize();
        while (true) {
            SentencePair sentencePair = sentencePairReader.nextPair();
            if (sentencePair==null || sentencePair.id<0) break;
             
            Alignment[] alignments = aligner.align(sentencePair);

            probMap.addSentence(sentencePair, alignments);
            
        }
        
        sentencePairReader.close();
      
        probMap.makeProb();
        
        return probMap;
    }
    
    
    public static void main(String[] args) throws Exception {
    	
    	TrainAlignmentProb options = new TrainAlignmentProb();
    	
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
        {
            FileInputStream in = new FileInputStream(options.propFile);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
        props = PropertyUtil.resolveEnvironmentVariables(props);
        
        Aligner aligner = new Aligner(Float.parseFloat(props.getProperty("align.threshold", "0.1")));
        
        String chParseDir = props.getProperty("chinese.parseDir");
        String chPropDir = props.getProperty("chinese.propDir");
        String enParseDir = props.getProperty("english.parseDir");
        String enPropDir = props.getProperty("english.propDir");
        String alignDir = props.getProperty("alignment.dir");
        
        int cnt = 0;
        
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
        
        AlignmentProb probMap = new AlignmentProb();

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

        	String[] wa = MakeAlignedLDASamples.readAlignment(new File(alignDir, alignName));
        	
        	for (int i=0; i<chTrees.length; ++i) {
        		SentencePair sp = MakeAlignedLDASamples.makeSentencePair(cnt++, chTrees[i], chProps.get(i), enTrees[i], enProps.get(i), wa[i]);
        		Alignment[] al = aligner.align(sp);
        		probMap.addSentence(sp, al);
        	}
        	
        }
        idReader.close();
        probMap.makeProb();
        
        //SentencePairReader sentencePairReader = new DefaultSentencePairReader(PropertyUtil.filterProperties(props, "align."));
           
        //AlignmentProb probMap = computeProb(aligner, sentencePairReader);
        
        SortedSet<String> srcArgs = new TreeSet<String>(probMap.srcArgProb.getKeySet());
        SortedSet<String> dstArgs = new TreeSet<String>(probMap.dstArgProb.getKeySet());
        
        
        for(String srcArg:srcArgs) {
        	CountProb<String> probs = probMap.srcArgDstArgProb.get(srcArg);
        	if (probs==null) continue;
        	System.out.printf("P(en_arg|ch_%s):", srcArg);
        	
        	for (String dstArg:dstArgs)
        		System.out.printf(" %s=%e", dstArg, probs.getProb(dstArg, false));
        	System.out.print("\n");
        }
        
        System.out.print("\n");
        
        for(String dstArg:dstArgs) {
        	CountProb<String> probs = probMap.dstArgSrcArgProb.get(dstArg);
        	if (probs==null) continue;
        	System.out.printf("P(ch_arg|en_%s):", dstArg);
        	
        	for (String srcArg:srcArgs)
        		System.out.printf(" %s=%e", srcArg, probs.getProb(srcArg, false));
        	System.out.print("\n");
        }
  
    }
}
