package edu.colorado.clear.srl.align;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import edu.colorado.clear.common.util.PropertyUtil;

public class RunTrainer {
    
    static SentencePairReader gatherSentences(Aligner aligner , Properties props, String prefix) 
    		throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException  {   
        Map<String, TObjectIntMap<String>> srcDstMapping = new TreeMap<String, TObjectIntMap<String>>();
        Map<String, TObjectIntMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntMap<String>>();

        
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
        
        SentencePairReader sentencePairReader = prefix.startsWith("LDC")
            ? new LDCSentencePairReader(PropertyUtil.filterProperties(props, prefix+"align.")) 
            : new DefaultSentencePairReader(PropertyUtil.filterProperties(props, prefix+"align."));
        
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
                    
                    for (ArgAlignmentPair alignmentPair:alignment.getArgAlignmentPairs())
                    {   
                        if (alignment.getSrcPBArg(alignmentPair.srcArgIdx).isPredicate() && 
                            alignment.getDstPBArg(alignmentPair.dstArgIdx).isPredicate())
                        {
                            String srcRole = alignment.getSrcPBInstance().getRoleset();
                            String dstRole = alignment.getDstPBInstance().getRoleset();
                            
                            TObjectIntMap<String> tgtMap;

                            // strip roleset id
                            if (srcRole.lastIndexOf('.')>=0) 
                                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.')+1);
                            if (dstRole.lastIndexOf('.')>=0) 
                                    dstRole = dstRole.substring(0,dstRole.lastIndexOf('.')+1);
                            
                            if ((tgtMap = srcDstMapping.get(srcRole))==null)
                            {
                                    tgtMap = new TObjectIntHashMap<String>();
                                    srcDstMapping.put(srcRole, tgtMap);
                            }
                            tgtMap.put(dstRole, tgtMap.get(dstRole)+1);
                            
                            if ((tgtMap = dstSrcMapping.get(dstRole))==null)
                            {
                                    tgtMap = new TObjectIntHashMap<String>();
                                    dstSrcMapping.put(dstRole, tgtMap);
                            }
                            tgtMap.put(srcRole, tgtMap.get(srcRole)+1);     
                            break;
                        }
                    }
                    
                    //alignment.printScoreTable(alignmentStream);
                }
                Aligner.printAlignment(htmlStream, sentencePair, alignments);
                sentencePair.printPredicates(System.out);
            }
            else
                ++badCnt;
            
            //if (goodCnt>10000) break;
        }
        
        sentencePairReader.close();
        Aligner.finalizeAlignmentOutput(htmlStream);
        if (alignmentStream!=System.out) alignmentStream.close();
        
        System.out.println(goodCnt+" "+badCnt);

        
        Set<String> srcLightVerbs = new HashSet<String>();
        {
                StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.srcLightVerbs"),",");
                while(tok.hasMoreTokens())
                        srcLightVerbs.add(tok.nextToken().trim()+".");
                System.out.println(srcLightVerbs);
        }
        
        Set<String> dstLightVerbs = new HashSet<String>();
        {
                StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.dstLightVerbs"),",");
                while(tok.hasMoreTokens())
                        dstLightVerbs.add(tok.nextToken().trim()+".");
                System.out.println(dstLightVerbs);
        }
        
        for (Iterator<Map.Entry<String, TObjectIntMap<String>>> iter = srcDstMapping.entrySet().iterator(); iter.hasNext();)
        {
                Map.Entry<String, TObjectIntMap<String>> entry = iter.next();
                
                for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
                {
                        tIter.advance();
                        if (tIter.value()==1 || dstLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
                                tIter.remove();
                }
                if (entry.getValue().isEmpty() || srcLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
                        iter.remove();
        }
        for (Iterator<Map.Entry<String, TObjectIntMap<String>>> iter = dstSrcMapping.entrySet().iterator(); iter.hasNext();)
        {
                Map.Entry<String, TObjectIntMap<String>> entry = iter.next();
                
                for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
                {
                        tIter.advance();
                        if (tIter.value()==1 || srcLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
                                tIter.remove();
                }
                if (entry.getValue().isEmpty() || dstLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
                        iter.remove();
        }

        for (Map.Entry<String, TObjectIntMap<String>> entry:srcDstMapping.entrySet())
        {
            System.out.print(entry.getKey().substring(0,entry.getKey().lastIndexOf('.'))+" ");
            for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
            {
                tIter.advance();
                System.out.print(tIter.key().substring(0,tIter.key().lastIndexOf('.'))+" ");
            }
            System.out.print("\n");
        }

//        aligner.collectStats();
        
        return sentencePairReader;
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
        Aligner aligner = new Aligner(Float.parseFloat(props.getProperty((args.length==1?"":args[1])+"align.threshold", "0.7")));
        
        gatherSentences(aligner, props, args.length==1?"":args[1]);
        /*
        SentencePairReader goldReader = gatherSentences(props, "ldcgold.").reader;
        SentencePairReader sysReader = gatherSentences(props, "ldcsys.").reader;
        gatherSentences(props, "");
        
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
        */
    }
}
