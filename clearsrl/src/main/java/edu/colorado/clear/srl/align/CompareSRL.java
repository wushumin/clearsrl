package edu.colorado.clear.srl.align;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

public class CompareSRL {
    public static void main(String[] args) throws Exception
    {
        Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
        
        Aligner aligner = new Aligner(Float.parseFloat(props.getProperty("ldcgold.align.threshold", "0.7")));
        SentencePairReader goldReader = RunTrainer.gatherSentences(aligner, props, "ldcgold.");
        SentencePairReader sysReader = RunTrainer.gatherSentences(aligner, props, "ldcsys.");
        
        goldReader.initialize();
        sysReader.initialize();
        
        while (true)
        {
            SentencePair goldSentence = goldReader.nextPair();
            SentencePair sysSentence = sysReader.nextPair();
            if (goldSentence==null) break;
            
            Alignment[] alignments = aligner.align(goldSentence);
            
            for (Alignment alignment:alignments)
            {
                String goldSrc = goldSentence.src.pbInstances[alignment.srcPBIdx].toText();
                String goldDst = goldSentence.dst.pbInstances[alignment.dstPBIdx].toText();
                
                String sysSrc = sysSentence.src.pbInstances[alignment.srcPBIdx].toText();
                String sysDst = sysSentence.dst.pbInstances[alignment.dstPBIdx].toText();
                
                if (!goldSrc.equals(sysSrc) || !goldDst.equals(sysDst))
                {
                    System.out.println("\n"+goldSentence.id+" "+alignment.srcPBIdx+" "+alignment.dstPBIdx);
                    System.out.println("  "+goldSrc);
                    System.out.println("  "+sysSrc);
                    System.out.println("  "+goldDst);
                    System.out.println("  "+sysDst);
                }
            }        
        }
        goldReader.close();
        sysReader.close();
        
    }
}
