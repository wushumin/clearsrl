package clearsrl.align;

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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import clearsrl.util.MakeSentences;

public class TrainAlignmentProb {
	
	static final int GZIP_BUFFER = 0x40000;
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	static final float ARG_INCREMENT = 0.1f;
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-objFile",usage="object stream file to read from/write to")
    private File objFile = null; 

    @Option(name="-model",usage="output probability model")
    private File modelFile = null; 
    
    @Option(name="-fmeasure",usage="use fmeasure of alignment & prob")
    private boolean fMeasure = false;
    
    @Option(name="-round",usage="number of rounds")
    private int round = 5;
    
    @Option(name="-overWrite",usage="overwrite object stream file if it exists")
    private boolean overWrite = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    
    static List<SentencePair> readSentencePairs(Properties props, String filter1, String filter2, LanguageUtil chUtil, LanguageUtil enUtil) throws Exception {
    	
		props = PropertyUtil.filterProperties(props, filter1, true);
		props = PropertyUtil.filterProperties(props, filter2, true);
		
		List<SentencePair> sentList = new ArrayList<SentencePair>();
		
		SentencePairReader reader = new DefaultSentencePairReader(props);
		reader.initialize();
		SentencePair s = null;
		while ((s = reader.nextPair())!=null) {
			sentList.add(s);
		}
		reader.close();
		return sentList;
	}
    
    static void writeAlignments(Properties props, String filter1, String filter2, List<SentencePair> sentList, Aligner aligner, LanguageUtil chUtil, LanguageUtil enUtil, int round) throws Exception {
    	props = PropertyUtil.filterProperties(props, filter1, true);
		props = PropertyUtil.filterProperties(props, filter2, true);
		
		
		
		String[] subTypes = {"",".dstVerb",".srcVerb",".allVerbs"};
		
		
		for (int i=0; i<subTypes.length; ++i)
			subTypes[i] = subTypes[i]+".r"+round;
		
		String fName = props.getProperty("output.txt");
		PrintStream[] outs = new PrintStream[]{new PrintStream(fName.substring(0, fName.lastIndexOf('.'))+subTypes[0]+fName.substring(fName.lastIndexOf('.'))),
				new PrintStream(fName.substring(0, fName.lastIndexOf('.'))+subTypes[1]+fName.substring(fName.lastIndexOf('.'))),
				new PrintStream(fName.substring(0, fName.lastIndexOf('.'))+subTypes[2]+fName.substring(fName.lastIndexOf('.'))),
				new PrintStream(fName.substring(0, fName.lastIndexOf('.'))+subTypes[3]+fName.substring(fName.lastIndexOf('.')))};

    	PrintStream[] outhtmls = null;
    	fName = props.getProperty("output.html");		
    	if (fName!=null) {
    		outhtmls = new PrintStream[4];
    		for (int i=0; i<subTypes.length; ++i) {
				outhtmls[i] = new PrintStream(fName.substring(0, fName.lastIndexOf('.'))+subTypes[i]+fName.substring(fName.lastIndexOf('.')));
				Aligner.initAlignmentOutput(outhtmls[i]);
    		}
    	}
    	
    	for (SentencePair s:sentList) {
    		for (int i=0; i<subTypes.length; ++i) {
    			switch (i) {
    			case 0:
    				aligner.alignSrcNominal = true;
    				aligner.alignDstNominal = true;
    				break;
    			case 1:
    				aligner.alignSrcNominal = true;
    				aligner.alignDstNominal = false;
    				break;
    			case 2:
    				aligner.alignSrcNominal = false;
    				aligner.alignDstNominal = true;
    				break;
    			case 3:
    				aligner.alignSrcNominal = false;
    				aligner.alignDstNominal = false;
    				break;
    			default:
    				break;
    			}
    			
    			MakeSentences.writeAlignment(s, outs[i], outhtmls==null?null:outhtmls[i], aligner, chUtil, enUtil);
    			
    			/*
				Alignment[] alignments = aligner.align(s);
				for (Alignment alignment:alignments)
					outs[i].printf("%d,%s;[%s,%s]\n",s.id+1, alignment.toString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());
				if (outhtmls!=null)
					Aligner.printAlignment(outhtmls[i], s, alignments);*/
    		}
		}
    	for (int i=0; i<subTypes.length; ++i)
    		outs[i].close();
    	if (outhtmls!=null)
    		for (int i=0; i<subTypes.length; ++i)
    			Aligner.finalizeAlignmentOutput(outhtmls[i]);
    }
    
	
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
	
	class AlignJob implements Runnable{
		SentencePair sp;
		int idx;
		Aligner[] aligners;
		double[] tScores;
		int[] aCnts;
		
		public AlignJob(SentencePair sp, int idx, Aligner[] aligners, double[] tScores, int[] aCnts) {
			this.sp = sp;
			this.idx = idx;
			this.aligners = aligners;
			this.tScores = tScores;
			this.aCnts = aCnts;
		}

		@Override
        public void run() {
	        // TODO Auto-generated method stub
			Alignment[] al = aligners[idx].align(sp);
			synchronized (aligners[idx]) {
				for (Alignment alignment:al) 
					tScores[idx]+=alignment.getCompositeScore();
				aCnts[idx]+=al.length;
			}
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
	
	void filterProps(Sentence sentence) {
		if (sentence.pbInstances==null) return;
		List<PBInstance> propList = new ArrayList<PBInstance>(sentence.pbInstances.length);
		for (PBInstance inst:sentence.pbInstances)
			if (!inst.getRoleset().endsWith(".XX"))
				propList.add(inst);
		if (propList.size()!=sentence.pbInstances.length)
			sentence.pbInstances = propList.toArray(new PBInstance[propList.size()]);
	}

	AlignmentProb readCorpus(File fileList, File objFile, Properties props, LanguageUtil chUtil, LanguageUtil enUtil, Aligner aligner) throws IOException, BadInstanceException {
        
		double tScore = 0;
		int aCnt = 0;
		
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
        		SentencePair sp = MakeAlignedLDASamples.makeSentencePair(cnt++, chTrees[i], chProps.get(i), enTrees[i], enProps.get(i), wa[i], true);
            	if (objStream != null) {
            		objStream.writeObject(sp);
                    if (sp.id%5000==999)
                        objStream.reset();
            	}
            	filterProps(sp.src);
            	filterProps(sp.dst);
        		Alignment[] al = aligner.align(sp);
        		probMap.addSentence(sp, al);
        	
				for (Alignment alignment:al) 
					tScore+=alignment.getCompositeScore();
				aCnt+=al.length;	
        		
        	}
        }
        idReader.close();
        if (objStream != null)
        	objStream.close();

        System.out.printf("alignments: %d, total score: %f\n", aCnt, tScore);
		
		return probMap;
	}
	
	void processSentences(AlignmentProb probMap, Aligner aligner, List<SentencePair> sentences) {
		for (SentencePair sp:sentences) {
			Alignment[] al = aligner.align(sp);
			probMap.addSentence(sp, al);
		}
	}
	
	int findBestAligner(List<SentencePair> spPairs, Aligner[] aligners){
		double[] tScores = new double[aligners.length];
		int[] aCnts = new int[aligners.length];

		for (SentencePair sp:spPairs) {
        	//filterProps(sp.src);
        	//filterProps(sp.dst);
        	
        	for (int i=0; i<aligners.length; ++i) {
        		Alignment[] al = aligners[i].align(sp);
				for (Alignment alignment:al) 
					tScores[i]+=alignment.getCompositeScore();
				aCnts[i]+=al.length;
        	}
		}
        
		double highScore = Double.MIN_VALUE;
		int bestIdx = -1;
		
		for (int i=0; i<tScores.length; i++) {
			if (tScores[i]>highScore) {
				highScore = tScores[i];
				bestIdx = i;
			}
			System.out.printf("alpha = %f, beta = %f, cnt = %d, score = %f\n", aligners[i].predProbWeight, aligners[i].argProbWeight, aCnts[i], tScores[i]);
		}
		
		System.out.printf("best result: alpha = %f, beta = %f, cnt = %d, score = %f\n", aligners[bestIdx].predProbWeight, aligners[bestIdx].argProbWeight, aCnts[bestIdx], tScores[bestIdx]);
		
		return bestIdx;
	}
	

	int findBestAligner(File objFile, Aligner[] aligners)  throws IOException {
		double[] tScores = new double[aligners.length];
		int[] aCnts = new int[aligners.length];
		
		int cpus = Runtime.getRuntime().availableProcessors();
        // more than 4 processors is probably hyper-thread cores
        if (cpus>4) 
        	cpus = cpus/2;
		
		ExecutorService executor = //Executors.newFixedThreadPool(cpus-1);
				new ThreadPoolExecutor(cpus-1, cpus-1, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(1000), new ThreadPoolExecutor.CallerRunsPolicy());
		
		ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(objFile),GZIP_BUFFER),GZIP_BUFFER*4));

		int cnt = 0;
		while (true)
			try {
				SentencePair sp = (SentencePair) inStream.readObject();
            	filterProps(sp.src);
            	filterProps(sp.dst);
            	
            	for (int i=0; i<aligners.length; ++i) {
            		executor.execute(new AlignJob(sp, i, aligners, tScores, aCnts));
            	}
        		if (++cnt%10000==0)
        			logger.info(String.format("read %d sentences", cnt));
			} catch (Exception e) {
				if (!(e instanceof EOFException))
					e.printStackTrace();
				break;
			}
		inStream.close();
		
		executor.shutdown();
        
        while (true)
            try {
                if (executor.awaitTermination(1, TimeUnit.MINUTES)) break;
            } catch (InterruptedException e) {
                logger.severe(e.getMessage());
            }
		
		double highScore = Double.MIN_VALUE;
		int bestIdx = -1;
		
		for (int i=0; i<tScores.length; i++) {
			if (tScores[i]>highScore) {
				highScore = tScores[i];
				bestIdx = i;
			}
			System.out.printf("alpha = %f, beta = %f, cnt = %d, score = %f\n", aligners[i].predProbWeight, aligners[i].argProbWeight, aCnts[i], tScores[i]);
		}
		
		System.out.printf("best result:\nalpha = %f, beta = %f, cnt = %d, score = %f\n", aligners[bestIdx].predProbWeight, aligners[bestIdx].argProbWeight, aCnts[bestIdx], tScores[bestIdx]);
		
		return bestIdx;
	}
	
	AlignmentProb processCorpus(File objFile, Aligner aligner, Aligner oldAligner) throws IOException {
		double tScore = 0;
		int aCnt = 0;
		
		AlignmentProb probMap = new AlignmentProb();
		
		ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(objFile),GZIP_BUFFER),GZIP_BUFFER*4));

		int cnt = 0;
		while (true)
			try {
				SentencePair sp = (SentencePair) inStream.readObject();
            	filterProps(sp.src);
            	filterProps(sp.dst);
				Alignment[] al = aligner.align(sp);
				for (Alignment alignment:al) 
					tScore+=alignment.getCompositeScore();
				
				if (oldAligner!=null) {
					Alignment[] oldAl = oldAligner.align(sp);
					boolean same = al.length==oldAl.length;
					
					if (same)
						for (int i=0; i<al.length; ++i)
							if (al[i].srcPBIdx!=oldAl[i].srcPBIdx || al[i].dstPBIdx!=oldAl[i].dstPBIdx) {
								same = false;
								break;
							}
					
					/*if (!same) {
						System.out.println(sp);
						System.out.println(Arrays.toString(al));
						System.out.println(Arrays.toString(oldAl));
						System.out.print("\n");
					}*/
				}
				
				aCnt+=al.length;
				
        		probMap.addSentence(sp, al);
        		if (++cnt%10000==0)
        			logger.info(String.format("read %d sentences", cnt));
			} catch (Exception e) {
				if (!(e instanceof EOFException))
					e.printStackTrace();
				break;
			}
		inStream.close();
		
		
		
        System.out.printf("alignments: %d, total score: %f\n\n", aCnt, tScore);
		
		//probMap.makeProb();
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
        
        boolean chAlignNominal = true;
        boolean enAlignNominal = true;
        
        Aligner aligner = new Aligner(0.05f, chAlignNominal, enAlignNominal, chUtil, enUtil, null, 0, 0, options.fMeasure);
       
        List<SentencePair> bcSentences = readSentencePairs(props, "bc.", "auto.", chUtil, enUtil);
        List<SentencePair> nwSentences = readSentencePairs(props, "nw.", "auto.", chUtil, enUtil);
        List<SentencePair> bcGWASentences = readSentencePairs(props, "bc.", "gwa.", chUtil, enUtil);
        List<SentencePair> nwGWASentences = readSentencePairs(props, "nw.", "gwa.", chUtil, enUtil);
        List<SentencePair> bcBerkSentences = readSentencePairs(props, "bc.", "berk.", chUtil, enUtil);
        List<SentencePair> nwBerkSentences = readSentencePairs(props, "nw.", "berk.", chUtil, enUtil); 
        //List<SentencePair> nwtestSentences = readSentencePairs(props, "nwtest.", "auto.", chUtil, enUtil);
        //List<SentencePair> nwtestGWASentences = readSentencePairs(props, "nwtest.", "gwa.", chUtil, enUtil); 
        
        AlignmentProb probMap = null;
        if (options.objFile!=null && options.objFile.exists() && ! options.overWrite)
        	probMap = options.processCorpus(options.objFile, aligner, null);
        else
        	probMap = options.readCorpus(options.inFileList, options.objFile, props, chUtil, enUtil, aligner);
        
        options.processSentences(probMap, aligner, bcSentences);
        options.processSentences(probMap, aligner, nwSentences);
        
        probMap.makeProb();
        
        if (options.modelFile!=null) {
        	ObjectOutputStream objStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(options.modelFile),GZIP_BUFFER),GZIP_BUFFER*4));
        	objStream.writeObject(probMap);
        	objStream.close();
        }


        float[] alphaList = {0.15f, 0.175f, 0.2f, 0.225f, 0.25f, 0.3f, 0.35f};
        float[] betaList = {0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f};
        
        float bestAlpha = 0.2f;
        float bestBeta = 0.1f;

        float increment = ARG_INCREMENT;
        
        Aligner[] aligners = new Aligner[alphaList.length*betaList.length];
        
        aligner.alignThreshold = 0.2f*(1-0.8f*bestBeta);
        
        for (int r=0; r<options.round; ++r) {
        
        	AlignmentProb oldProbMap = probMap;
 	        Aligner oldAligner = aligner; 	        
 	        
 	        aligner = new Aligner(oldAligner.alignThreshold, chAlignNominal, enAlignNominal, chUtil, enUtil, oldProbMap, bestAlpha, bestBeta, options.fMeasure);
 	        
        	int idx = 0;
 	        for (float alpha:alphaList)
	        	for (float beta:betaList)
	        		aligners[idx++] = new Aligner(0.2f*(1-0.8f*beta), chAlignNominal, enAlignNominal, chUtil, enUtil, aligner.probMap, alpha, beta, options.fMeasure);
 	        
 	        //aligner.alignThreshold = 0.18f*(1+increment)*(r+1);
        	
 	        writeAlignments(props, "bc.", "auto.", bcSentences, aligner, chUtil, enUtil, r);
 	        writeAlignments(props, "nw.", "auto.", nwSentences, aligner, chUtil, enUtil, r);
 	       
 	        aligner.alignThreshold *= 1.5;
 	        
 	        writeAlignments(props, "bc.", "berk.", bcBerkSentences, aligner, chUtil, enUtil, r);
 	        writeAlignments(props, "nw.", "berk.", nwBerkSentences, aligner, chUtil, enUtil, r);
 	       
 	        writeAlignments(props, "bc.", "gwa.", bcGWASentences, aligner, chUtil, enUtil, r);
 	        writeAlignments(props, "nw.", "gwa.", nwGWASentences, aligner, chUtil, enUtil, r);
        	//System.out.print("bc.auto "); writeAlignments(props, "bc.", "auto.", bcSentences, aligners[options.findBestAligner(bcSentences, aligners)], r);
        	//System.out.print("bc.gwa  "); writeAlignments(props, "bc.", "gwa.", bcGWASentences, aligners[options.findBestAligner(bcGWASentences, aligners)], r);
        	//System.out.print("bc.berk "); writeAlignments(props, "bc.", "berk.", bcBerkSentences, aligners[options.findBestAligner(bcBerkSentences, aligners)], r);
        	//System.out.print("nw.auto "); writeAlignments(props, "nw.", "auto.", nwSentences, aligners[options.findBestAligner(nwSentences, aligners)], r);
        	//System.out.print("nw.gwa  "); writeAlignments(props, "nw.", "gwa.", nwGWASentences, aligners[options.findBestAligner(nwGWASentences, aligners)], r);
        	//System.out.print("nw.berk "); writeAlignments(props, "nw.", "berk.", nwBerkSentences, aligners[options.findBestAligner(nwBerkSentences, aligners)], r);
        	//System.out.print("nwtest.auto "); writeAlignments(props, "nwtest.", "auto.", nwtestSentences, aligners[options.findBestAligner(nwtestSentences, aligners)], r);
        	
        	if (r+1==options.round) break;
        	
        	//aligner.alignThreshold = 0.05f;
 
 	        aligner = aligners[options.findBestAligner(options.objFile, aligners)];
 	        bestAlpha = aligner.predProbWeight;
 	        bestBeta = aligner.argProbWeight;
        	probMap = options.processCorpus(options.objFile, aligner, oldAligner);

	        //aligner.alignThreshold = 0.05f;
	        
	        options.processSentences(probMap, aligner, bcSentences);
	        options.processSentences(probMap, aligner, nwSentences);
	        probMap.makeProb();
	        
	        
	        /*if (options.fMeasure)
	        	increment += ARG_INCREMENT;
	        else 
	        	increment = 1-(1-ARG_INCREMENT)*(1-increment);*/
	        
        }
        
        /*
        options.printMap(probMap.srcArgDstArgProb, System.out, "P(ch_arg|en_%s):", 50);
        System.out.println("\n");
        options.printMap(probMap.dstArgSrcArgProb, System.out, "P(en_arg|ch_%s):", 50);
        System.out.println("\n");
        
        options.printMap(probMap.srcPredDstPredProb, System.out, "P(ch_pred|en_%s):", 30);
        System.out.println("\n");
        options.printMap(probMap.dstPredSrcPredProb, System.out, "P(en_pred|ch_%s):", 30);
        System.out.println("\n");
        */
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
