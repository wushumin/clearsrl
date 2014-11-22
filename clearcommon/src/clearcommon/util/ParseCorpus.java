package clearcommon.util;

import edu.berkeley.nlp.PCFGLA.CoarseToFineMaxRuleParser;
import edu.berkeley.nlp.PCFGLA.CoarseToFineNBestParser;
import edu.berkeley.nlp.PCFGLA.Corpus;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.io.PTBLineLexer;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;
import clearcommon.propbank.PBFileReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * @author shumin
 *
 */
public class ParseCorpus {
    
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
    
    @Option(name="-in",usage="input file/directory")
    private String treeDir = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private String inFileList = null; 
    
    @Option(name="-txtdir",usage="list of files in the input directory to process (overwrites regex)")
    private String txtDir = null; 
    
    @Option(name="-conll", usage="use conll POS format")
    private boolean isCoNLL = false;
    
    @Option(name="-model",usage="list of files in the input directory to process (overwrites regex)")
    private String grammar = null; 
    
    @Option(name="-out",usage="output file/directory")
    private String outDir = null;
    
    @Option(name="-lower",usage="convert to lowercase")
    private boolean toLowerCase = false; 
    
    @Option(name="-overWrite",usage="overwrites output")
    private boolean overWrite = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    ExecutorService executor;
    ArrayBlockingQueue<Future<String>> parseQueue;
    Map<Long, CoarseToFineMaxRuleParser> parserMap;
    Properties props;
    PTBLineLexer tokenizer;
    
    boolean flushOutput;
    
    /**
     * @param flushOutput whether to immediately flush the writer (used if the output is connected to a pipe for another stage of processing)
     */
    public ParseCorpus(boolean flushOutput) {
    	this.flushOutput = flushOutput;
    }
    
    public void initialize(Properties props)
    {
        this.props = props;

        int threads = -1;

        String threadCnt = props.getProperty("threads","auto");

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
        
        executor = Executors.newFixedThreadPool(threads);
        parseQueue = new ArrayBlockingQueue<Future<String>>(threads*25);
        parserMap = new HashMap<Long, CoarseToFineMaxRuleParser>();
        
        if (props.getProperty("tokenize")!=null && !props.getProperty("tokenize").equals("false"))
            tokenizer = new PTBLineLexer();
    }
    
    public void close() {
        executor.shutdown();
        
        while (true) {
            try {
                if (executor.awaitTermination(1, TimeUnit.MINUTES)) break;
            } catch (InterruptedException e) {
                logger.severe(e.getMessage());
            }
        }
        
    }

    class Sentence implements Callable<String>  {
        List<String> words;
        List<String> poses;
        
        public Sentence(List<String> words) {
        	this(words, null);
        }
        
        public Sentence(List<String> words, List<String> poses){
            this.words = words;
            this.poses = poses;
        }
        
        @Override
        public String call() {
        	if (words==null) return null;
            if (words.isEmpty())
                return makeDefaultParse(words);
            if (words.size()>=250) { 
                logger.warning("Skipping sentence with "+words.size()+" words since it is too long.");
                return makeDefaultParse(words);
            }
            String parse = null;
            Tree<String> parsedTree = null;
            try {
            	//System.out.println(words);
            	//System.out.println(poses);
                parsedTree = getParser().getBestConstrainedParse(words,poses,null);
            } catch (Exception e) {
                logger.severe(e.toString());
                e.printStackTrace();
            } finally {
                if (parsedTree!=null&&!parsedTree.getChildren().isEmpty()) {
                	removeUselessNodes(parsedTree);
                	/*removeUselessNodes(parsedTree.getChildren().get(0));
                	parse = "( "+parsedTree.getChildren().get(0)+" )\n";*/
                	// Can't just use the first child as multiple "S" in a tree is allowed in Treebank
                	StringBuilder builder = new StringBuilder("( ");
                	for (Tree<String> child:parsedTree.getChildren())
                		builder.append(child+" ");
                	builder.append(")\n");
                	parse = builder.toString();
                	
                }
                else
                	parse = makeDefaultParse(words);
            }
            return parse;
        }
    }
    
    public class SentenceWriter extends Thread {
        Writer outputData;
        
        public SentenceWriter(Writer outputData) {
            this.outputData = outputData;
        }

        public void run() {
            while (true) {
            	Future<String> future;
                try {
	                future = parseQueue.take();
                } catch (InterruptedException e) {
	                continue;
                }
            	
            	String parse=null;
            	while (true)
	                try {
		                parse = future.get();
		                break;
	                } catch (InterruptedException e) {
	                	continue;
	                } catch (ExecutionException e) {
		                e.printStackTrace();
		                break;
	                }

            	if (parse==null) break;
            	
            	try {
                    outputData.write(parse);
                    if (flushOutput)
                    	outputData.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

     CoarseToFineMaxRuleParser getParser() {
        CoarseToFineMaxRuleParser parser;
        if ((parser=parserMap.get(Thread.currentThread().getId()))==null) {
            double threshold = 1.0;
            ParserData pData = ParserData.Load(props.getProperty("grammar"));
            if (pData==null) {
                logger.severe("Failed to load grammar from file"+props.getProperty("grammar")+".");
                System.exit(1);
            }
            Grammar grammar = pData.getGrammar();
            Lexicon lexicon = pData.getLexicon();
            Numberer.setNumberers(pData.getNumbs());
        
            if (props.getProperty("Chinese")!=null && !props.getProperty("Chinese").equals("false"))  {
                logger.info("Chinese parsing features enabled.");
                Corpus.myTreebank = Corpus.TreeBankType.CHINESE;
            }
            parser = new CoarseToFineNBestParser(grammar, lexicon, 1,threshold,-1, false, false, false, false, false, false, true);;
            parser.binarization = pData.getBinarization();
            synchronized (parserMap) {
            	parserMap.put(Thread.currentThread().getId(), parser);
            }
        }
        return parser;
    }

    static void outputTrees(List<Tree<String>> parseTrees, PrintWriter outputData, 
            CoarseToFineMaxRuleParser parser) {
        for (Tree<String> parsedTree : parseTrees){
            if (!parsedTree.getChildren().isEmpty())
                outputData.write("( "+parsedTree.getChildren().get(0)+" )\n");
            else
                outputData.write("(())\n");
        }
    }

    static void removeUselessNodes(Tree<String> node) {
        if (node.isLeaf()) return;
        ArrayList<Tree<String>> newChildren = new ArrayList<Tree<String>>();
        for (Tree<String> child:node.getChildren())
        {
            removeUselessNodes(child);
            if (!child.isLeaf() && child.getLabel().startsWith("@"))
                newChildren.addAll(child.getChildren());
            else
                newChildren.add(child);
        }
        node.setChildren(newChildren); 
    }
    
    public static String makeDefaultParse(List<String> sentence) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("( (FRAG ");
        for (String word:sentence)
            buffer.append("(X "+word+") ");
        buffer.append(") )\n");
        return buffer.toString();
    }
    
    public SentenceWriter parse(Reader reader, Writer writer) throws IOException {
        BufferedReader inputData = new BufferedReader(reader);

        SentenceWriter sentWriter = new SentenceWriter(writer);
        sentWriter.start();
        
        String line;
        
        if (!isCoNLL)
	        while((line=inputData.readLine()) != null){
	        	if (toLowerCase)
	        		line = line.toLowerCase();
	            List<String> words = tokenizer==null?Arrays.asList(line.trim().split(" +")):tokenizer.tokenizeLine(line);
	            Future<String> future = executor.submit(new Sentence(words));
	            while(true)
	    	        try {
	    	        	parseQueue.put(future);
	    	        	break;
	    	        } catch (InterruptedException e) {
	    	        }
	            //System.out.println(lineCount);
	        }
        else {
        	ArrayList<String> words=new ArrayList<String>();
        	ArrayList<String> poses=new ArrayList<String>();
        	while((line=inputData.readLine()) != null){
        		line = line.trim();
        		if (line.isEmpty()) {
        			if (words.isEmpty()) continue;
        			words.trimToSize();
        			poses.trimToSize();
        			Future<String> future = executor.submit(new Sentence(words, poses));
    	            while(true)
    	    	        try {
    	    	        	parseQueue.put(future);
    	    	        	break;
    	    	        } catch (InterruptedException e) {
    	    	        }
    	            words = new ArrayList<String>();
    	            poses = new ArrayList<String>();
    	            continue;
        		}
        		String[] tokens = line.split(" ");
        		words.add(toLowerCase?tokens[0].toLowerCase():tokens[0]);
        		poses.add(tokens[1]);
	        }
        }
        
        
        
        // add a dummy termination sentence
        Future<String> future = executor.submit(new Sentence(null));
        while(true)
	        try {
	        	parseQueue.put(future);
		        break;
	        } catch (InterruptedException e) {
	        }
	  
        return sentWriter;
    }
    
    public static void main(String[] args) throws Exception {
        ParseCorpus parser = new ParseCorpus(false);
        CmdLineParser cmdParser = new CmdLineParser(parser);
        
        try {
            cmdParser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("invalid options:"+e);
            cmdParser.printUsage(System.err);
            System.exit(0);
        }
        if (parser.help){
            cmdParser.printUsage(System.err);
            System.exit(0);
        }

        Properties props = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(parser.propFile), "UTF-8");
        props.load(in);
        props = PropertyUtil.resolveEnvironmentVariables(props);
        in.close();
        props = PropertyUtil.filterProperties(props, "parser.");

        if (parser.treeDir!=null) props.setProperty("tbdir", parser.treeDir);
        if (parser.txtDir!=null) props.setProperty("tbtxtdir", parser.txtDir);
        if (parser.inFileList!=null) props.setProperty("filelist", parser.inFileList);
        if (parser.outDir!=null) props.setProperty("parsedir", parser.outDir);
        if (parser.grammar!=null) props.setProperty("grammar",parser.grammar);
        
        parser.initialize(props);
        
        String txtDir = props.getProperty("tbtxtdir");
        String parseDir = props.getProperty("parsedir");
        
        List<String> fileNames = null;
        
        if (props.getProperty("tbdir")!=null) {
            String fileList = props.getProperty("filelist");
            
            if (fileList != null)
                fileNames = FileUtil.getFileList(new File(props.getProperty("tbdir")), new File(fileList));
            Map<String, TBTree[]> treeBank = fileList==null?
                    TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("regex")):
                    TBUtil.readTBDir(props.getProperty("tbdir"), fileNames);
            TBUtil.extractText(txtDir, treeBank, parser.isCoNLL);
        }
        
        if (fileNames==null) {
            fileNames = FileUtil.getFiles(new File(txtDir), props.getProperty("txtregex", "[^\\.].*"));
            logger.info("parsing "+fileNames.size()+" files");
        }
        
        for (String fileName:fileNames) {
            File inputFile = new File(txtDir, fileName);
            if (!inputFile.exists()) {
                logger.severe(inputFile.getAbsolutePath()+" does not exist");
                continue;
            }
            
            File outputFile = new File(parseDir, fileName);
            if (!parser.overWrite && outputFile.exists())
            	continue;
            
            outputFile.getParentFile().mkdirs();
            
            System.out.println("Parsing: "+fileName);
            Reader reader = new InputStreamReader(new FileInputStream(inputFile), "UTF-8");
            Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
            
            try{
                SentenceWriter sWriter = parser.parse(reader, writer);
                sWriter.join();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                reader.close();
                writer.close();
            }
        }

        parser.close();
        
        System.exit(0);
    }
}
