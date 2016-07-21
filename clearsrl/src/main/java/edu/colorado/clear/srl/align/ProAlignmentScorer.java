package edu.colorado.clear.srl.align;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import edu.colorado.clear.common.propbank.PBArg;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.util.PropertyUtil;

public class ProAlignmentScorer {
    
    long pCount;
    long rCount;
    
    long pTotal;
    long rTotal;
    
    public ProAlignmentScorer() {
        pCount = 0;
        rCount = 0;
        pTotal = 0;
        rTotal = 0;
    }
    
    public void addAlignment(long[] gold, long[] sys)
    {
        rTotal += gold.length;
        pTotal += sys.length;
        
        TLongSet intersection = new TLongHashSet(gold);
        intersection.retainAll(sys);
        
        pCount += intersection.size();
        rCount += intersection.size();
    }
    
    static public void printAlignment(PrintStream out, long[] align)
    {
        Arrays.sort(align);
        for (long a:align)
            out.print((a>>>32)+"-"+((a<<32)>>>32)+" ");
        out.print("\n");
    }
    
    public void printStats(PrintStream out)
    {
        double p = pTotal==0?0:pCount*1.0/pTotal;
        double r = rTotal==0?0:rCount*1.0/rTotal;
        double f = p==0?0:(r==0?0:2*p*r/(p+r));
        out.printf("precision(%d): %.3f, recall(%d): %.3f, f-score: %.3f\n", (int)pTotal, p, (int)rTotal, r, f);
    }
    
    static Map<Integer, String[]> readAlignment(String fileName) throws IOException
    {
        Map<Integer, String[]> alignMap = new TreeMap<Integer, String[]>();
    
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        
        String line;
        while ((line = reader.readLine())!=null)
        {
            if (line.trim().isEmpty()) continue;
            
            String[] alignments = line.trim().split("\\s+");
            
            int id = Integer.parseInt(alignments[0]);
        
            if (alignments.length==1)
                alignMap.put(id, new String[0]);
            else
                alignMap.put(id, Arrays.copyOfRange(alignments, 1, alignments.length));         
        }
        
        //System.out.println(alignMap);
        return alignMap;
    }
    
    static long[] getProAlignment(BitSet srcSet, BitSet dstSet, String[] alignments)
    {
        TLongHashSet aSet = new TLongHashSet();
        
        //System.out.print(s.id);
        
        for (String a:alignments)
        {
            String[] tokens = a.split(":");
            if (tokens.length!=3) continue;
            
            String[] sIdStr = tokens[0].trim().isEmpty()?new String[0]:tokens[0].trim().split(",");
            String[] dIdStr = tokens[1].trim().isEmpty()?new String[0]:tokens[1].trim().split(",");
            
            if (sIdStr.length==0) continue;
            
            int[] sIds = new int[sIdStr.length];
            int[] dIds = new int[dIdStr.length];
            
            for (int i=0; i<sIds.length; ++i)
                sIds[i] = Integer.parseInt(sIdStr[i]);
            for (int i=0; i<dIds.length; ++i)
                dIds[i] = Integer.parseInt(dIdStr[i]);
            
            for (int sId:sIds)
            {
                if (!srcSet.get(sId-1)) continue;
                
                //if (!s.src.terminals[sId-1].getWord().equals("*pro*")) continue;
                for (int dId:dIds)
                {
                    if (!dstSet.get(dId-1)) continue;
                    aSet.add(SentencePair.makeLong(sId, dId));
                    //System.out.print(" "+sId+":"+dId);
                }
            }
            
        }
        //System.out.println("");
        return aSet.toArray();
    }
    
    public static void main(String[] args) throws Exception
    {           
        Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, StandardCharsets.UTF_8);
            props.load(iReader);
            iReader.close();
            in.close();
        }
        
        props = PropertyUtil.filterProperties(props, "ldc09gold.align.");
        SentencePairReader sentencePairReader = new LDC09SentencePairReader(props, false);
        sentencePairReader.initialize();
        Map<Integer, SentencePair> sentenceMap = new TreeMap<Integer, SentencePair>();
        
        Aligner aligner = new Aligner(Float.parseFloat(props.getProperty("threshold", "0.5")));
        
        SentencePair s;
        while ((s= sentencePairReader.nextPair())!=null)
        {
            //System.out.println(s.id);
            sentenceMap.put(s.id, s);
        }
        sentencePairReader.close();

        ProAlignmentScorer scoreraa = new ProAlignmentScorer();
        ProAlignmentScorer scorerap = new ProAlignmentScorer();
        ProAlignmentScorer scoreram = new ProAlignmentScorer();
        ProAlignmentScorer scorera = new ProAlignmentScorer();
        ProAlignmentScorer scorerp = new ProAlignmentScorer();
        ProAlignmentScorer scorerm = new ProAlignmentScorer();
        
        Map<Integer, String[]> goldAlignment = readAlignment(args[1]);
        Map<Integer, String[]> sysAlignment = readAlignment(args[2]);
        
        for (Map.Entry<Integer, String[]> goldEntry:goldAlignment.entrySet())
        {
            String[] sysVal = sysAlignment.get(goldEntry.getKey());
            if (sysVal==null) sysVal = new String[0];
            
            s = sentenceMap.get(goldEntry.getKey());
            
            Alignment[] alignments = aligner.align(s);
            
            BitSet aSet = new BitSet();
            BitSet pSet = new BitSet();
            BitSet mSet = new BitSet();

            BitSet daSet = new BitSet();
            BitSet dtSet = new BitSet();
            
            for (int i=0; i<s.src.terminals.length; ++i)
                if (s.src.terminals[i].getWord().equals("*pro*"))
                    aSet.set(i);
            
            for (int i=0; i<s.dst.terminals.length; ++i)
            {
                daSet.set(i);
                if (s.dst.terminals[i].isToken())
                    dtSet.set(i);
            }
            scoreraa.addAlignment(getProAlignment(aSet, daSet, goldEntry.getValue()),
                    getProAlignment(aSet, daSet, sysVal));
            scorera.addAlignment(getProAlignment(aSet, dtSet, goldEntry.getValue()),
                    getProAlignment(aSet, dtSet, sysVal));

            for (PBInstance i:s.src.pbInstances)
                for (PBArg arg:i.getAllArgs())
                {
                    if (arg.getNode().getTerminalNodes().size()==1 && arg.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
                        pSet.set(Arrays.binarySearch(s.src.terminalIndices, SentencePair.makeLong(i.getTree().getIndex(), (arg.getNode().getTerminalNodes().get(0).getTerminalIndex()))));
                    for (PBArg a:arg.getNestedArgs())
                        if (a.getNode().getTerminalNodes().size()==1 && a.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
                            pSet.set(Arrays.binarySearch(s.src.terminalIndices, SentencePair.makeLong(i.getTree().getIndex(), (a.getNode().getTerminalNodes().get(0).getTerminalIndex()))));
                }
            scorerap.addAlignment(getProAlignment(pSet, daSet, goldEntry.getValue()),
                    getProAlignment(pSet, daSet, sysVal));
            scorerp.addAlignment(getProAlignment(pSet, dtSet, goldEntry.getValue()),
                    getProAlignment(pSet, dtSet, sysVal));
            
            
            for (Alignment alignment:alignments)
            {
                PBInstance srcPB = s.src.pbInstances[alignment.srcPBIdx];
                
                for (PBArg arg:srcPB.getAllArgs())
                {
                    if (arg.getNode().getTerminalNodes().size()==1 && arg.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
                        mSet.set(Arrays.binarySearch(s.src.terminalIndices, SentencePair.makeLong(srcPB.getTree().getIndex(), (arg.getNode().getTerminalNodes().get(0).getTerminalIndex()))));
                    for (PBArg a:arg.getNestedArgs())
                        if (a.getNode().getTerminalNodes().size()==1 && a.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
                            mSet.set(Arrays.binarySearch(s.src.terminalIndices, SentencePair.makeLong(srcPB.getTree().getIndex(), (a.getNode().getTerminalNodes().get(0).getTerminalIndex()))));
                }
            }
            scoreram.addAlignment(getProAlignment(mSet, daSet, goldEntry.getValue()),
                    getProAlignment(mSet, daSet, sysVal));
            scorerm.addAlignment(getProAlignment(mSet, dtSet, goldEntry.getValue()),
                    getProAlignment(mSet, dtSet, sysVal));
            
            //scorer.addAlignment(getProAlignment(sentenceMap.get(goldEntry.getKey()), goldEntry.getValue()),
            //      getProAlignment(sentenceMap.get(goldEntry.getKey()), goldEntry.getValue()));
        }
        scoreraa.printStats(System.out);
        scorera.printStats(System.out);
        System.out.println("-----------");
        scorerap.printStats(System.out);
        scorerp.printStats(System.out);
        System.out.println("-----------");
        scoreram.printStats(System.out);
        scorerm.printStats(System.out);
    }
    
}
