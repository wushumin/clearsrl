package clearsrl.align;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;

import clearcommon.util.PropertyUtil;

public class RunTrainer {
	public static void main(String[] args) throws IOException
	{
	    Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
        
        String htmlOutfile = props.getProperty("ldc.alignment.output.html", null);
        
        if (htmlOutfile==null)
            htmlOutfile = "/dev/null";
        
        PrintStream alignmentStream;
        try {
            alignmentStream = new PrintStream(props.getProperty("ldc.alignment.output.txt", null));
        } catch (Exception e) {
            alignmentStream = System.out;
        }
        
        PrintStream htmlStream = new PrintStream(htmlOutfile);
		
        SentencePairReader sentencePairReader = new LDCSentencePairReader(PropertyUtil.filterProperties(props, "ldc."));
        Aligner aligner = new Aligner(sentencePairReader);
        
        sentencePairReader.initialize();
        Aligner.initAlignmentOutput(htmlStream);
        
		//Aligner aligner = new Aligner(sentencePairReader);
		int goodCnt = 0;
		int badCnt = 0;
        
		while (true)
        {
            SentencePair sentencePair = sentencePairReader.nextPair();
            if (sentencePair==null) break;
            if (sentencePair.id >=0)
            {
                ++goodCnt;
                Alignment[] alignments = aligner.align(sentencePair);
                
                for (Alignment alignment:alignments)
                    alignmentStream.println(alignment.toString());
                
                Aligner.printAlignment(htmlStream, sentencePair, alignments);
            }
            else
                ++badCnt;
        }
		
		sentencePairReader.close();
        Aligner.finalizeAlignmentOutput(htmlStream);
        
		System.out.println(goodCnt+" "+badCnt);
	}
}
