package clearsrl.align;

import gnu.trove.TLongHashSet;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Properties;

import clearcommon.util.PropertyUtil;

public class RunTrainer {
	
	static Aligner gatherSentences(Properties props, String prefix) throws IOException
	{	
		String htmlOutfile = props.getProperty(prefix+"align.output.html", null);
        
        if (htmlOutfile==null)
            htmlOutfile = "/dev/null";
        
        PrintStream alignmentStream;
        try {
            alignmentStream = new PrintStream(props.getProperty(prefix+"align.output.txt", null));
        } catch (Exception e) {
            alignmentStream = System.out;
        }
        
        PrintStream htmlStream = new PrintStream(htmlOutfile);
		
        SentencePairReader sentencePairReader = new LDCSentencePairReader(PropertyUtil.filterProperties(props, prefix+"align."));
        
        Aligner aligner = new Aligner(sentencePairReader, Float.parseFloat(props.getProperty(prefix+"align.threshold", "0.7")));
        
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
                {
                    alignmentStream.println(sentencePair.id+","+alignment.toArgTokenString());
                    //alignment.printScoreTable(alignmentStream);
                }
                Aligner.printAlignment(htmlStream, sentencePair, alignments);
            }
            else
                ++badCnt;
        }
		
		sentencePairReader.close();
        Aligner.finalizeAlignmentOutput(htmlStream);
        if (alignmentStream!=System.out) alignmentStream.close();
        
		System.out.println(goodCnt+" "+badCnt);
	
		aligner.collectStats();
		return aligner;
	}
	
	
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
        SentencePairReader goldReader = gatherSentences(props, "ldcgold.").reader;
        SentencePairReader sysReader = gatherSentences(props, "ldcsys.").reader;
        gatherSentences(props, "ldcgold2.");
        
        
        WordAlignmentScorer srcScorer = new WordAlignmentScorer();
        WordAlignmentScorer dstScorer = new WordAlignmentScorer();
        WordAlignmentScorer sectScorer = new WordAlignmentScorer();
        WordAlignmentScorer unionScorer = new WordAlignmentScorer();
        
        goldReader.initialize();
        sysReader.initialize();
        
        while (true)
        {
            SentencePair goldSentence = goldReader.nextPair();
            SentencePair sysSentence = sysReader.nextPair();
            if (goldSentence==null) break;
            
            long[] goldWA = goldSentence.getSrcWordAlignment();
            long[] sysSrcWA = sysSentence.getSrcWordAlignment();
            long[] sysDstWA = sysSentence.getDstWordAlignment();
            
            TLongHashSet longSet = new TLongHashSet(sysSrcWA);
            longSet.addAll(sysDstWA);
            
            long[] sysUnionWA = longSet.toArray();
            
            longSet.clear();
            longSet.addAll(sysDstWA);
            longSet.retainAll(sysSrcWA);
            
            long[] sysSectWA = longSet.toArray();
            
            
            srcScorer.addAlignment(goldWA, sysSrcWA);
            dstScorer.addAlignment(goldWA, sysDstWA);
            sectScorer.addAlignment(goldWA, sysSectWA);
            unionScorer.addAlignment(goldWA, sysUnionWA);
        }
        goldReader.close();
        sysReader.close();
        
        System.out.print("src: "); srcScorer.printStats(System.out);
        System.out.print("dst: "); dstScorer.printStats(System.out);
        System.out.print("intersection: "); sectScorer.printStats(System.out);
        System.out.print("union: "); unionScorer.printStats(System.out);
        
	}
}
