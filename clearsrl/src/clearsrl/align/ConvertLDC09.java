package clearsrl.align;

import gnu.trove.TIntArrayList;
import gnu.trove.TLongArrayList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.FileUtil;
import clearcommon.util.PropertyUtil;

public class ConvertLDC09 {
	
	static Properties props;
	
	static Map<String, TBTree[]> srcTreeBank;
	static Map<String, TBTree[]> dstTreeBank;
	static Map<String, SortedMap<Integer, List<PBInstance>>>  srcPropBank;
	static Map<String, SortedMap<Integer, List<PBInstance>>>  dstPropBank;
    
	static Map<String, String> srcFilenameMap;
	static Map<String, String> dstFilenameMap;
	
	static Deque<String> fileList;
	static Deque<SentencePair> sentenceQueue;
	
	static int cnt;

	static SentencePair nextPair() {
		if (sentenceQueue.isEmpty())
		{
			if (fileList.isEmpty()) return null;
			try {
				readNextFile();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
			return nextPair();
		}
		return sentenceQueue.removeFirst();
	}
	
	static void readNextFile() throws IOException
	{
		String filePrefix = fileList.removeFirst();

		BufferedReader alignmentReader = new BufferedReader(new FileReader(new File(props.getProperty("wa.dir"), filePrefix+".ctb.wa")));
		BufferedReader srcTokenReader = new BufferedReader(new FileReader(new File(props.getProperty("src.token.dir"), filePrefix+".cmn.tkn")));
		BufferedReader dstTokenReader = new BufferedReader(new FileReader(new File(props.getProperty("dst.token.dir"), filePrefix+".eng.tkn")));
		
		String srcTreeFile = srcFilenameMap.get(filePrefix)+".parse";
		String dstTreeFile = dstFilenameMap.get(filePrefix)+".parse";
		
		TBTree[] srcTrees = srcTreeBank.get(srcTreeFile);
		TBTree[] dstTrees = dstTreeBank.get(dstTreeFile);
		
		long srcStartIndex = 0;
		long dstStartIndex = 0;
		
		//Ontonotes 4.0 addes document title to xinhua news
		if (srcTreeFile.matches(".*chtb_0.*"))
			srcStartIndex = ((long)1)<<32;

		String line;
		while ((line=alignmentReader.readLine())!=null)
		{
			String[] alignments = line.split("\\s+");;
			String[] srcTokens = srcTokenReader.readLine().trim().split("\\s+");
			String[] dstTokens = dstTokenReader.readLine().trim().split("\\s+");
			
			int[] srcTokenCnt = new int[srcTokens.length];
			int[] dstTokenCnt = new int[dstTokens.length];
						
			String[] srcRawTokens = findRawTokens(srcTokens, srcTokenCnt);
			String[] dstRawTokens = findRawTokens(dstTokens, dstTokenCnt);
			
			TLongArrayList srcTokenIndices = new TLongArrayList();
			TLongArrayList dstTokenIndices = new TLongArrayList();
			
			int srcRet = matchTokens(srcRawTokens, srcTrees, srcStartIndex, srcTokenIndices);
			int dstRet = matchTokens(dstRawTokens, dstTrees, dstStartIndex, dstTokenIndices);
			
			// can't find matching tokens
			if (srcRet<0 || dstRet<0) break;
			
			srcStartIndex = srcTokenIndices.isEmpty()?srcStartIndex:srcTokenIndices.get(srcTokenIndices.size()-1)+1;
			dstStartIndex = dstTokenIndices.isEmpty()?dstStartIndex:dstTokenIndices.get(dstTokenIndices.size()-1)+1;

			// different tokenization
			if (srcRet>0 || dstRet>0) continue;
			
			// no word alignment
			if (alignments.length==1 && alignments[0].equals("rejected")) continue;
			
			SentencePair pair = new SentencePair(cnt++);
			pair.src = Sentence.parseSentence(srcTokenIndices.toNativeArray(), srcTreeFile, srcTrees, srcPropBank.get(srcTreeFile));
			pair.dst = Sentence.parseSentence(dstTokenIndices.toNativeArray(), dstTreeFile, dstTrees, dstPropBank.get(dstTreeFile));

			TIntArrayList srcAlignmentTerminals = new TIntArrayList();
			TIntArrayList dstAlignmentTerminals = new TIntArrayList();
			
			Pattern alignmentPattern = Pattern.compile("([^-\\(\\)]*)-([^-\\(\\)]*)(\\((\\w|,)+\\))?");
			//System.out.println(line);
			for (String alignment:alignments)
			{
				Matcher m = alignmentPattern.matcher(alignment);
				if (!m.matches())
				{
					System.err.println("Can't match: "+alignment);
					break;
				}
				if (m.group(1).isEmpty() || m.group(2).isEmpty()) continue;
				
				int[] srcA = getIndices(m.group(1).split("(?<=[0-9\\]]),"));
				int[] dstA = getIndices(m.group(2).split("(?<=[0-9\\]]),"));
				
				for (int sIdx:srcA)
					for (int dIdx:dstA)
					{
						//System.out.printf("%d-%d ", sIdx,dIdx);
						if (srcTokenCnt[sIdx-1]>=0 && dstTokenCnt[dIdx-1]>=0)
						{
							srcAlignmentTerminals.add(srcTokenCnt[sIdx-1]);
							dstAlignmentTerminals.add(dstTokenCnt[dIdx-1]);
						}
							
					}
			}
			/*
			System.out.println("");
			for (int i=0; i<srcAlignmentTerminals.size(); ++i)
				System.out.printf("%d-%d ", srcAlignmentTerminals.get(i),dstAlignmentTerminals.get(i));
			System.out.println("\n");
			*/
			pair.setAlignment(srcAlignmentTerminals.toNativeArray(), dstAlignmentTerminals.toNativeArray());
			sentenceQueue.add(pair);
		}
		
		alignmentReader.close();
		srcTokenReader.close();
		dstTokenReader.close();
		
	}
	
	static int[] getIndices(String[] strs)
	{
		int[] indices = new int[strs.length];
		for (int i=0; i<strs.length; ++i)
		{
			if (strs[i].charAt(strs[i].length()-1)==']')
				indices[i] = Integer.parseInt(strs[i].substring(0, strs[i].indexOf('[')));
			else
				indices[i] = Integer.parseInt(strs[i]);
		}
		return indices;
	}
	
	static int matchTokens(String[] tokens, TBTree[] trees, long startIndex, TLongArrayList treeIndices)
	{
		StringBuilder rawTokenStr = new StringBuilder();
		StringBuilder rawTokenizedStr = new StringBuilder();
		
		for (String token:tokens)
		{
			rawTokenStr.append(token);
			rawTokenizedStr.append(token);
			rawTokenizedStr.append(' ');
		}
		
		StringBuilder treeTokenStr = new StringBuilder();
		StringBuilder treeTokenizedStr = new StringBuilder();
		
		int tokenLen = getLength(tokens);
		
		int lenCnt = 0;

		int treeIdx = (int) (startIndex>>32);
		int tokenIdx = (int) (startIndex&0xffffffff);
		
		List<TBNode> treeTokens = null;
		
		while (lenCnt < tokenLen)
		{
			if (tokenIdx>=trees[treeIdx].getTokenCount())
			{
				++treeIdx;
				tokenIdx=0;
				treeTokens = trees[treeIdx].getRootNode().getTokenNodes();
				continue;
			}
			
			if (treeTokens==null) treeTokens = trees[treeIdx].getRootNode().getTokenNodes();

			lenCnt+= treeTokens.get(tokenIdx).getWord().length();
			treeTokenStr.append(treeTokens.get(tokenIdx).getWord());
			treeTokenizedStr.append(treeTokens.get(tokenIdx).getWord());
			treeTokenizedStr.append(' ');
			treeIndices.add((((long)(treeIdx))<<32)|tokenIdx);
			++tokenIdx;	
		}
		
		if (!rawTokenStr.toString().equals(treeTokenStr.toString()))
		{
			System.err.println("Unmatched string detected! "+trees[0].getFilename());
			System.err.println(rawTokenStr.toString());
			System.err.println(treeTokenStr.toString());
			return -1;
		}
		if (!rawTokenizedStr.toString().trim().equals(treeTokenizedStr.toString().trim()))
		{
			System.err.println("Unmatched tokenization detected!");
			System.err.println(rawTokenizedStr.toString());
			System.err.println(treeTokenizedStr.toString());
			return 1;
		}
	
		//System.out.println(rawTokenizedStr.toString());
		//System.out.println(treeTokenizedStr.toString());
		return 0;
	}

	static int getLength(String[] sArray)
	{
		int length = 0;
		for (String str:sArray)
			length += str.length();
		return length;
	}
	
	static String[] findRawTokens(String[] tokens, int[] tCnt)
	{
		List<String> rawTokens = new ArrayList<String>();
		int tIdx = -1;
		
		for (int i=0; i<tokens.length; ++i)
			if (tokens[i].matches("\\[.*\\]|\\<.*\\>|\\*.*|0") &&
					!(tokens[i].equals("0") && i>=2 && tokens[i-1].equals(":") && tokens[i-2].matches("\\d+")))
				tCnt[i] = -1;
			else
			{
				tCnt[i] = ++tIdx;
				rawTokens.add(tokens[i]);
			}
		return rawTokens.toArray(new String[rawTokens.size()]);
	}
	
    static Map<String, String> readMaps(File dir)
    {
    	final String name = "map\\.txt";
    	
    	Map<String, String> fNameMap = new HashMap<String, String>();
    	
    	List<String> mapFiles = FileUtil.getFiles(dir, name);
    	
    	for (String mapFile:mapFiles)
    	{
    		try {
				BufferedReader reader = new BufferedReader(new FileReader(new File(dir, mapFile)));
				String prefix = mapFile.substring(0, mapFile.length()-name.length()+1);
				
				String line;
				while ((line = reader.readLine())!=null)
				{
					String[] vals = line.trim().split("\\s+");
					if (vals.length==2) 
					{
						fNameMap.put(vals[1], prefix+vals[0]);
						fNameMap.put(new File(vals[0]).getName(), prefix+vals[0]);
					}
				}
				
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    	
    	return fNameMap;
    }
 

	public static void main(String[] args) throws IOException
	{
		props = new Properties();
		{
			FileInputStream in = new FileInputStream(args[0]);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		
		props = PropertyUtil.filterProperties(props, args[1]);
		
        sentenceQueue = new ArrayDeque<SentencePair>();
        
        if (props.getProperty("wa.dir")!=null)
        {
        	List<String> fNames = FileUtil.getFiles(new File(props.getProperty("wa.dir")), "[^\\.]+.*");

        	fileList = new LinkedList<String>();
        	List<String> srcTreeFilePrefixs = new ArrayList<String>();
        	List<String> dstTreeFilePrefixs = new ArrayList<String>();
        	
        	String srcOntonotesDir = props.getProperty("src.ontonotes.dir");
        	String dstOntonotesDir = props.getProperty("dst.ontonotes.dir");
        	
        	srcFilenameMap = readMaps(new File(srcOntonotesDir));
        	dstFilenameMap = readMaps(new File(dstOntonotesDir));

        	for (String fName:fNames)
        	{
        		String prefix = fName.substring(0, fName.length()-".ctb.wa".length());
        		String srcVal = srcFilenameMap.get(prefix);
        		String dstVal = dstFilenameMap.get(prefix);
        		if (srcVal==null || dstVal==null) continue;
        		fileList.add(prefix);
        		srcTreeFilePrefixs.add(srcVal);
        		dstTreeFilePrefixs.add(dstVal);
        	}
        	// now read tree and propbank
        	
        	List<String> srcTreeFiles = new ArrayList<String>();
        	List<String> srcPropFiles = new ArrayList<String>();
        	
        	List<String> dstTreeFiles = new ArrayList<String>();
        	List<String> dstPropFiles = new ArrayList<String>();
        	
        	for (String filePrefix:srcTreeFilePrefixs)
        	{
        		srcTreeFiles.add(filePrefix+".parse");
        		srcPropFiles.add(srcOntonotesDir+File.separatorChar+filePrefix+".prop");
        	}
        	srcTreeBank = TBUtil.readTBDir(srcOntonotesDir, srcTreeFiles);
        	srcPropBank = PBUtil.readPBDir(srcPropFiles, new TBReader(srcTreeBank), new OntoNoteTreeFileResolver());

        	for (String filePrefix:dstTreeFilePrefixs)
        	{
        		dstTreeFiles.add(filePrefix+".parse");
        		dstPropFiles.add(dstOntonotesDir+File.separatorChar+filePrefix+".prop");
        	}
            dstTreeBank = TBUtil.readTBDir(dstOntonotesDir, dstTreeFiles);
            dstPropBank = PBUtil.readPBDir(dstPropFiles, new TBReader(dstTreeBank), new OntoNoteTreeFileResolver());
        	
        }

        PrintStream srcTokenOut = new PrintStream(props.getProperty("src.token.file"));
        PrintStream dstTokenOut = new PrintStream(props.getProperty("dst.token.file"));
        PrintStream srcTextOut = new PrintStream(props.getProperty("src.text.file"));
        PrintStream dstTextOut = new PrintStream(props.getProperty("dst.text.file"));
        PrintStream tokenAlign = new PrintStream(props.getProperty("token.alignment.file"));
        PrintStream srcTerminalOut = new PrintStream(props.getProperty("src.terminal.file"));
        PrintStream dstTerminalOut = new PrintStream(props.getProperty("dst.terminal.file"));
        PrintStream terminalAlign = new PrintStream(props.getProperty("terminal.alignment.file"));
        
        SentencePair s;
        while ((s=nextPair())!=null)
        {	
		    long[] wa = s.getWordAlignment(SentencePair.WordAlignmentType.UNION);
		 
		    srcTokenOut.print(s.src.tbFile);
		    for (int i=0; i<s.src.indices.length; ++i)
		    {
		    	int treeIndex = (int)(s.src.indices[i]>>>32);
		    	int tokenIndex = s.src.tokens[i].getTokenIndex();
		    	srcTokenOut.printf(" %d-%d",treeIndex,tokenIndex);
		    }
		    srcTokenOut.print("\n");
		    
		    dstTokenOut.print(s.dst.tbFile);
		    for (int i=0; i<s.dst.indices.length; ++i)
		    {
		    	int treeIndex = (int)(s.dst.indices[i]>>>32);
		    	int tokenIndex = s.dst.tokens[i].getTokenIndex();
		    	dstTokenOut.printf(" %d-%d",treeIndex,tokenIndex);
		    }
		    dstTokenOut.print("\n");

		    for (long a:wa)
		    	tokenAlign.printf("%d-%d ", (int)(a>>>32), (int)(a&0xffffffff));
		    tokenAlign.print("\n");

		    srcTextOut.println(s.src.toTokens());
		    dstTextOut.println(s.dst.toTokens());
		    
		    //------------------------------------------------------------------------------------------
		    
		    srcTerminalOut.print(s.src.tbFile);
		    for (long index:s.src.terminalIndices)
		    {
		    	int treeIndex = (int)(index>>>32);
		    	int terminalIndex = (int)(index&0xffffffff);
		    	srcTerminalOut.printf(" %d-%d",treeIndex,terminalIndex);
		    }
		    srcTerminalOut.print("\n");
		    
		    dstTerminalOut.print(s.dst.tbFile);
		    for (long index:s.dst.terminalIndices)
		    {
		    	int treeIndex = (int)(index>>>32);
		    	int terminalIndex = (int)(index&0xffffffff);
		    	dstTerminalOut.printf(" %d-%d",treeIndex,terminalIndex);
		    }		    
		    dstTerminalOut.print("\n");

		    for (long a:wa)
    		{
		    	int src = (int)(a>>>32);
		    	int dst = (int)(a&0xffffffff);
		    	terminalAlign.printf("%d-%d ",s.src.tokenToTerminalMap[src-1]+1,s.dst.tokenToTerminalMap[dst-1]+1);
    		}
		    terminalAlign.print("\n");   
		    
		    
		    // print out format for ISI
		    int tree = -1;
		    BitSet tokenSet = null;
		    boolean oneTree = true;
		    
		    for (long idx: s.dst.indices)
		    {
		    	int treeidx = (int)(idx>>>32);
		    	int tokenIdx = (int)(idx&0xffffffff);
		    	
		    	if (tree<0)
		    	{
		    		tree=treeidx;
		    		tokenSet = new BitSet();
		    	}
		    	else if (tree!=treeidx)
		    	{
		    		oneTree = false;
		    		
		    	}
		    	tokenSet.set(tokenIdx);
		    }
		    
		    if (!tokenSet.get(0)) continue;
		    
		    System.out.print(s.dst.tbFile.substring(0, s.dst.tbFile.length()-5)+tree);
		    
		    if (!oneTree) 
		    {
		    	System.out.println(" ON TOO LONG");
		    	continue;
		    }
		    
		    if (tokenSet.cardinality()!=s.dst.treeMap.get(tree).getTokenCount())
		    {
		    	System.out.println(" ON TOO SHORT");
		    }
		    
	    	for (TBNode node:s.src.tokens)
	    		System.out.print(" "+node.getWord());
	    	System.out.print("\n");
		    
        } 
        srcTokenOut.close();
        dstTokenOut.close();
        tokenAlign.close();
        srcTerminalOut.close();
        dstTerminalOut.close();
        terminalAlign.close();
	}
}
