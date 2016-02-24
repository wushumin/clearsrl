package edu.colorado.clear.srl.util;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.logging.Logger;

import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.align.Aligner;
import edu.colorado.clear.srl.align.Alignment;
import edu.colorado.clear.srl.align.DefaultSentencePairReader;
import edu.colorado.clear.srl.align.SentencePair;
import edu.colorado.clear.srl.align.SentencePairReader;

public class MakeSentences {
   
	private static Logger logger = Logger.getLogger("clearsrl");
	
	public static void writeAlignment(SentencePair s, PrintStream out,  PrintStream outhtml, Aligner aligner,LanguageUtil chUtil, LanguageUtil enUtil) throws Exception {
		
		Alignment[] alignments = aligner.align(s);
		for (Alignment alignment:alignments) {
			out.printf("%d,%s;[%s,%s];[%s,%s]\n",s.id+1, alignment.toString(), 
					alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset(),
					chUtil.isVerb(alignment.getSrcPBInstance().getPredicate().getPOS())?"VERB":"NOUN",
					enUtil.isVerb(alignment.getDstPBInstance().getPredicate().getPOS())?"VERB":"NOUN");
		}
		if (outhtml!=null)
			Aligner.printAlignment(outhtml, s, alignments);
	}
	
	
	static void readSentencePairs(Properties props, String filter1, String filter2, LanguageUtil chUtil, LanguageUtil enUtil) throws Exception {
	
		props = PropertyUtil.filterProperties(props, filter1, true);
		props = PropertyUtil.filterProperties(props, filter2, true);
		
		logger.info("Processing "+filter1+"."+filter2);
		SentencePairReader reader = new DefaultSentencePairReader(props);
		reader.initialize();
		
		String[] subTypes = {"",".dstVerb",".srcVerb",".allVerbs"};

		float threshhold = Float.parseFloat(props.getProperty("threshold"));
		
		Aligner aligners[] = new Aligner[]{new Aligner(threshhold, true, true, chUtil, enUtil, null, 0, 0, false),
				new Aligner(threshhold, true, false, chUtil, enUtil, null, 0, 0, false),
				new Aligner(threshhold, false, true, chUtil, enUtil, null, 0, 0, false),
				new Aligner(threshhold, false, false, chUtil, enUtil, null, 0, 0, false),};
		
		//(Float.parseFloat(props.getProperty("threshold")));
		
		String outName = props.getProperty("output.txt");
		PrintStream[] outs = new PrintStream[]{new PrintStream(outName),
				new PrintStream(outName.substring(0, outName.lastIndexOf('.'))+subTypes[1]+outName.substring(outName.lastIndexOf('.'))),
				new PrintStream(outName.substring(0, outName.lastIndexOf('.'))+subTypes[2]+outName.substring(outName.lastIndexOf('.'))),
				new PrintStream(outName.substring(0, outName.lastIndexOf('.'))+subTypes[3]+outName.substring(outName.lastIndexOf('.')))};
		
		//new PrintStream(props.getProperty("output.txt"));
		
		PrintStream[] outhtmls = null;
		String htmlOutFile = props.getProperty("output.html");
		if (htmlOutFile!=null) {
			outhtmls = new PrintStream[4];
			outhtmls[0] = new PrintStream(htmlOutFile);
			outhtmls[1] = new PrintStream(htmlOutFile.substring(0, htmlOutFile.lastIndexOf('.'))+subTypes[1]+htmlOutFile.substring(htmlOutFile.lastIndexOf('.')));
			outhtmls[2] = new PrintStream(htmlOutFile.substring(0, htmlOutFile.lastIndexOf('.'))+subTypes[2]+htmlOutFile.substring(htmlOutFile.lastIndexOf('.')));
			outhtmls[3] = new PrintStream(htmlOutFile.substring(0, htmlOutFile.lastIndexOf('.'))+subTypes[3]+htmlOutFile.substring(htmlOutFile.lastIndexOf('.')));
		}
		
		SentencePair s = null;
		if (outhtmls!=null) {
			for (PrintStream outhtml:outhtmls)
				Aligner.initAlignmentOutput(outhtml);
		}
			
		while ((s = reader.nextPair())!=null) {
			
			logger.info("processing "+s.src.tbFile+" "+s.id);
			//logger.info(s.toString());

			for (int i=0; i<4;++i)
				writeAlignment(s, outs[i], outhtmls==null?null:outhtmls[i],aligners[i], chUtil, enUtil);
			
		}
		reader.close();
		for (PrintStream out:outs)
			out.close();
		if (outhtmls!=null)
			for (PrintStream outhtml:outhtmls)
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
        
        readSentencePairs(props, "nw.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "nw.", "auto.", chUtil, enUtil);
        readSentencePairs(props, "nw.", "gwa.", chUtil, enUtil);
        readSentencePairs(props, "nw.", "berk.", chUtil, enUtil);
   
        readSentencePairs(props, "bc.", "gold.", chUtil, enUtil);
        readSentencePairs(props, "bc.", "gwa.", chUtil, enUtil);
        readSentencePairs(props, "bc.", "berk.", chUtil, enUtil);
        readSentencePairs(props, "bc.", "auto.", chUtil, enUtil);
        
        //readSentencePairs(props, "nwpart.", "gold.", chUtil, enUtil);
        //readSentencePairs(props, "nwpart.", "berk.", chUtil, enUtil);
        //readSentencePairs(props, "nwpart.", "auto.", chUtil, enUtil);
        
        //readSentencePairs(props, "nwtest.", "gold.", chUtil, enUtil);
        //readSentencePairs(props, "nwtest.", "gwa.", chUtil, enUtil);
        //readSentencePairs(props, "nwtest.", "berk.", chUtil, enUtil);
        //readSentencePairs(props, "nwtest.", "auto.", chUtil, enUtil);
        
    }
}
