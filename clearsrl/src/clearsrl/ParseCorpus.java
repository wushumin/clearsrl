package clearsrl;

import edu.berkeley.nlp.PCFGLA.BerkeleyParser;
import edu.berkeley.nlp.PCFGLA.Binarization;
import edu.berkeley.nlp.PCFGLA.CoarseToFineMaxRuleParser;
import edu.berkeley.nlp.PCFGLA.CoarseToFineNBestParser;
import edu.berkeley.nlp.PCFGLA.Corpus;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.BerkeleyParser.Options;
import edu.berkeley.nlp.io.PTBLineLexer;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;
import gnu.trove.TIntObjectHashMap;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
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
import java.util.Map;
import java.util.Properties;

public class ParseCorpus {
	
	public static CoarseToFineMaxRuleParser initParser(Properties props)
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
	    	Corpus.myTreebank = Corpus.TreeBankType.CHINESE;
    
	    CoarseToFineMaxRuleParser parser = new CoarseToFineNBestParser(grammar, lexicon, 1,threshold,-1, false, false, false, false, false, false, true);;
	    parser.binarization = pData.getBinarization();
	    
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
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();
		
		CoarseToFineMaxRuleParser parser = initParser(props);
		
		Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("regex"));
		TBUtil.extractText(props.getProperty("tbtxtdir"), treeBank);
		
		for (Map.Entry<String, TBTree[]> entry:treeBank.entrySet()) 
		{
			File inputFile = new File(props.getProperty("tbtxtdir"), entry.getKey());
			File outputFile = new File(props.getProperty("parsedir"), entry.getKey());
			
			outputFile.getParentFile().mkdirs();
			
			System.out.println("Parsing: "+entry.getKey());
		    try{
		    	BufferedReader inputData = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"));
		    	PrintWriter outputData = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"), true);
	
		    	String line = "";
		    	while((line=inputData.readLine()) != null){
		    		List<String> sentence = Arrays.asList(line.split(" "));
		    		if (sentence.isEmpty())
		    		{
		    		    outputData.write(makeDefaultParse(sentence));
		    		    continue;
		    		}
		    		if (sentence.size()>=250) { 
		    			outputData.write(makeDefaultParse(sentence));
		    			System.err.println("Skipping sentence with "+sentence.size()+" words since it is too long.");
		    			continue; 
		    		}
		    		    
					Tree<String> parsedTree = parser.getBestConstrainedParse(sentence,null,null);
	
					if (parsedTree!=null&&!parsedTree.getChildren().isEmpty())
					{
			        	removeUselessNodes(parsedTree.getChildren().get(0));
						outputData.write("( "+parsedTree.getChildren().get(0)+" )\n");
					}
					else
						outputData.write(makeDefaultParse(sentence));
		    	}
		    	outputData.flush();
		    	outputData.close();
		    } catch (Exception ex) {
		    	ex.printStackTrace();
		    }
		}		
	    System.exit(0);
	}
}
