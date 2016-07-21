package edu.colorado.clear.srl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.StringTokenizer;

import edu.colorado.clear.common.treebank.ParseException;
import edu.colorado.clear.common.treebank.SerialTBFileReader;
import edu.colorado.clear.common.treebank.TBFileReader;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBTree;

public class CoNLLSentence {
    
	/*
	 * Columns :
	 * 1    Document ID
     * 2    Part number
     * 3    Word number
     * 4    Word itself   
     * 5    Part-of-Speech
     * 6    Parse bit
     * 7    Predicate lemma
     * 8    Predicate Frameset ID
     * 9    Word sense
     * 10   Speaker/Author 
     * 11   Named Entities 
     * 12:N Predicate Arguments
     * N    Coreference
	 * 
	 */
	
	final static int SRL_IDX = 11; 
	
    TBTree       parse;
    String[]     namedEntities;
    SRInstance[] srls;
    
    public static String toDepString(TBTree parse, SRInstance[] predictedSRLs) {
    	boolean outputPOS = true;
    	boolean outputDep = true;
    	
    	int depOffset = outputPOS?1:0;
    	int srlOffset = outputDep?depOffset+2:depOffset;
    	
        StringBuilder buffer = new StringBuilder();
        String[][] outStr = new String[parse.getTokenCount()][srlOffset+2];
        
        TBNode[] tokens = parse.getTokenNodes(); 
        
        if (outputPOS)
        	for (int i=0; i<outStr.length; ++i)
            	outStr[i][0] = tokens[i].getPOS();
        if (outputDep)
        	for (int i=0; i<outStr.length; ++i) {
        		outStr[i][depOffset] = Integer.toString(tokens[i].getHeadOfHead()==null?0:(tokens[i].getHeadOfHead().getTokenIndex()+1));
        		outStr[i][depOffset+1] = tokens[i].getDepLabel()==null?"_":tokens[i].getDepLabel();
        	}
        
        for (int i=0; i<outStr.length; ++i) {
            outStr[i][srlOffset] = "_";
            outStr[i][srlOffset+1] = "_";
        }
        for (int j=0; j<predictedSRLs.length; ++j) {
            outStr[predictedSRLs[j].getPredicateNode().getTokenIndex()][srlOffset] = predictedSRLs[j].rolesetId;
            //System.out.println(predictedSRLs[j].toCONLLString());
            
            String idStr = Integer.toString(predictedSRLs[j].getPredicateNode().getTokenIndex()+1);
      
            String[] vals = predictedSRLs[j].toCONLLDepString().trim().split(" ");
            
            
            for (int i=0; i<outStr.length; ++i) {
            	if (vals[i].equals("_") || vals[i].equals("V"))
            		continue;
            	vals[i] = idStr+':'+vals[i];
            	
            	outStr[i][srlOffset+1] = outStr[i][srlOffset+1].equals("_")?vals[i]:outStr[i][srlOffset+1]+','+vals[i];
            }
            
        }

        int[] maxlength = new int[outStr[0].length];
        for (int i=0; i<outStr.length; ++i)
            for (int j=0; j<outStr[i].length; ++j)
                if (maxlength[j]<outStr[i][j].length())
                    maxlength[j] = outStr[i][j].length();

        for (int i=0; i<outStr.length; ++i) {       
            for (int j=0; j<outStr[i].length; ++j) {
                buffer.append(outStr[i][j]);
                for (int k=outStr[i][j].length(); k<=maxlength[j]; ++k)
                    buffer.append(' ');
            }
            buffer.append("\n");
        }
        return buffer.toString();
    }

    public static String toString(TBTree parse, SRInstance[] predictedSRLs) {
        StringBuilder buffer = new StringBuilder();
        String[][] outStr = new String[parse.getTokenCount()][predictedSRLs.length+12];

        TBNode[] nodes = parse.getTokenNodes();
        
        for (int i=0; i<outStr.length; ++i) {
            outStr[i][0] = parse.getFilename();
            outStr[i][1] = "0";
            outStr[i][2] = Integer.toString(i);
            outStr[i][3] = nodes[i].getWord();
            outStr[i][4] = nodes[i].getPOS();
            outStr[i][5] = "*"; // TODO: output parse
            outStr[i][6] = "-"; // will change to predicate
            outStr[i][7] = "-"; // will change to roleset
            outStr[i][8] = "-";
            outStr[i][9] = "-";
            outStr[i][outStr[i].length-1] = "-";
        }
        
        for (int j=0; j<predictedSRLs.length; ++j) {
            outStr[predictedSRLs[j].getPredicateNode().getTokenIndex()][6] = predictedSRLs[j].rolesetId.substring(0, predictedSRLs[j].rolesetId.indexOf('.'));
            outStr[predictedSRLs[j].getPredicateNode().getTokenIndex()][7] = predictedSRLs[j].rolesetId.substring(predictedSRLs[j].rolesetId.indexOf('.')+1);
          
            //System.out.println(predictedSRLs[j].toCONLLString());
            String[] vals = predictedSRLs[j].toCONLLString().trim().split(" ");
            for (int i=0; i<outStr.length; ++i)
                outStr[i][j+SRL_IDX] = vals[i];
        }
        /*
        
        for (int i=0; i<outStr.length; ++i)
        {
            outStr[i][0] = "-";
            for (int j=1; j<outStr[i].length; ++j)
                outStr[i][j] = "*";
        }
        for (int j=1; j<=predictedSRLs.length; ++j)
        //for (SRInstance instance:sentence.srls)
        {
            SRInstance instance = predictedSRLs[j-1];
            outStr[instance.predicateNode.getTokenIndex()][0] = instance.rolesetId;
            
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
        */
        int[] maxlength = new int[outStr[0].length];
        for (int i=0; i<outStr.length; ++i)
            for (int j=0; j<outStr[i].length; ++j)
                if (maxlength[j]<outStr[i][j].length())
                    maxlength[j] = outStr[i][j].length();

        for (int i=0; i<outStr.length; ++i)
        {       
            for (int j=0; j<outStr[i].length; ++j)
            {
                buffer.append(outStr[i][j]);
                for (int k=outStr[i][j].length(); k<=maxlength[j]; ++k)
                    buffer.append(' ');
            }
            buffer.append("\n");
        }
        return buffer.toString();
    }
    
    @Override
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
        
        TBFileReader tbReader = new SerialTBFileReader(new StringReader(parseStr.toString()));
        
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
                TBNode predicateNode = sentence.parse.getNodeByTokenIndex(i);
                sentence.srls[srlIdx] = new SRInstance(predicateNode, sentence.parse, null);
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
        //  System.out.println(instance);
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
