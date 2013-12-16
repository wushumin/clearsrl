package clearsrl;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.OntoNotesTokenizer;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBTokenizer;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.ParseException;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.FileUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.ParseCorpus;
import clearcommon.util.PropertyUtil;
import clearcommon.util.LanguageUtil.POS;
import clearsrl.SRInstance.OutputFormat;

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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
 
    @Option(name="-compressOutput",usage="Compress the SRL output")
    private boolean compressOutput = false; 
    
    @Option(name="-out",usage="output file/directory")
    private File outFile = null;
    
    @Option(name="-usePropBank",usage="use PropBank to find predicates")
    private boolean usePropBank = false; 
    
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

    class Runner implements Runnable
    {
        BlockingQueue<TBTree> treeQueue;
        PrintWriter writer;
        SortedMap<Integer, List<PBInstance>> pbFileMap;
        LanguageUtil langUtil;

        public Runner(BlockingQueue<TBTree> treeQueue, 
                PrintWriter writer, 
                SortedMap<Integer, List<PBInstance>> pbFileMap,
                LanguageUtil langUtil)
        {
            this.treeQueue = treeQueue;
            this.writer    = writer;
            this.pbFileMap = pbFileMap;
            this.langUtil  = langUtil;
        }
        
        public void run() {
            while (true)
            {
                TBTree tree;
                try {
                    tree = treeQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                if (tree.getRootNode()==null) break;
                logger.fine("Processing tree "+tree.getIndex());
                
                //System.out.println(i+" "+trees[i].getRootNode().toParse());
                //List<TBNode> nodes = SRLUtil.getArgumentCandidates(trees[i].getRootNode());
                //for (TBNode node:nodes)
                //    System.out.println(node.toParse());
                TBUtil.linkHeads(tree, langUtil.getHeadRules());

               
                List<SRInstance> predictions = null;
                
                if (pbFileMap==null) {
                    predictions = model.predict(tree, null, null);
                    
                    /*
                    BitSet goldPredicates = new BitSet();
                    for (SRInstance instance:goldInstances)
                        goldPredicates.set(instance.getPredicateNode().getTokenIndex());
                
                    BitSet predPredicates = new BitSet();
                    for (SRInstance instance:predictions)
                        predPredicates.set(instance.getPredicateNode().getTokenIndex());

                    if (!goldPredicates.equals(predPredicates))
                    {
                        System.out.print(entry.getKey()+","+i+": ");
                        List<TBNode> nodes = trees[i].getRootNode().getTokenNodes();
                        for (int j=0; j<nodes.size(); ++j)
                        {
                            if (goldPredicates.get(j)&& predPredicates.get(j))
                                System.out.print("["+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+"] ");
                            else if (goldPredicates.get(j))
                                System.out.print("("+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+") ");
                            else if (predPredicates.get(j))
                                System.out.print("{"+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+"} ");
                            else
                                System.out.print(nodes.get(j).getWord()+" ");
                        }
                        System.out.print("\n");
                    }*/
                } else  {
                    List<PBInstance> pbInstances = pbFileMap==null?null:pbFileMap.get(tree.getIndex());
                    if (pbInstances!=null) {
                        
                        predictions = new ArrayList<SRInstance>();
                        SRInstance[] goldInstances = new SRInstance[pbInstances.size()];
                        for (int j=0; j<goldInstances.length; ++j) {
                            goldInstances[j] = new SRInstance(pbInstances.get(j));
                            List<String> stem = langUtil.findStems(goldInstances[j].predicateNode);
                            predictions.add(new SRInstance(goldInstances[j].predicateNode, tree, stem.get(0)+".XX", 1.0));
                        }
                        if (goldInstances!=null && goldInstances.length!=0)
                            TBUtil.linkHeads(goldInstances[0].tree, langUtil.getHeadRules());
                        predictions = model.predict(tree, goldInstances, null);
                    }
                        
                        /*

                        ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                        ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();
                        SRLUtil.getSamplesFromParse(goldInstance, tree, langUtil, THRESHOLD, argNodes, labels);
                        
                        SRInstance trainInstance = new SRInstance(goldInstance.predicateNode, tree, goldInstance.getRolesetId(), 1.0);
                        for (int l=0; l<labels.size(); ++l)
                        {
                            if (SRLUtil.getMaxLabel(labels.get(l)).equals(SRLModel.NOT_ARG)) continue;
                            trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(l)), argNodes.get(l)));
                        } */
                }
                
                if (predictions!=null) {
                    if (outputFormat.equals(OutputFormat.CONLL))
                        writer.println(CoNLLSentence.toString(tree, predictions.toArray(new SRInstance[predictions.size()])));
                    else
                        for (SRInstance instance:predictions)
                            writer.println(instance.toString(outputFormat));
                }
                
            }
            logger.info("done");
        }
    }
    
    void processInput(Reader reader, String inName, PrintWriter writer, String outName, ParseCorpus parser, SortedMap<Integer, List<PBInstance>>  propBank, LanguageUtil langUtil) throws IOException, ParseException, InterruptedException
    {
        logger.info("Processing "+(inName==null?"stdin":inName)+", outputing to "+(outName==null?"stdout":outName));
        
        BlockingQueue<TBTree> queue = new ArrayBlockingQueue<TBTree>(100);
        
        Thread srlThread = new Thread(new Runner(queue, writer, propBank, langUtil));
        srlThread.start();
        
        Reader parseIn = null;
        PrintWriter parseOut = null;
        PipedWriter pipedWriter = null;
        if (parsed)
        {
            parseIn = reader;
        }
        else
        {
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
            queue.put(tree);            
            if (parseOut!=null)
                parseOut.println(tree.toString());
        }
        queue.put(new TBTree(null, Integer.MAX_VALUE, null, 0, 0));
        treeReader.close();
        
        parseIn.close();
        if (parseOut!=null) parseOut.close();
        if (pipedWriter!=null) pipedWriter.close();
        reader.close();
        
        srlThread.join();
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
        
        logger.info(PropertyUtil.toString(props));
        
        Properties runSRLProps = PropertyUtil.filterProperties(props, "srl.");
        runSRLProps = PropertyUtil.filterProperties(runSRLProps, "run.", true);
        
        if (options.outFile!=null) runSRLProps.setProperty("output.dir", options.outFile.getAbsolutePath());

        options.langUtil = (LanguageUtil) Class.forName(runSRLProps.getProperty("language.util-class")).newInstance();
        if (!options.langUtil.init(runSRLProps))
        {
            logger.severe(String.format("Language utility (%s) initialization failed",runSRLProps.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        logger.info("Loading model "+runSRLProps.getProperty("model_file"));
        
        ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(runSRLProps.getProperty("model_file"))));
        options.model = (SRLModel)mIn.readObject();
        mIn.close();
        
        logger.info("model loaded");
        
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
            if (!options.inFile.isDirectory())
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
        if (!dataFormat.equals("conll"))
        {   
            Map<String, SortedMap<Integer, List<PBInstance>>>  propBank = null;
            Map<String, TBTree[]> treeBank = null;
            if (options.usePropBank && runSRLProps.getProperty("pbdir")!=null)
            {
                String treeRegex = runSRLProps.getProperty("tb.regex");
                String propRegex = runSRLProps.getProperty("pb.regex");

                treeBank = TBUtil.readTBDir(runSRLProps.getProperty("tbdir"), treeRegex);
                //Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
                PBTokenizer tokenzier = runSRLProps.getProperty("pb.tokenizer")==null?(runSRLProps.getProperty("data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(runSRLProps.getProperty("pb.tokenizer")).newInstance();

                propBank = PBUtil.readPBDir(runSRLProps.getProperty("pbdir"), propRegex, 
                                     new TBReader(treeBank),tokenzier);
            }
            
            ParseCorpus phraseParser = null;
            
            if (!options.parsed)
            {
                phraseParser = new ParseCorpus();
                phraseParser.initialize(PropertyUtil.filterProperties(props, "parser."));
            }   
            if (options.inFile==null)
                options.processInput(new InputStreamReader(System.in), null, new PrintWriter(System.out), null, phraseParser, null, options.langUtil);
            else {
                List<String> fileList = options.inFileList==null?FileUtil.getFiles(options.inFile, runSRLProps.getProperty("regex",".*\\.(parse|txt)"))
                        :FileUtil.getFileList(options.inFile, options.inFileList);
                    
                for (String fName:fileList)
                {
                    File file = options.inFile.isFile()?options.inFile:new File(options.inFile, fName);
                    
                    Reader reader = new InputStreamReader(file.getName().endsWith(".gz")?new GZIPInputStream(new FileInputStream(file)):new FileInputStream(file), "UTF-8");

                    String foutName=null;
                    PrintWriter writer=null;
                    if (options.outFile==null)
                        writer = new PrintWriter(System.out);
                    else
                    {
                        foutName = fName.endsWith(".gz")?fName.substring(0, fName.length()-3):fName;
                        foutName = foutName.replaceAll("\\.(parse|txt)\\z", ".prop");

                        if (options.compressOutput) foutName=foutName+".gz";
                        
                        foutName = options.inFile.isFile()?options.outFile.getPath():foutName;

                        File outFile = new File(options.inFile.isFile()?null:options.outFile, foutName);
                        if (outFile.getParentFile()!=null)
                            outFile.getParentFile().mkdirs();
                        writer = new PrintWriter(new OutputStreamWriter(options.compressOutput?new GZIPOutputStream(new FileOutputStream(outFile)):new FileOutputStream(outFile), "UTF-8"));
                        foutName = outFile.getPath();
                    }
                    
                    options.processInput(reader, fName, writer, foutName, phraseParser, propBank==null?null:propBank.get(fName), options.langUtil);
                    reader.close();
                    writer.close();
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
    }
}
