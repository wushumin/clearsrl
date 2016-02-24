package clearsrl.align;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import clearcommon.alg.CrossValidator;

public class AnnotationOutput {
    
    static String process(String in)
    {
        String[] sa = in.split(" ");
        StringBuilder builder = new StringBuilder();
        
        boolean underscore = false;
        
        for (int i=2; i<sa.length; ++i)
        {
            if (sa[i].equals(" ")) continue;
            builder.append(sa[i]);
            if (sa[i].length()>1 && sa[i].startsWith("["))
                underscore = true;
            if (sa[i].length()>1 && sa[i].endsWith("]"))
                underscore = false;
            builder.append(underscore?'_':' ');
        }
        return builder.toString();
    }
    
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
        
        goldReader.initialize();
        
        List<SentencePair> sentences = new ArrayList<SentencePair>();
        
        while (true)
        {
            SentencePair goldSentence = goldReader.nextPair();
            if (goldSentence == null) break;
            
            sentences.add(goldSentence);
        }
        goldReader.close();

        int[] perm = new int[sentences.size()];
        for (int i=0; i<perm.length; ++i)
            perm[i] = i;
        CrossValidator.randomPermute(perm, new Random(12345));
        
        int[] indices = Arrays.copyOf(perm, 50);
        //int[] indices = Arrays.copyOf(perm, 10000);
        Arrays.sort(indices);
        
        for (int index:indices)
        {
            SentencePair sentence = sentences.get(index);
            Alignment[] alignments = aligner.align(sentence);
            
            for (Alignment alignment:alignments)
            {
                 System.out.printf("%d%02d%02d\n",index,alignment.srcPBIdx,alignment.dstPBIdx);
                 System.out.println(process(sentence.src.pbInstances[alignment.srcPBIdx].toText())+"[NULL]");
                 System.out.println(process(sentence.dst.pbInstances[alignment.dstPBIdx].toText())+"[NULL]");
            }
        }
    }
}
