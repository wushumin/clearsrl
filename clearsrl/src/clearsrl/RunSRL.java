package clearsrl;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.treebank.TBUtil.Dependency;
import clearcommon.util.FileUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.ParseCorpus;
import clearcommon.util.PropertyUtil;
import clearcommon.util.LanguageUtil.POS;
import clearsrl.SRInstance.OutputFormat;
import clearsrl.Sentence.Source;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class RunSRL {
    private static Logger logger = Logger.getLogger("clearsrl");

    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
    
    @Option(name="-in",usage="input file/directory")
    private File inFile = null; 
    
    @Option(name="-depIn",usage="dependency input file/directory")
    private File depInFile = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
 
    @Option(name="-compressOutput",usage="Compress the SRL output")
    private boolean compressOutput = false; 
    
    @Option(name="-model",usage="model file to use")
    private String modelFName = null; 
    
    @Option(name="-out",usage="output file/directory")
    private File outFile = null;
    
    @Option(name="-usePBCorpus",usage="use specified PropBank corpus to find predicates")
    private String usePBCorpus = null; 
    
    @Option(name="-parsed",usage="input is parse trees")
    private boolean parsed = false;
    
    @Option(name="-outputParse",usage="output the intermediary parse")
    private boolean outputParse = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    @Option(name="-format",usage="srl output format: TEXT/PROPBANK")
    private SRInstance.OutputFormat outputFormat = null;
    
    private SRLModel model; 
    private LanguageUtil langUtil;
    
    static final float THRESHOLD=0.8f;

    ExecutorService executor;
    class Output {
    	TBTree tree;
    	List<SRInstance> srls;
    	public Output(TBTree tree, List<SRInstance> srls) {
    		this.tree = tree;
    		this.srls = srls;
    	}
    }
    
    ArrayBlockingQueue<Future<Output>> srlQueue;
    
    class RunnableSentence implements Callable<Output>  {
    	Sentence sent = null;
    	
    	public RunnableSentence(Sentence sent) {
    		this.sent = sent;
    	}
    	
		@Override
        public Output call() {
			if (sent==null) 
				return null;
			TBTree tree = sent.parse==null?sent.treeTB:sent.parse;
            return new Output(tree, model.predict(tree, sent.propPB, sent.depEC, sent.namedEntities));
		}
    }
    
    class SRLWriter extends Thread {
        PrintWriter writer;
        LanguageUtil langUtil;

        public SRLWriter(PrintWriter writer, LanguageUtil langUtil) {
            this.writer    = writer;
            this.langUtil  = langUtil;
        }
        
        public void run() {
            while (true) {
            	Future<Output> future;
            	try {
	                future = srlQueue.take();
                } catch (InterruptedException e) {
	                continue;
                }
            	
            	Output output = null;
            	while (true)
	                try {
	                	output = future.get();
	                	break;
	                } catch (InterruptedException e) {
	                	continue;
	                } catch (ExecutionException e) {
		                e.printStackTrace();
		                break;
	                }

            	if (output==null) break;
            	
            	if (outputFormat.equals(OutputFormat.CONLL))
                    writer.println(CoNLLSentence.toString(output.tree, output.srls.toArray(new SRInstance[output.srls.size()])));
            	else if (outputFormat.equals(OutputFormat.CONLL_DEP))
                    writer.println(CoNLLSentence.toDepString(output.tree, output.srls.toArray(new SRInstance[output.srls.size()])));
                else
                    for (SRInstance instance:output.srls)
                        writer.println(instance.toString(outputFormat));
            	
            }
            writer.flush();
            logger.info("done");
        }
    }
    
    void processInput(Reader reader, BufferedReader depReader, String inName, PrintWriter writer, String outName, ParseCorpus parser, Sentence[]  sentences, LanguageUtil langUtil) throws IOException, ParseException, InterruptedException
    {
        logger.info("Processing "+(inName==null?"stdin":inName)+", outputing to "+(outName==null?"stdout":outName));
        
        Thread writerThread = new SRLWriter(writer, langUtil);
        writerThread.start();
        
        Reader parseIn = null;
        PrintWriter parseOut = null;
        PipedWriter pipedWriter = null;

        if (sentences==null) {
	        if (parsed)
	        		parseIn = reader;
	        else {
	            pipedWriter = new PipedWriter();
	            parseIn = new PipedReader(pipedWriter);
	            
	            parser.parse(reader, pipedWriter);
	            
	            if (outputParse)
	            {
	                if (outName==null)
	                    inName = "stdout.parse";
	                else
	                    inName = (outName.endsWith(".prop") ? outName.substring(0, outName.length()-5) : outName) + ".parse";
	                parseOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(inName), "UTF-8"));
	            }
	        }
	        
	        TBFileReader treeReader = new SerialTBFileReader(parseIn, inName);
	        
	        TBTree tree;
	        while ((tree=treeReader.nextTree())!=null){
	        	
	        	if (depReader==null)
	        		TBUtil.linkHeads(tree, langUtil.getHeadRules());
	        	else
	        		TBUtil.addDependency(tree, TBUtil.readCoNLLTree(depReader, 6, 7));
	        	
	        	Future<Output> future = executor.submit(new RunnableSentence(new Sentence(tree, null, null, null, null, null)));
	            while(true)
	    	        try {
	    	        	srlQueue.put(future);
	    	        	break;
	    	        } catch (InterruptedException e) {
	    	        }  
	            if (parseOut!=null)
	                parseOut.println(tree.toString());
	        }
	        treeReader.close();
        } else {
    		for (Sentence sentence:sentences) {
    			Future<Output> future = executor.submit(new RunnableSentence(sentence));
	            while(true)
	    	        try {
	    	        	srlQueue.put(future);
	    	        	break;
	    	        } catch (InterruptedException e) {
	    	        }  
    		}
        }
        
        if (parseIn!=null) parseIn.close();
        if (parseOut!=null) parseOut.close();
        if (pipedWriter!=null) pipedWriter.close();
        if (reader!=null) reader.close();

        Future<Output> future = executor.submit(new RunnableSentence(null));
        while (true)
		    try {
		        srlQueue.put(future);
		        break;
	        } catch (InterruptedException e) {
	        }

        writerThread.join();
    }
    
    public static void main(String[] args) throws Exception
    {   
        RunSRL options = new RunSRL();
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
        
        Properties runSRLProps = PropertyUtil.filterProperties(props, "srl.", true);
        runSRLProps = PropertyUtil.filterProperties(runSRLProps, "run.", true);
        
        {
	        String logLevel = runSRLProps.getProperty("logger.level");
	        if (logLevel!=null) {
		        ConsoleHandler ch = new ConsoleHandler();
		        ch.setLevel(Level.parse(logLevel));
		        logger.addHandler(ch);
		        logger.setLevel(Level.parse(logLevel));
		        Logger.getLogger("clearcommon").addHandler(ch);
		        Logger.getLogger("clearcommon").setLevel(Level.parse(logLevel));
	        }
        }
        
        logger.info(PropertyUtil.toString(runSRLProps));
         
        if (options.outFile!=null) runSRLProps.setProperty("output.dir", options.outFile.getAbsolutePath());

        Properties langProps = PropertyUtil.filterProperties(runSRLProps, runSRLProps.getProperty("language").trim()+'.', true);
        options.langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!options.langUtil.init(langProps)) {
            logger.severe(String.format("Language utility (%s) initialization failed",runSRLProps.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        if (options.modelFName!=null)
        	runSRLProps.setProperty("model_file", options.modelFName);
        
        logger.info("Loading model "+runSRLProps.getProperty("model_file"));
        
        ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(runSRLProps.getProperty("model_file"))));
        options.model = (SRLModel)mIn.readObject();
        mIn.close();
       
        logger.info("model loaded");
        
        if (runSRLProps.getProperty("predicateOverride")!=null) {
        	Set<String> overrideSet = new HashSet<String>();
        	try (BufferedReader reader = new BufferedReader(new FileReader(runSRLProps.getProperty("predicateOverride")))) {
                String line;
                while ((line=reader.readLine())!=null) {
                	if (line.trim().isEmpty()) continue;
                	overrideSet.add(line.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        	if (!overrideSet.isEmpty()) {
        		options.model.setPredicateOverride(overrideSet);
        		logger.info("using "+overrideSet.size()+" predicate classification overrides");
        	}
        } else {
        	
        }
        	
        
        BitSet mask1 = options.model.argLabelClassifier.getFeatureMask();
        BitSet mask2 = options.model.argLabelStage2Classifier==null?new BitSet():(options.model.argLabelStage2Classifier.getFeatureMask());
        
        System.out.printf("%d features, reduceable to %d %d\n", options.model.argLabelFeatures.getDimension(), mask1.cardinality(), mask2.cardinality());
        
        boolean useStage2 = !runSRLProps.getProperty("useStage2", "true").equals("false");
        if (!useStage2)
        	options.model.argLabelStage2Classifier = null;
        
        logger.info("Argument features: "+options.model.argLabelFeatures.getFeatures());
        //for (EnumSet<SRLModel.Feature> feature:model.featureSet)
        //  logger.info(SRLModel.toString(feature));
        
        if (options.model.predicateModel!=null)
        {
            logger.info("Predicate features: "+options.model.predicateModel.getFeatures());
            //for (EnumSet<SRLModel.PredFeature> feature:model.predicateFeatureSet)
            //  logger.info(SRLModel.toString(feature));
        }
        
        options.model.setLanguageUtil(options.langUtil);
       
        int threads = -1;
        String threadCnt = runSRLProps.getProperty("threads","auto");

        if (threadCnt.equals("auto")) {
            threads = Runtime.getRuntime().availableProcessors();
            // more than 4 processors is probably hyper-thread cores
            if (threads>4) 
            	threads = threads/2;
        } else 
            try {
                threads = Integer.parseInt(threadCnt);
            } catch (NumberFormatException e) {
                threads = -1;
            } finally {
                if (threads <=0) threads = Runtime.getRuntime().availableProcessors();
            }
        if (threads>40) threads=40;
        logger.info(String.format("Using %d threads\n",threads));
        
        options.executor = Executors.newFixedThreadPool(threads);
        options.srlQueue = new ArrayBlockingQueue<Future<Output>>(threads*20);
        
        int pCount = 0;
        String dataFormat = runSRLProps.getProperty("data.format", "default");
        if (!options.parsed) 
        {
            dataFormat = "default";
            runSRLProps.remove("pbdir");
        }
        /*
        if (dataFormat.equals("default"))
        {       
            String testRegex = props.getProperty("test.regex");
            //Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
            Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = PBUtil.readPBDir(props.getProperty("pbdir"), testRegex, props.getProperty("tbdir"), false);
            Map<String, TBTree[]> parsedTreeBank = TBUtil.readTBDir(props.getProperty("parsedir"), testRegex);
            
            for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
                for (TBTree tree: entry.getValue())
                    TBUtil.findHeads(tree.getRootNode(), headrules);
            
            model.initScore();
            for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
                for (TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
                {
                    iter.advance();
                    for (PBInstance pbInstance:iter.value())
                    {
                        ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                        ArrayList<TObjectFloatHashMap<String>> labels = new ArrayList<TObjectFloatHashMap<String>>();
                        if (pbInstance.tree.getTokenCount()== parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()].getTokenCount())
                            SRLUtil.getSamplesFromParse(new SRInstance(pbInstance), parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()], 
                                THRESHOLD, argNodes, labels);
                        
                        SRInstance trainInstance = new SRInstance(pbInstance.predicateNode, parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()]);
                        for (int i=0; i<labels.size(); ++i)
                        {
                            if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
                            trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
                        }
                        trainInstance.addArg(new SRArg("rel", pbInstance.predicateNode));
                        
                        model.score.addResult(SRLUtil.convertSRInstanceToTokenMap(trainInstance), SRLUtil.convertSRInstanceToTokenMap(new SRInstance(pbInstance)));
                        String a = new SRInstance(pbInstance).toString().trim();
                        String b = trainInstance.toString().trim();
                        if (!a.equals(b))
                        {
                            System.out.println(a);
                            System.out.println(b);
                            for (SRArg arg: trainInstance.args)
                                System.out.println(arg.node.toParse());
                            System.out.print("\n");
                        }
                    }
                }
            model.score.printResults(System.out);

            model.initScore();
        
            for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
            {
                TBTree[] trees = parsedTreeBank.get(entry.getKey());
                for (TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
                {
                    for (PBInstance pbInstance:iter.value())
                    {
                        iter.advance();
                        SRInstance pInstance = null;
                        if (pbInstance.tree.getTokenCount()==trees[pbInstance.tree.getTreeIndex()].getTokenCount())
                            pInstance = new SRInstance(trees[pbInstance.tree.getTreeIndex()].getRootNode().getTokenNodes().get(pbInstance.predicateNode.tokenIndex), trees[pbInstance.tree.getTreeIndex()]);
                        else
                            pInstance = new SRInstance(pbInstance.predicateNode, trees[pbInstance.tree.getTreeIndex()]);
                        SRInstance instance = new SRInstance(pbInstance);
                        cCount += model.predict(pInstance, instance, null);
                        pCount += pInstance.getArgs().size()-1;
                        //System.out.println(instance);
                        //System.out.println(pInstance+"\n");
                    }
                } 
            }
        }
        */
        boolean predictPredicate = (runSRLProps.getProperty("predict_predicate")!=null && 
                !runSRLProps.getProperty("predict_predicate").equals("false") &&
                runSRLProps.getProperty("pbdir")!=null);
        
        if (predictPredicate==true && options.model.predicateModel==null)
        {
            logger.severe("Predicate Classifier not trained!");
            System.exit(-1);
        }
        
        if (options.outputFormat==null)
            options.outputFormat = SRInstance.OutputFormat.valueOf(runSRLProps.getProperty("output.format",SRInstance.OutputFormat.TEXT.toString()));
        
        //File outputDir = null;
        //{
        //    String outputDirName = props.getProperty("output.dir");
        //    if (outputDirName != null) outputDir = new File(outputDirName);
        //}

        PrintStream output = null;
        if (options.outFile!=null)
        {
            if (options.inFile!=null && !options.inFile.isDirectory())
                try {
                    output = new PrintStream(options.outFile);
                } catch (FileNotFoundException e) {
                    logger.severe("Cannot create output file "+options.outFile.getAbsolutePath());
                    System.exit(1);
                }
            else if (!options.outFile.isDirectory() && !options.outFile.mkdirs())
            {
                logger.severe("Cannot create output directory "+options.outFile.getAbsolutePath());
                System.exit(1);
            }
        }
        else
            output = System.out;
        
        //SRLScore score = new SRLScore(new TreeSet<String>(Arrays.asList(model.labelStringMap.keys(new String[model.labelStringMap.size()]))));
        //SRLScore score2 = new SRLScore(new TreeSet<String>(Arrays.asList(model.labelStringMap.keys(new String[model.labelStringMap.size()]))));
        if (!dataFormat.equals("conll")) {   
            Map<String, Sentence[]>  sentenceMap = null;
            if (options.usePBCorpus!=null)
            {
            	Properties srcProps = PropertyUtil.filterProperties(props, options.usePBCorpus+".", true);
            	
            	sentenceMap = Sentence.readCorpus(srcProps, Source.PARSE, Sentence.readSources(runSRLProps.getProperty("corpus.source")), options.langUtil);            	
            	/*
                String treeRegex = runSRLProps.getProperty("tb.regex");
                String propRegex = runSRLProps.getProperty("pb.regex");

                treeBank = TBUtil.readTBDir(runSRLProps.getProperty("tbdir"), treeRegex, options.langUtil.getHeadRules());
                //Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
                PBTokenizer tokenzier = runSRLProps.getProperty("pb.tokenizer")==null?(runSRLProps.getProperty("data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(runSRLProps.getProperty("pb.tokenizer")).newInstance();

                //propBank = PBUtil.readPBDir(runSRLProps.getProperty("pbdir"), propRegex, 
                //                    new TBReader(treeBank),tokenzier);*/
            }
            
            ParseCorpus phraseParser = null;
            
            if (!options.parsed)
            {
                phraseParser = new ParseCorpus(true);
                phraseParser.initialize(PropertyUtil.filterProperties(props, "parser."));
            }   
            if (options.inFile==null && sentenceMap==null)
                options.processInput(new InputStreamReader(System.in), null, null, new PrintWriter(System.out), null, phraseParser, null, options.langUtil);
            else {
                List<String> fileList = null;
                
                if (sentenceMap==null)
                	fileList = options.inFileList==null?FileUtil.getFiles(options.inFile, runSRLProps.getProperty("regex",".*\\.(parse|txt)"))
                        :FileUtil.getFileList(options.inFile, options.inFileList);
                else
                	fileList = new ArrayList<String>(sentenceMap.keySet());
                    
                for (String fName:fileList) {
                    Reader reader = null;
                    BufferedReader depReader = null;
                    Sentence[] sentences = null;
                    
                    if (sentenceMap==null) {
                    	File file = options.inFile.isFile()?options.inFile:new File(options.inFile, fName);
                    	logger.info("Opening "+file.getPath());
                    	reader = new InputStreamReader(file.getName().endsWith(".gz")?new GZIPInputStream(new FileInputStream(file)):new FileInputStream(file), "UTF-8");
                    	
                    	if (phraseParser==null && options.depInFile!=null) {
                    		File depFile = new File(options.depInFile, fName+".dep");
                    		if (depFile.exists()) {
                    			logger.info("Opening "+depFile.getPath());
                    			depReader = new BufferedReader(new InputStreamReader(new FileInputStream(depFile),"UTF-8"));
                    		}
                    	}	
                    } else
                    	sentences = sentenceMap.get(fName);
                    

                    String foutName=null;
                    PrintWriter writer=null;
                    if (options.outFile==null&&sentenceMap==null)
                        writer = new PrintWriter(System.out);
                    else
                    {
                        foutName = fName.endsWith(".gz")?fName.substring(0, fName.length()-3):fName;
                        foutName = foutName.replaceAll("\\.\\w+\\z", ".prop");

                        if (options.compressOutput) foutName=foutName+".gz";
                        
                        if (options.inFile==null)
                        	
                        foutName = options.inFile!=null&&options.inFile.isFile()?options.outFile.getPath():foutName;

                        File outFile = new File(options.inFile!=null&&options.inFile.isFile()?null:options.outFile, foutName);
                        if (outFile.getParentFile()!=null)
                            outFile.getParentFile().mkdirs();
                        writer = new PrintWriter(new OutputStreamWriter(options.compressOutput?new GZIPOutputStream(new FileOutputStream(outFile)):new FileOutputStream(outFile), "UTF-8"));
                        foutName = outFile.getPath();
                    }
                    options.processInput(reader, depReader, fName, writer, foutName, phraseParser, sentences, options.langUtil);
                    if (reader!=null) reader.close();
                    if (depReader!=null) depReader.close();
                    //writer.close();
                }
            }
            
            if (phraseParser!=null) phraseParser.close();
                
               /* 
            
            if (options.parsed)
            {
                Map<String, TBTree[]> parsedTreeBank = null;
                
                if (!props.getProperty("goldparse", "false").equals("false"))
                    parsedTreeBank = treeBank!=null?treeBank:TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("tb.regex"));
                else
                    parsedTreeBank = TBUtil.readTBDir(props.getProperty("parsedir"), props.getProperty("regex"));

                for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
                {
                    SortedMap<Integer, List<PBInstance>> pbFileMap = propBank==null?null:propBank.get(entry.getKey());
                    TBTree[] trees = entry.getValue();
                    
                    logger.info("Processing "+entry.getKey());
                    
                    boolean closeStream = false;
                    if (output==null && options.outFile.isDirectory())
                    {
                        String fName = entry.getKey();
                        if (fName.endsWith("parse")) fName = fName.substring(0, fName.length()-5)+"prop";
                        File propFile = new File(options.outFile, fName);
                        propFile.getParentFile().mkdirs();
                        closeStream = true;
                        output = new PrintStream(propFile);
                    }
                        
                    BlockingQueue<TBTree> queue = new ArrayBlockingQueue<TBTree>(100);
                    
                    //@TODO: figure out output file name
                    Thread srlThread = new Thread(options.new Runner(queue, null, pbFileMap));
                    srlThread.start();
                    
                    for (TBTree tree:trees) queue.put(tree);
                    queue.put(new TBTree(null, 0, null, 0, 0));
                    
                    srlThread.join();  

                    if (closeStream)
                    {
                        output.close();
                        output = null;
                    }
                }
            }
            else // not parsed
            {
                
            }
            */
        }       
        else if (dataFormat.equals("conll"))
        {
            /*
            ArrayList<CoNLLSentence> testing = CoNLLSentence.read(new FileReader(runSRLProps.getProperty("test.input")), false);
            
            for (CoNLLSentence sentence:testing)
            {
                TBUtil.linkHeads(sentence.parse, options.langUtil.getHeadRules());
                for (SRInstance instance:sentence.srls)
                {
                    ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                    ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();
                    SRLUtil.getSamplesFromParse(instance, sentence.parse, options.langUtil, THRESHOLD, argNodes, labels);
                    
                    SRInstance trainInstance = new SRInstance(instance.predicateNode, sentence.parse, instance.getRolesetId(), 1.0);
                    for (int i=0; i<labels.size(); ++i)
                    {
                        if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
                        trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
                    }
                }               
            }


            for (CoNLLSentence sentence:testing)
            {
                String[][] outStr = new String[sentence.parse.getTokenCount()][sentence.srls.length+1];
                for (int i=0; i<outStr.length; ++i)
                {
                    outStr[i][0] = "-";
                    for (int j=1; j<outStr[i].length; ++j)
                        outStr[i][j] = "*";
                }
                
                for (SRInstance instance:sentence.srls) {
                    Map<String, BitSet> argBitSet = new HashMap<String, BitSet>();
                    for (SRArg arg:instance.args)
                    {
                        if (arg.isPredicate()) continue;
                        BitSet bits = argBitSet.get(arg.label);
                        if (bits!=null)
                            bits.or(arg.getTokenSet());
                        else
                            argBitSet.put(arg.label, arg.getTokenSet());
                    }
                }
                
                List<SRInstance> predictions = options.model.predict(sentence.parse, sentence.srls, sentence.namedEntities);
                
                for (int j=1; j<=predictions.size(); ++j)
                //for (SRInstance instance:sentence.srls)
                {
                    SRInstance instance = predictions.get(j-1);
                    outStr[instance.predicateNode.getTokenIndex()][0] = instance.rolesetId;
                    
                    pCount += instance.getArgs().size()-1;
                    //pCount += instance.getArgs().size()-1;
                    //System.out.println(instance);
                    //System.out.println(pInstance+"\n");
                    //String a = instance.toString().trim();
                    //String b = pInstance.toString().trim();

                    Collections.sort(instance.args);
                    //Map<String, SRArg> labelMap= new HashMap<String, SRArg>();
                    for (SRArg arg:instance.args)
                    {
                        String label = arg.label;
                        if (label.equals("rel"))
                            label = "V";
                        else if (label.startsWith("ARG"))
                            label = "A"+label.substring(3);
                        else if (label.startsWith("C-ARG"))
                            label = "C-A"+label.substring(5);
                        else if (label.startsWith("R-ARG"))
                            label = "R-A"+label.substring(5);
                
                        BitSet bitset = arg.getTokenSet();
                        if (bitset.cardinality()==1)
                        {
                            outStr[bitset.nextSetBit(0)][j] = "("+label+"*)";
                        }
                        else
                        {
                            int start = bitset.nextSetBit(0);
                            outStr[start][j] = "("+label+"*";
                            outStr[bitset.nextClearBit(start+1)-1][j] = "*)";
                        }
                    }
                }
                for (int i=0; i<outStr.length; ++i)
                {
                    for (int j=0; j<outStr[i].length; ++j)
                        output.print(outStr[i][j]+"\t");
                    output.print("\n");
                }
                output.print("\n");
            }
*/
        }
        if (output!=null && output != System.out)
            output.close();
        /*
        else if (dataFormat.equals("conll"))
        {
            float p=0, r=0;
            int ptCnt=0,rtCnt=0;
            
            ArrayList<CoNLLSentence> testing = CoNLLSentence.read(new FileReader(props.getProperty("test.input")), false);
            model.initScore();
            for (CoNLLSentence sentence:testing)
            {
                TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
                List<SRInstance> predictions = model.predict(sentence.parse, sentence.srls, sentence.namedEntities);
                for (SRInstance prediction:predictions)
                {
                    for (SRInstance srl:sentence.srls)
                        if (prediction.predicateNode.tokenIndex==srl.predicateNode.tokenIndex)
                        {
                            ++p; ++r;
                        }
                }
                
                ptCnt += predictions.size();
                rtCnt += sentence.srls.length;
            }
            p /=ptCnt; r /= rtCnt;
            System.out.printf("predicate prediction: %f, %f, %f\n", p, r, 2*p*r/(p+r));
            
        }       
        */
        logger.info("Constituents predicted: "+pCount);
        
        //System.out.printf("%d/%d %.2f%%\n", count, y.length, count*100.0/y.length);
        
        //System.out.println(SRLUtil.getFMeasure(model.labelStringMap, testProb.y, y));
        
        if (options.executor!=null) {
	        options.executor.shutdown();
	        
	        while (true) {
	            try {
	                if (options.executor.awaitTermination(1, TimeUnit.MINUTES)) break;
	            } catch (InterruptedException e) {
	                logger.severe(e.getMessage());
	            }
	        }
        }
    }
}
