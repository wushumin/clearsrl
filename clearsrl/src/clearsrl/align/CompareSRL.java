package clearsrl.align;

import java.io.FileInputStream;
import java.io.IOException;
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
        
        Aligner aligner = RunTrainer.gatherSentences(props, "ldcgold.");
        SentencePairReader goldReader = aligner.reader;
        SentencePairReader sysReader = RunTrainer.gatherSentences(props, "ldcsys.").reader;
        
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
