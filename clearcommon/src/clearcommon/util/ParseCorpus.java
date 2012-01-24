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
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ParseCorpus {
    
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    ExecutorService executor;
    PriorityQueue<Sentence> parseQueue;
    Map<Long, CoarseToFineMaxRuleParser> parserMap;
    Properties props;
    PTBLineLexer tokenizer;
    
    public ParseCorpus(Properties props)
    {
        this.props = props;

        int threads = -1;

        String threadCnt = props.getProperty("threads","auto");

        if (threadCnt.equals("auto"))
            threads = Runtime.getRuntime().availableProcessors();
        else 
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
        parseQueue = new PriorityQueue<Sentence>();
        parserMap = new HashMap<Long, CoarseToFineMaxRuleParser>();
        
        if (props.getProperty("tokenize")!=null && !props.getProperty("tokenize").equals("false"))
            tokenizer = new PTBLineLexer();
    }
    
    public void close()
    {
        executor.shutdown();
        
        while (true)
        {
            try {
                if (executor.awaitTermination(1, TimeUnit.MINUTES)) break;
            } catch (InterruptedException e) {
                logger.severe(e.getMessage());
            }
        }
        
    }

    class Sentence implements Comparable<Sentence>, Runnable  {
        int linenum;
        List<String> words;
        String parse;
        
        public Sentence(int linenum, List<String> words){
            this.linenum = linenum;
            this.words = words;
        }
        @Override
        public int compareTo(Sentence arg0) {
            return linenum-arg0.linenum;
        }
        
        @Override
        public void run() {
            if (words.isEmpty())
                parse=makeDefaultParse(words);
            else if (words.size()>=250) { 
                parse=makeDefaultParse(words);
                System.err.println("Skipping sentence with "+words.size()+" words since it is too long.");
            }
            else {
                Tree<String> parsedTree = null;
        		try {
        		    parsedTree = getParser().getBestConstrainedParse(words,null,null);
        		} catch (Exception e) {
        		    logger.severe(e.toString());
        		    e.printStackTrace();
        		} finally {
        		    if (parsedTree!=null&&!parsedTree.getChildren().isEmpty())
        		    {
        			removeUselessNodes(parsedTree.getChildren().get(0));
        			parse="( "+parsedTree.getChildren().get(0)+" )\n";
        		    }
        		    else
        			parse=makeDefaultParse(words);
        		}
            }
            synchronized (parseQueue)
            {
                parseQueue.add(this);
                parseQueue.notify();
            }
        }
    }
    
    public class SentenceWriter extends Thread {
        Writer outputData;
        int lineCount;
        
        public SentenceWriter(Writer outputData, int lineCount) {
            this.outputData = outputData;
            this.lineCount = lineCount;
        }

        public void run() {
            int lineNum = 0;
            while (lineNum!=lineCount)
            {
                Sentence sentence;
                synchronized (parseQueue) {
                    sentence = parseQueue.peek();
                    if (sentence==null || sentence.linenum!=lineNum)
                    {
                        try {
                            parseQueue.wait();
                        } catch (InterruptedException e) {
                            logger.severe(e.toString());
                        }
                        continue;
                    }
                    sentence = parseQueue.remove();
                }
                try {
                    outputData.write(sentence.parse);outputData.flush();
                    //System.out.println(lineNum+" "+sentence.parse);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ++lineNum;
            }
        }
    }

    CoarseToFineMaxRuleParser getParser()
    {
        CoarseToFineMaxRuleParser parser;
        if ((parser=parserMap.get(Thread.currentThread().getId()))==null)
        {
            double threshold = 1.0;
            ParserData pData = ParserData.Load(props.getProperty("grammar"));
            if (pData==null) {
                logger.severe("Failed to load grammar from file"+props.getProperty("grammar")+".");
                System.exit(1);
            }
            Grammar grammar = pData.getGrammar();
            Lexicon lexicon = pData.getLexicon();
            Numberer.setNumberers(pData.getNumbs());
        
            if (props.getProperty("Chinese")!=null && !props.getProperty("Chinese").equals("false")) 
            {
                System.out.println("Chinese parsing features enabled.");
                Corpus.myTreebank = Corpus.TreeBankType.CHINESE;
            }
            parser = new CoarseToFineNBestParser(grammar, lexicon, 1,threshold,-1, false, false, false, false, false, false, true);;
            parser.binarization = pData.getBinarization();
            parserMap.put(Thread.currentThread().getId(), parser);
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

	static void removeUselessNodes(Tree<String> node)
	{
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
	
	public static String makeDefaultParse(List<String> sentence)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append("( (FRAG ");
		for (String word:sentence)
			buffer.append("(X "+word+") ");
		buffer.append(") )\n");
		return buffer.toString();
	}
	
	public SentenceWriter parse(Reader reader, Writer writer) throws IOException
	{
	    BufferedReader inputData = new BufferedReader(reader);

        String line;
        int lineCount = 0;

        while((line=inputData.readLine()) != null){
            List<String> words = tokenizer==null?Arrays.asList(line.split(" ")):tokenizer.tokenizeLine(line);
            executor.execute(new Sentence(lineCount++, words));
            //System.out.println(lineCount);
        }
        
        SentenceWriter sentWriter = new SentenceWriter(writer, lineCount);
        sentWriter.start();
        
        return sentWriter;

	}
	
	public static void main(String[] args) throws Exception
	{
	    Properties props = new Properties();
		Reader in = new InputStreamReader(new FileInputStream(args[0]), "UTF-8");
		props.load(in);
		in.close();
		props = PropertyUtil.filterProperties(props, "parser.");

		ParseCorpus parser = new ParseCorpus(props);
        
		String txtDir = props.getProperty("tbtxtdir");
	    String parseDir = props.getProperty("parsedir");
		if (props.getProperty("tbdir")!=null)
		{
		    Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("regex"));
		    TBUtil.extractText(txtDir, treeBank);
		}
		
		List<String> fileNames = FileUtil.getFiles(new File(txtDir), props.getProperty("txtregex", "[^\\.].*"));
		
		for (String fileName:fileNames)
		{
			File inputFile = new File(txtDir, fileName);
			File outputFile = new File(parseDir, fileName);
			
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
