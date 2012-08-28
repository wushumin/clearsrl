package clearsrl.align;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;

import clearcommon.treebank.TBNode;
import clearcommon.util.PropertyUtil;

public class WriteSentencePro {

	static void printProLine(TBNode[] nodes, PrintStream out) {
		for (TBNode node:nodes)
		{
			if (node.isToken()) out.print(node.getWord()+" ");
			//else if (node.getWord().startsWith("*pro")) out.print("LPRO ");
			//else if (node.getWord().startsWith("*PRO")) out.print("BPRO ");
		}
		out.print("\n");
	}
	
	public static void main(String[] args) throws IOException{
		Properties props = new Properties();
		{
			FileInputStream in = new FileInputStream(args[0]);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		
		System.err.print(PropertyUtil.toString(props));
		
		Properties srcProps = PropertyUtil.filterProperties(props,"src.", true);
		Properties dstProps = PropertyUtil.filterProperties(props,"dst.", true);
		
		SentenceReader srcReader = new TokenedSentenceReader(srcProps);
		SentenceReader dstReader = new TokenedSentenceReader(dstProps);
		
		srcReader.initialize();
		dstReader.initialize();
		
		Sentence src;
		Sentence dst;
		
		PrintStream srcOut = new PrintStream(srcProps.getProperty("protext"));
		PrintStream dstOut = new PrintStream(dstProps.getProperty("protext"));
		
		while ((src=srcReader.nextSentence())!=null && (dst=dstReader.nextSentence())!=null)
		{
			printProLine(src.terminals, srcOut);
			printProLine(dst.terminals, dstOut);
		}
		
		srcReader.close();
		dstReader.close();
		srcOut.close();
		dstOut.close();
		
	}
}
