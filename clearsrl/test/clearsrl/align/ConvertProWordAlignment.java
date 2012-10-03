package clearsrl.align;

import gnu.trove.TIntArrayList;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;

import clearcommon.util.PropertyUtil;

public class ConvertProWordAlignment {
	
	static void processSentence(Sentence s, PrintStream terminalOut, PrintStream proTermOut, PrintStream proTextOut) {
		terminalOut.print(s.tbFile);
		proTermOut.print(s.tbFile);
		
		
		for (int i=0; i<s.terminals.length; ++i)
		{
			int treeIdx = (int)(s.terminalIndices[i]>>>32);
			int terminalIdx = (int)(s.terminalIndices[i]&0xffffffff);
			
			terminalOut.printf(" %d-%d", treeIdx, terminalIdx);
			if (!s.terminals[i].isToken() && !s.terminals[i].getWord().matches("\\*[pP].+"))
				continue;
			proTermOut.printf(" %d-%d", treeIdx, terminalIdx);
			proTextOut.printf(" %s", s.terminals[i].getWord());
		}
		
		terminalOut.print("\n");
		proTermOut.print("\n");
		proTextOut.print("\n");
	}
	
	static int[] convertIndices(Sentence s)
	{
		TIntArrayList nIdx = new TIntArrayList();
		for (int i=0; i<s.terminals.length; ++i)
			if (s.terminals[i].isToken() || s.terminals[i].getWord().matches("\\*[pP].*"))
				nIdx.add(i);

		return nIdx.toNativeArray();
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
		
		BufferedReader waReader = new BufferedReader(new FileReader(props.getProperty("pro_alignment")));
		
		PrintStream waOut = new PrintStream(props.getProperty("terminal_alignment"));
		
		while ((srcS = srcReader.nextSentence())!=null)
		{
			dstS = dstReader.nextSentence();
			
			int[] srcIdx = convertIndices(srcS);
			int[] dstIdx = convertIndices(dstS);
			
			String line = waReader.readLine().trim();
			
			String[] alignmentStrs = line.isEmpty()?new String[0]:line.split("[ \t]+");
    	    
	        int srcI, dstI;
	        
	        for (String s : alignmentStrs)
	        {
	        	srcI = Integer.parseInt(s.substring(0, s.indexOf('-')));
	        	dstI = Integer.parseInt(s.substring(s.indexOf('-')+1));
	        	
	        	waOut.printf("%d-%d ", srcIdx[srcI], dstIdx[dstI]);
	        }
	        waOut.print("\n");
		}
		
		srcReader.close();
		dstReader.close();
		waReader.close();
		waOut.close();
		
	}

}
