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
import java.io.InputStreamReader;
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

public class ParseCorpus {
    public ParseCorpus()
    {
        
    }
    
    public static PriorityQueue<Sentence> parseQueue = new PriorityQueue<Sentence>();

    public class Sentence implements Comparable<Sentence>, Runnable  {
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
                Tree<String> parsedTree = getParser().getBestConstrainedParse(words,null,null);
    
                if (parsedTree!=null&&!parsedTree.getChildren().isEmpty())
                {
                    removeUselessNodes(parsedTree.getChildren().get(0));
                    parse="( "+parsedTree.getChildren().get(0)+" )\n";
                }
                else
                    parse=makeDefaultParse(words);
            }
            synchronized (parseQueue)
            {
                //System.out.println(linenum+" "+line);
                parseQueue.add(this);
                parseQueue.notify();
            }
        }
    }
    
    public static Map<Long, CoarseToFineMaxRuleParser> parserMap = new HashMap<Long, CoarseToFineMaxRuleParser>();
    
    public static Properties props;
    public static CoarseToFineMaxRuleParser getParser()
    {
        CoarseToFineMaxRuleParser parser;
        if ((parser=parserMap.get(Thread.currentThread().getId()))==null)
        {
            double threshold = 1.0;
            ParserData pData = ParserData.Load(props.getProperty("parser.grammar"));
            if (pData==null) {
                System.out.println("Failed to load grammar from file"+props.getProperty("parser.grammar")+".");
                System.exit(1);
            }
            Grammar grammar = pData.getGrammar();
            Lexicon lexicon = pData.getLexicon();
            Numberer.setNumberers(pData.getNumbs());
        
            if (props.getProperty("parser.Chinese")!=null) 
            {
                System.err.println("Chinese parsing features enabled.");
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
	
	public static void main(String[] args) throws Exception
	{
	    ParseCorpus parseCorpus = new ParseCorpus();
		props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		
		ExecutorService executor = Executors.newFixedThreadPool(Integer.parseInt(props.getProperty("parser.threads","1")));
        
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
		    try{
		    	BufferedReader inputData = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"));
		    	PrintWriter outputData = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"), true);
	
		    	String line;
		    	int lineCount = 0;
		    	int lineNum = 0;
		    	while((line=inputData.readLine()) != null){
		    	    executor.execute(parseCorpus.new Sentence(lineCount++, line));		    	    
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
		    	outputData.close();
		    } catch (Exception ex) {
		    	ex.printStackTrace();
		    }
		}
        executor.shutdown();
	    System.exit(0);
	}
}
