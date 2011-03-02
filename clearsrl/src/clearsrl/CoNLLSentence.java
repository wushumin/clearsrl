package clearsrl;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.ParseException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.StringTokenizer;

public class CoNLLSentence {
	
	TBTree       parse;
	String[]     namedEntities;
	SRInstance[] srls;
	
	public String toString()
	{
		StringBuilder buffer = new StringBuilder();
		
		buffer.append(parse.getRootNode().toParse());
		buffer.append('\n');
		
		for (SRInstance srl:srls)
		{
			buffer.append(srl);
			buffer.append('\n');
		}
		for (String s:namedEntities)
		{
			if (s==null)
				buffer.append('-');
			else
				buffer.append(s);
			buffer.append(' ');
		}
		buffer.append('\n');
		
		return buffer.toString();
	}
	
	static CoNLLSentence buildSentence(ArrayList<String[]> sentenceTokens) throws ParseException
	{
		CoNLLSentence sentence = new CoNLLSentence();
		
		sentence.namedEntities = new String[sentenceTokens.size()];
		
		StringBuilder parseStr = new StringBuilder();
		
		for (String[] tokens:sentenceTokens)
		{
			tokens[0] = tokens[0].replace("(", "-LRB-");
			tokens[0] = tokens[0].replace(")", "-RRB-");
			tokens[1] = tokens[1].replace("(", "-LRB-");
			tokens[1] = tokens[1].replace(")", "-RRB-");
			parseStr.append(tokens[2].replace("*", '('+tokens[1]+' '+tokens[0]+')'));
		}
		
		//if (!parseStr.toString().startsWith("(S1(S")) System.out.println(parseStr);
		
		TBFileReader tbReader = new TBFileReader(new StringReader(parseStr.toString()));
		
		sentence.parse = tbReader.nextTree();
	
		//System.out.println(parseStr);
		//System.out.println(sentence.parse.getRootNode().toParse());
		
		sentence.srls = new SRInstance[sentenceTokens.get(0).length-6];
		
		int srlIdx = 0;
		boolean neCont = false;
		for (int i=0; i<sentenceTokens.size(); ++i)
		{
			String [] tokens = sentenceTokens.get(i);
			if (neCont)
				sentence.namedEntities[i] = sentence.namedEntities[i-1];
			if (tokens[3].startsWith("("))
			{
				sentence.namedEntities[i] = tokens[3].substring(1,tokens[3].indexOf('*'));
				neCont = true;
			}
			if (tokens[3].endsWith(")"))
				neCont = false;

			if (!tokens[5].equals("-"))
			{
				//if (!training) System.out.println(sentence.parse);
				TBNode predicateNode = sentence.parse.getRootNode().getTokenNodes().get(i);
				sentence.srls[srlIdx] = new SRInstance(predicateNode, sentence.parse);
				if (!tokens[5].equals(tokens[4]))
					sentence.srls[srlIdx].setRolesetId(tokens[5]+'.'+tokens[4]);
				else
					sentence.srls[srlIdx].setRolesetId(tokens[5]);
				++srlIdx;
			}
		}
		BitSet [] args = new BitSet[sentence.srls.length];
		String [] labels = new String[sentence.srls.length];
		for (int i=0; i<sentenceTokens.size(); ++i)
		{
			String [] tokens = sentenceTokens.get(i);
			for (int j=0; j<sentence.srls.length; ++j)
			{
				if (tokens[j+6].startsWith("("))
				{
					args[j] = new BitSet(sentence.parse.getTokenCount());
					labels[j] = tokens[j+6].substring(1,tokens[j+6].indexOf('*'));
				}
				if (args[j]!=null) args[j].set(i);
				if (tokens[j+6].endsWith(")"))
				{
					if (labels[j].equals("V")) labels[j] = "rel";
					else if (labels[j].equals("C-V")) labels[j] = "C-rel";
					else if (labels[j].startsWith("R-A")) labels[j] = "R-ARG"+labels[j].substring(3);
					//else if (labels[j].startsWith("R-A")) labels[j] = "ARG"+labels[j].substring(3);
					//else if (labels[j].startsWith("C-A")) labels[j] = "C-ARG"+labels[j].substring(3);
					else if (labels[j].startsWith("C-A")) labels[j] = "C-ARG"+labels[j].substring(3);
					else labels[j] = "ARG"+labels[j].substring(1);
					sentence.srls[j].addArg(new SRArg(labels[j], args[j]));
					args[j]=null;
				}
			}
		}
		
		//for (SRInstance instance:sentence.srls)
		//	System.out.println(instance);
		return sentence;
	}
	
	static ArrayList<CoNLLSentence> read(Reader in, boolean training) throws IOException
	{
		ArrayList<CoNLLSentence> sentences = new ArrayList<CoNLLSentence>();
		
		ArrayList<String[]> sentenceTokens = new ArrayList<String[]>();
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine())!=null)
		{
			StringTokenizer tokenizer = new StringTokenizer(line);
			String [] tokens = new String[tokenizer.countTokens()];
			for (int i=0; i<tokens.length; ++i)
				tokens[i] = tokenizer.nextToken();
			
			if (tokens.length==0)
			{
				if (!sentenceTokens.isEmpty())
				{
					try {
						sentences.add(buildSentence(sentenceTokens));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					sentenceTokens.clear();
				}
			}
			else
				sentenceTokens.add(tokens);
		}
		
		if (!sentenceTokens.isEmpty())
		{
			try {
				sentences.add(buildSentence(sentenceTokens));
			} catch (ParseException e) {
				e.printStackTrace();
			}
			sentenceTokens.clear();
		}
		
		return sentences;
	}
	
	public static void main(String[] args) throws Exception
	{
		ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(args[0]), true);
		System.out.printf("Read %d training sentences\n",training.size());
		ArrayList<CoNLLSentence> testing = CoNLLSentence.read(new FileReader(args[1]), false);
		System.out.printf("Read %d testing sentences\n",testing.size());
	}
}
