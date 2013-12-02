package clearsrl.align;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Properties;

import clearcommon.util.PropertyUtil;

public class ConvertProWordAlignment {

	static int[] convertIndices(Sentence s)
	{
		TIntList nIdx = new TIntArrayList();
		for (int i=0; i<s.terminals.length; ++i)
			if (s.terminals[i].isToken() || s.terminals[i].getWord().matches("\\*[pP].*"))
				nIdx.add(i);

		return nIdx.toArray();
	}
	
	static int[] convertIndices(Sentence s, String line)
	{
		String[] texts = line.split("\\s+");

		TIntArrayList nIdx = new TIntArrayList();
		for (int i=0; i<texts.length; ++i) {
			int treeIdx = Integer.parseInt(texts[i].substring(0,texts[i].indexOf('-')));
			int terminalIdx = Integer.parseInt(texts[i].substring(texts[i].indexOf('-')+1));
			nIdx.add(Arrays.binarySearch(s.terminalIndices, Sentence.makeIndex(treeIdx, terminalIdx)));
		}

		return nIdx.toArray();
	}
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{			
		Properties props = new Properties();
		{
			FileInputStream in = new FileInputStream(args[0]);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		System.err.println(PropertyUtil.toString(props));
		
		Properties srcProp = PropertyUtil.filterProperties(props, "src.", true);
		Properties dstProp = PropertyUtil.filterProperties(props, "dst.", true);
		
		Sentence srcS=null, dstS=null;
		
		SentenceReader srcReader = new TokenedSentenceReader(srcProp);
		srcReader.initialize();
		SentenceReader dstReader = new TokenedSentenceReader(dstProp);
		dstReader.initialize();
		
		String prefix = args[1];
		
		BufferedReader waReader = new BufferedReader(new FileReader(props.getProperty(prefix+"alignment")));
		BufferedReader srcTerminalReader = new BufferedReader(new FileReader(props.getProperty("src."+prefix+"terminal")));
		BufferedReader dstTerminalReader = new BufferedReader(new FileReader(props.getProperty("dst."+prefix+"terminal")));
		
		PrintStream waOut = new PrintStream(props.getProperty("terminal_alignment"));
		PrintStream waTokenOut = new PrintStream(props.getProperty("token_alignment"));
		
		int sCnt = 0;
		
		while ((srcS = srcReader.nextSentence())!=null)
		{
			dstS = dstReader.nextSentence();
			
			//int[] srcIdx = convertIndices(srcS);
			//int[] dstIdx = convertIndices(dstS);
			
			int[] srcIdx = convertIndices(srcS, srcTerminalReader.readLine().trim());
			int[] dstIdx = convertIndices(dstS, dstTerminalReader.readLine().trim());
			
			String line = waReader.readLine().trim();
			
			String[] alignmentStrs = line.isEmpty()?new String[0]:line.split("[ \t]+");
    	    
	        int srcI, dstI;
	        System.out.print(sCnt);
	        

	        
	        BitSet proSet = new BitSet();	        
	        for (int i=0; i<srcS.terminals.length; ++i)
	        	if (srcS.terminals[i].getWord().startsWith("*pro"))
	        		proSet.set(i);
	        
	        //System.err.println(Arrays.asList(srcS.terminals));
	        
	        for (String s : alignmentStrs)
	        {
	        	srcI = Integer.parseInt(s.substring(0, s.indexOf('-')));
	        	dstI = Integer.parseInt(s.substring(s.indexOf('-')+1));
	        	
	        	waOut.printf("%d-%d ", srcIdx[srcI], dstIdx[dstI]);
	        	if (proSet.get(srcIdx[srcI]))
	        		System.out.printf(" %d:%d:unspec", srcIdx[srcI]+1, dstIdx[dstI]+1);
	        	else
	        		waTokenOut.printf("%d-%d ", srcS.terminalToTokenMap[srcIdx[srcI]], dstS.terminalToTokenMap[dstIdx[dstI]]);
	        	
	        }
	        waOut.print("\n");
	        waTokenOut.print("\n");
	        System.out.print("\n");
	        
	        ++sCnt;
		}
		
		srcReader.close();
		dstReader.close();
		srcTerminalReader.close();
		dstTerminalReader.close();
		waReader.close();
		waOut.close();
		waTokenOut.close();
	}

}
