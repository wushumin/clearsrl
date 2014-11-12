package clearsrl.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.Logger;

import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.align.Aligner;
import clearsrl.align.Alignment;
import clearsrl.align.DefaultSentencePairReader;
import clearsrl.align.SentencePair;
import clearsrl.align.SentencePairReader;

public class MakeSentences {
   
	private static Logger logger = Logger.getLogger("clearsrl");
	
	static void readSentencePairs(Properties props, String filter1, String filter2, LanguageUtil chUtil, LanguageUtil enUtil) throws Exception {
	
		props = PropertyUtil.filterProperties(props, filter1, true);
		props = PropertyUtil.filterProperties(props, filter2, true);
		
		Aligner aligner = new Aligner(Float.parseFloat(props.getProperty("threshold")));
		
		PrintStream out = new PrintStream(props.getProperty("output.txt"));
		
		PrintStream outhtml = null;
		String htmlOutFile = props.getProperty("output.html");
		if (htmlOutFile!=null)
			outhtml = new PrintStream(htmlOutFile);
		
		SentencePairReader reader = new DefaultSentencePairReader(props);
		reader.initialize();
		SentencePair s = null;
		if (outhtml!=null)
			Aligner.initAlignmentOutput(outhtml);
		while ((s = reader.nextPair())!=null) {
			
			
			Alignment[] alignments = aligner.align(s);
			for (Alignment alignment:alignments) {
				out.printf("%d,%s;[%s,%s]\n",s.id+1, alignment.toString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());
			}
			if (outhtml!=null)
				Aligner.printAlignment(outhtml, s, alignments);
		}
		reader.close();
		out.close();
		if (outhtml!=null)
			Aligner.finalizeAlignmentOutput(outhtml);
	}
	
	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
        props = PropertyUtil.resolveEnvironmentVariables(props);
        
        LanguageUtil chUtil = (LanguageUtil) Class.forName(props.getProperty("chinese.util-class")).newInstance();
        if (!chUtil.init(PropertyUtil.filterProperties(props,"chinese."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("chinese.util-class")));
            System.exit(-1);
        }
        
        LanguageUtil enUtil = (LanguageUtil) Class.forName(props.getProperty("english.util-class")).newInstance();
        if (!enUtil.init(PropertyUtil.filterProperties(props,"english."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("english.util-class")));
            System.exit(-1);
        }
   
        readSentencePairs(props, "bc.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "bc.", "berk.", chUtil, enUtil);
        readSentencePairs(props, "bc.", "auto.", chUtil, enUtil);
        
        readSentencePairs(props, "nw.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "nw.", "berk.", chUtil, enUtil);
        readSentencePairs(props, "nw.", "auto.", chUtil, enUtil);
        
        readSentencePairs(props, "nwpart.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "nwpart.", "berk.", chUtil, enUtil);
        readSentencePairs(props, "nwpart.", "auto.", chUtil, enUtil);
        
        readSentencePairs(props, "nwtest.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "nwtest.", "berk.", chUtil, enUtil);
        readSentencePairs(props, "nwtest.", "auto.", chUtil, enUtil);
        
    }
}
