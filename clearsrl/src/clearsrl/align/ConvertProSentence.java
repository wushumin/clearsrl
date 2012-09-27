package clearsrl.align;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;

import java.util.Properties;

import clearcommon.util.PropertyUtil;


public class ConvertProSentence {
	
	static void processSentence(Sentence s, PrintStream terminalOut, PrintStream proTermOut, PrintStream proTextOut) {
		terminalOut.print(s.tbFile);
		proTermOut.print(s.tbFile);
		
		
		for (int i=0; i<s.terminals.length; ++i)
		{
			int treeIdx = (int)(s.terminalIndices[i]>>>32);
			int terminalIdx = (int)(s.terminalIndices[i]&0xffffffff);
			
			terminalOut.printf(" %d-%d", treeIdx, terminalIdx);
			if (s.terminals[i].isTerminal() && !s.terminals[i].getWord().matches("\\*[pP].+"))
				continue;
			proTermOut.printf(" %d-%d", treeIdx, terminalIdx);
			proTextOut.printf(" %s", s.terminals[i].getWord());
		}
		
		terminalOut.print("\n");
		proTermOut.print("\n");
		proTextOut.print("\n");
	}
	
	@SuppressWarnings("unchecked")
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
		
		Sentence s=null;
		
		SentenceReader srcReader = new TokenedSentenceReader(srcProp);
		srcReader.initialize();
		
		PrintStream srcTerminalOut = new PrintStream(srcProp.getProperty("out.terminal"));
		PrintStream srcProTermOut = new PrintStream(srcProp.getProperty("out.proTerminal"));
		PrintStream srcProTextOut = new PrintStream(srcProp.getProperty("out.proText"));
		
		while ((s = srcReader.nextSentence())!=null)
			processSentence(s, srcTerminalOut, srcProTermOut, srcProTextOut);
		srcReader.close();
		srcTerminalOut.close();
		srcProTermOut.close();
		srcProTextOut.close();
		
		SentenceReader dstReader = new TokenedSentenceReader(dstProp);
		srcReader.initialize();
		
		PrintStream dstTerminalOut = new PrintStream(dstProp.getProperty("out.terminal"));
		PrintStream dstProTermOut = new PrintStream(dstProp.getProperty("out.proTerminal"));
		PrintStream dstProTextOut = new PrintStream(dstProp.getProperty("out.proText"));

		while ((s = dstReader.nextSentence())!=null)
			processSentence(s, dstTerminalOut,dstProTermOut,dstProTextOut);
		dstReader.close();
		dstTerminalOut.close();
		dstProTermOut.close();
		dstProTextOut.close();
		
	}
}
