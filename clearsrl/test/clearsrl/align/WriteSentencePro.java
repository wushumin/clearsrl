package clearsrl.align;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Properties;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.ChineseUtil;
import clearcommon.util.ECDependent;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

public class WriteSentencePro {
    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
    
    @Option(name="-t",usage="types of printing")
    private Type type = Type.TERMINAL;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    enum Type {
    	PRO,
    	DEPPRO,
    	TOKEN,
    	TERMINAL,
    }
    
	static void printProLine(Type type, Sentence sentence, LanguageUtil langUtil, PrintStream textOut, PrintStream terminalOut) {
		switch(type) {
		case TERMINAL:
			for (TBNode node:sentence.terminals)
				textOut.print(node.getECType()+" ");
			for (long index:sentence.terminalIndices)
				terminalOut.printf("%d-%d ", Sentence.getTreeIndex(index), Sentence.getTerminalIndex(index));
			break;
		case TOKEN:
			for (TBNode node:sentence.tokens)
				textOut.print(node.getWord()+" ");
			for (long index:sentence.indices)
				terminalOut.printf("%d-%d ", Sentence.getTreeIndex(index), Sentence.getTerminalIndex(index));
			break;
		case PRO:
			for (int i=0; i<sentence.terminals.length; ++i) {
				if (sentence.terminals[i].isToken()) {
					textOut.print(sentence.terminals[i].getWord()+" ");
					terminalOut.printf("%d-%d ", Sentence.getTreeIndex(sentence.terminalIndices[i]), Sentence.getTerminalIndex(sentence.terminalIndices[i]));
				} else if (sentence.terminals[i].getECType().toLowerCase().equals("*pro*")) {
					textOut.print(sentence.terminals[i].getECType()+" ");
					terminalOut.printf("%d-%d ", Sentence.getTreeIndex(sentence.terminalIndices[i]), Sentence.getTerminalIndex(sentence.terminalIndices[i]));
				}
			}
			break;
		case DEPPRO:
			TBNode[] sbjDep = new TBNode[sentence.tokens.length];
			TBNode[] objDep = new TBNode[sentence.tokens.length];
			long[] sbjIdx = new long[sentence.tokens.length];
			long[] objIdx = new long[sentence.tokens.length];
			
			int idx;
			for (TBTree tree:sentence.treeMap.values()) {
				TBUtil.linkHeads(tree, langUtil.getHeadRules());
				for (ECDependent dep:ECDependent.getDependents(tree)) {
					if ((idx=Arrays.binarySearch(sentence.indices, Sentence.makeIndex(tree.getIndex(), dep.getPredicate().getTerminalIndex())))<0) continue;
					
					if (sbjDep[idx]==null && dep.getSubject()!=null && 
							Arrays.binarySearch(sentence.terminalIndices, sbjIdx[idx]=Sentence.makeIndex(tree.getIndex(), dep.getSubject().getTerminalIndex()))>=0) 
						sbjDep[idx] = dep.getSubject();
					
					if (objDep[idx]==null && dep.getObject()!=null && 
							Arrays.binarySearch(sentence.terminalIndices, objIdx[idx]=Sentence.makeIndex(tree.getIndex(), dep.getObject().getTerminalIndex()))>=0)
						objDep[idx] = dep.getObject();
				}
			}
			
			for (int i=0; i<sentence.tokens.length; ++i) {
				if (sbjDep[i]!=null && sbjDep[i].getECType().toLowerCase().equals("*pro*")) {
					textOut.print("sbj-"+sbjDep[i].getECType()+" ");
					terminalOut.printf("%d-%d ", Sentence.getTreeIndex(sbjIdx[i]), Sentence.getTerminalIndex(sbjIdx[i]));
				}
				textOut.print(sentence.tokens[i].getWord()+" ");
				terminalOut.printf("%d-%d ", Sentence.getTreeIndex(sentence.indices[i]), Sentence.getTerminalIndex(sentence.indices[i]));
				if (objDep[i]!=null && objDep[i].getECType().toLowerCase().equals("*pro*")) {
					textOut.print("obj-"+objDep[i].getECType()+" ");
					terminalOut.printf("%d-%d ", Sentence.getTreeIndex(objIdx[i]), Sentence.getTerminalIndex(objIdx[i]));
				}
			}
		}
		textOut.print("\n");
		terminalOut.print("\n");
	}
	
	public static void main(String[] args) throws IOException{
		WriteSentencePro options = new WriteSentencePro();
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
		
		System.err.print(PropertyUtil.toString(props));
		 
		ChineseUtil chLangUtil = null;
		if (options.type==Type.DEPPRO) {
			chLangUtil = new ChineseUtil();
			if (!chLangUtil.init(PropertyUtil.filterProperties(props, "chinese.", true)))
				System.exit(-1);
		}
		
		Properties srcProps = PropertyUtil.filterProperties(props,"src.", true);
		Properties dstProps = PropertyUtil.filterProperties(props,"dst.", true);
		
		SentenceReader srcReader = new TokenedSentenceReader(srcProps);
		SentenceReader dstReader = new TokenedSentenceReader(dstProps);
		
		srcReader.initialize();
		dstReader.initialize();
		
		Sentence src;
		Sentence dst;
		
		String prefix = options.type.toString().toLowerCase()+'.';
		
		PrintStream srcOut = new PrintStream(srcProps.getProperty(prefix+"text"));
		PrintStream dstOut = new PrintStream(dstProps.getProperty(prefix+"text"));
		
		PrintStream srcTerminalOut = new PrintStream(srcProps.getProperty(prefix+"terminal"));
		PrintStream dstTerminalOut = new PrintStream(dstProps.getProperty(prefix+"terminal"));

		
		while ((src=srcReader.nextSentence())!=null && (dst=dstReader.nextSentence())!=null)
		{
			printProLine(options.type, src, chLangUtil, srcOut, srcTerminalOut);
			printProLine(options.type.equals(Type.DEPPRO)?Type.PRO:options.type, dst, null, dstOut, dstTerminalOut);
		}
		
		srcReader.close();
		dstReader.close();
		srcOut.close();
		dstOut.close();
		srcTerminalOut.close();
		dstTerminalOut.close();
		
	}
}
