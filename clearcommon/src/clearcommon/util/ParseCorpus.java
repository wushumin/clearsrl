package clearcommon.util;

import edu.berkeley.nlp.PCFGLA.CoarseToFineMaxRuleParser;
import edu.berkeley.nlp.PCFGLA.CoarseToFineNBestParser;
import edu.berkeley.nlp.PCFGLA.Corpus;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParseCorpus {
    
    ExecutorService executor;
    PriorityQueue<Sentence> parseQueue;
    Map<Long, CoarseToFineMaxRuleParser> parserMap;
    Properties props;
    
    public ParseCorpus(Properties props)
    {
        this.props = props;
        
        int threads = Integer.parseInt(props.getProperty("threads","1"));
        System.out.printf("Using %d threads\n",threads);
        
        executor = Executors.newFixedThreadPool(threads);
        parseQueue = new PriorityQueue<Sentence>();
        parserMap = new HashMap<Long, CoarseToFineMaxRuleParser>();
    }
    
    public void close()
    {
        executor.shutdown();
        
        while (true)
        {
            try {
                if (executor.awaitTermination(1, TimeUnit.MINUTES)) break;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
    }

    class Sentence implements Comparable<Sentence>, Runnable  {
        int linenum;
        String line;
        String parse;
        
        public Sentence(int linenum, String line){
            this.linenum = linenum;
            this.line = line;
        }
        @Override
        public int compareTo(Sentence arg0) {
            return linenum-arg0.linenum;
        }
        
        @Override
        public void run() {
            List<String> words = Arrays.asList(line.split(" "));
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
                //System.out.println(linenum+" "+line);
                parseQueue.add(this);
                parseQueue.notify();
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
                System.err.println("Failed to load grammar from file"+props.getProperty("grammar")+".");
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
	
	public void parse(InputStream in, OutputStream out) throws IOException, InterruptedException
	{
	    BufferedReader inputData = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        PrintWriter outputData = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), true);

        String line;
        int lineCount = 0;
        int lineNum = 0;
        while((line=inputData.readLine()) != null){
            executor.execute(new Sentence(lineCount++, line));                  
        }
        while (lineNum!=lineCount)
        {
            Sentence sentence;
            synchronized (parseQueue) {
                sentence = parseQueue.peek();
                if (sentence==null || sentence.linenum!=lineNum)
                {
                    parseQueue.wait();
                    continue;
                }
                sentence = parseQueue.remove();
            }
            outputData.write(sentence.parse);
            //System.out.println(lineNum+" "+sentence.parse);
            ++lineNum;
        }
        
        outputData.flush();
	}
	
	public static void main(String[] args) throws Exception
	{
	    Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();
		props = PropertyUtil.filterProperties(props, "parser.");
		
		int threads = Integer.parseInt(props.getProperty("threads","1"));
		System.out.printf("Using %d threads\n",threads);

		ParseCorpus parser = new ParseCorpus(props);
        
		String txtDir = props.getProperty("tbtxtdir");
	    String parseDir = props.getProperty("parsedir");
		if (props.getProperty("tbdir")!=null)
		{
		    Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("regex"));
		    TBUtil.extractText(txtDir, treeBank);
		}

		List<String> fileNames = FileUtil.getFiles(new File(txtDir), ".*\\..*");
		
		for (String fileName:fileNames)
		{
			File inputFile = new File(txtDir, fileName);
			File outputFile = new File(parseDir, fileName);
			
			outputFile.getParentFile().mkdirs();
			
			System.out.println("Parsing: "+fileName);
			FileInputStream inStream = new FileInputStream(inputFile);
			FileOutputStream outStream = new FileOutputStream(outputFile);
			
		    try{
		        parser.parse(inStream, outStream);
		    } catch (Exception ex) {
		    	ex.printStackTrace();
		    } finally {
		        inStream.close();
		        outStream.close();
		    }
		}

		parser.close();
        
	    System.exit(0);
	}
}
