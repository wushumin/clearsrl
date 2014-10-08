package clearsrl.align;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
import clearsrl.align.SentencePair.BadInstanceException;
import clearsrl.util.MakeAlignedLDASamples;

public class TrainAlignmentProb {
	
	static final int GZIP_BUFFER = 0x40000;
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-objFile",usage="object stream file to read from/write to")
    private File objFile = null; 
    
    @Option(name="-overWrite",usage="overwrite object stream file if it exists")
    private boolean overWrite = false;
    
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
    
    
	class KeyDoublePair<T extends Comparable<T>> implements Comparable<KeyDoublePair<T>>{
		T key;
		double val;

		public KeyDoublePair(T key, double val) {
			this.key=key;
			this.val=val;
		}
		
		@Override
        public int compareTo(KeyDoublePair<T> rhs) {
			if (this.val!=rhs.val)
				return this.val>rhs.val?-1:1;
	        return this.key.compareTo(rhs.key);
        }
	}
	
	void printMap(SortedMap<String, CountProb<String>> probMap, PrintStream out, String formatString, int maxCnt) {
		for (Map.Entry<String, CountProb<String>>entry:probMap.entrySet()) {
			CountProb<String> probs = entry.getValue();
        	out.printf(formatString, entry.getKey());
        	
        	Set<String> keySet = entry.getValue().getKeySet();
        	
            @SuppressWarnings("unchecked")
            KeyDoublePair<String>[] pairs = new KeyDoublePair[keySet.size()];
        	int cnt=0;
        	for (String key:keySet)
        		pairs[cnt++] = new KeyDoublePair<String>(key, probs.getProb(key, false));
        	
        	Arrays.sort(pairs);
        	cnt=0;
        	for (KeyDoublePair<String> pair:pairs) {
        		out.printf(" %s=%e", pair.key, pair.val);
        		if (++cnt>maxCnt)
        			break;
        	}
        	out.print("\n");
		}
			
	}
	
	AlignmentProb readCorpus(File fileList, File objFile, Properties props, LanguageUtil chUtil, LanguageUtil enUtil, Aligner aligner) throws IOException, BadInstanceException {
        
		int cnt=0;
		
        String chParseDir = props.getProperty("chinese.parseDir");
        String chPropDir = props.getProperty("chinese.propDir");
        String enParseDir = props.getProperty("english.parseDir");
        String enPropDir = props.getProperty("english.propDir");
        String alignDir = props.getProperty("alignment.dir");
		
		AlignmentProb probMap = new AlignmentProb();
		
		ObjectOutputStream objStream = null;
		if (objFile != null)
			try {
				objStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(objFile),GZIP_BUFFER),GZIP_BUFFER*4));
			} catch (Exception e){
				e.printStackTrace();
			}
		BufferedReader idReader = new BufferedReader(new FileReader(fileList));        
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

            	if (objStream != null) {
            		objStream.writeObject(sp);
                    if (sp.id%1000==999)
                        objStream.reset();
            	}
        		
        	}
        }
        idReader.close();
        if (objStream != null)
        	objStream.close();

        probMap.makeProb();
		
		return probMap;
	}
	
	AlignmentProb processCorpus(File objFile, Aligner aligner) throws IOException {
		AlignmentProb probMap = new AlignmentProb();
		
		ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(objFile),GZIP_BUFFER),GZIP_BUFFER*4));

		while (true)
			try {
				SentencePair sp = (SentencePair) inStream.readObject();
				Alignment[] al = aligner.align(sp);
        		probMap.addSentence(sp, al);
			} catch (Exception e) {
				if (!(e instanceof EOFException))
					e.printStackTrace();
				break;
			}
		inStream.close();
		
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
       
        AlignmentProb probMap = null;
        if (options.objFile!=null && options.objFile.exists() && ! options.overWrite)
        	probMap = options.processCorpus(options.objFile, aligner);
        else
        	probMap = options.readCorpus(options.inFileList, options.objFile, props, chUtil, enUtil, aligner);
        
        
        options.printMap(probMap.srcArgDstArgProb, System.out, "P(ch_arg|en_%s):", 30);
        System.out.println("\n");
        options.printMap(probMap.dstArgSrcArgProb, System.out, "P(en_arg|ch_%s):", 30);
        System.out.println("\n");
        
        options.printMap(probMap.srcPredDstPredProb, System.out, "P(ch_pred|en_%s):", 30);
        System.out.println("\n");
        options.printMap(probMap.dstPredSrcPredProb, System.out, "P(en_pred|ch_%s):", 30);
        System.out.println("\n");
        
        //SentencePairReader sentencePairReader = new DefaultSentencePairReader(PropertyUtil.filterProperties(props, "align."));
           
        //AlignmentProb probMap = computeProb(aligner, sentencePairReader);
        /*
        Set<String> srcArgs = probMap.srcArgProb.getKeySet();
        Set<String> dstArgs = probMap.dstArgProb.getKeySet();
 
        for(String srcArg:srcArgs) {
        	CountProb<String> probs = probMap.srcArgDstArgProb.get(srcArg);
        	if (probs==null) continue;
        	System.out.printf("P(ch_arg|en_%s):", srcArg);
        	
        	for (String dstArg:dstArgs)
        		System.out.printf(" %s=%e", dstArg, probs.getProb(dstArg, false));
        	System.out.print("\n");
        }
        
        System.out.print("\n");
        
        for(String dstArg:dstArgs) {
        	CountProb<String> probs = probMap.dstArgSrcArgProb.get(dstArg);
        	if (probs==null) continue;
        	System.out.printf("P(en_arg|ch_%s):", dstArg);

        	for (String srcArg:srcArgs)
        		System.out.printf(" %s=%e", srcArg, probs.getProb(srcArg, false));
        	System.out.print("\n");
        }
        
        */
        
    }
}
