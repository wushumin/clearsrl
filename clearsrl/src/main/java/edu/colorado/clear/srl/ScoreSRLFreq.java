package edu.colorado.clear.srl;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

import edu.colorado.clear.common.propbank.DefaultPBTokenizer;
import edu.colorado.clear.common.propbank.OntoNotesTokenizer;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.propbank.PBTokenizer;
import edu.colorado.clear.common.propbank.PBUtil;
import edu.colorado.clear.common.treebank.TBUtil;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.SRLScore.Type;
import edu.colorado.clear.srl.util.Topics;

public class ScoreSRLFreq {
	
	static TObjectIntMap<String> readFreq(String fname) throws IOException {
		TObjectIntMap<String> freq = new TObjectIntHashMap<String>();
		
		BufferedReader reader = new BufferedReader(new FileReader(fname));
		String line;
		while ((line=reader.readLine())!=null) {
			String[] tokens = line.trim().split("\\s+");
			if (tokens.length!=2) continue;
			freq.put(tokens[0], Integer.parseInt(tokens[1]));
		}
		reader.close();
		return freq;
	}
	
	static SRInstance filterInstance(PBInstance gold, PBInstance system, int[] intervals, int idx, boolean isArgFreq, TObjectIntMap<String> freqMap) {
		SRInstance filtered = new SRInstance(gold);
		for (Iterator<SRArg> iter=filtered.args.iterator(); iter.hasNext(); ) {
			SRArg arg = iter.next();
			String head = Topics.getTopicHeadword(arg.node, null);
            String label = arg.label;
            int freq = isArgFreq?freqMap.get(head+':'+label):freqMap.get(head);
            if (idx!=intervals.length-1 && (freq<intervals[idx] || freq>=intervals[idx+1]) || 
            		idx==intervals.length-1 && freq<intervals[idx])
            //if (idx!=freq && (idx<20||freq<20))
            	iter.remove();
		}		
		return filtered;
	}
	
	static SRInstance filterLabel(SRInstance gold, PBInstance system) {
		SRInstance sys = new SRInstance(system);
		
		Set<String> labels = new HashSet<String>();
		for (SRArg arg: gold.args)
			labels.add(arg.label);
		for (Iterator<SRArg> iter=sys.args.iterator(); iter.hasNext(); ) {
			SRArg arg = iter.next();
			if (!labels.contains(arg.label))
				iter.remove();
		}
		return sys;
	}
	
    public static void main(String[] args) throws Exception
    {   
    	int[] intervals = {0,1,5,11,21,41,91,201};
    	
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(args[0]);
        props.load(in);
        in.close();
        
        props = PropertyUtil.resolveEnvironmentVariables(props);

        props = PropertyUtil.filterProperties(props, "srl.", true);
        props = PropertyUtil.filterProperties(props, "score.", true);
        PBTokenizer goldTokenizer = props.getProperty("gold.pb.tokenizer")==null?(props.getProperty("gold.data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(props.getProperty("gold.pb.tokenizer")).newInstance();
     
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language").trim()+'.', true);
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps)) {
            System.err.println(String.format("Language utility (%s) initialization failed",props.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        TObjectIntMap<String> argMap = readFreq(props.getProperty("headcount.arg"));
        TObjectIntMap<String> allMap = readFreq(props.getProperty("headcount.all"));
        
        String system = props.getProperty("systems").trim();
       
        String[] labels = props.getProperty("labels").trim().split(",");
        
        for (int i=0; i< labels.length; ++i)
            labels[i] = labels[i].trim();

        SRLScore[] argScores = new SRLScore[intervals.length];
        SRLScore[] allScores = new SRLScore[intervals.length];
        SRLScore score = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        

        for (int i=0; i<argScores.length; ++i) {
        	argScores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        	allScores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        }

        Map<String, SortedMap<Integer, List<PBInstance>>>  goldPB = 
            PBUtil.readPBDir(props.getProperty("gold.pbdir"), 
                             props.getProperty("gold.pb.regex").trim(), 
                             props.getProperty("gold.tbdir"),
                             goldTokenizer);
        
        Map<String, SortedMap<Integer, List<PBInstance>>>  systemPB = PBUtil.readPBDir(props.getProperty(system+".pbdir"), 
                             props.getProperty(system+".pb.regex").trim(), 
                             props.getProperty(system+".tbdir"),
                             new DefaultPBTokenizer());
       
        for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:goldPB.entrySet()) {
            SortedMap<Integer, List<PBInstance>> goldMap = entry.getValue();
            SortedMap<Integer, List<PBInstance>> sysMap = systemPB.get(entry.getKey());
            if (sysMap==null) continue;
            
            for (Map.Entry<Integer, List<PBInstance>> e2:goldMap.entrySet())  {
                List<PBInstance> goldProps = e2.getValue();
                if (goldProps.isEmpty()) 
                	continue;
                TBUtil.linkHeads(goldProps.get(0).getTree(), langUtil.getHeadRules());

                List<PBInstance> sysProps = sysMap.get(e2.getKey());
                if (sysProps==null || sysProps.isEmpty())
                	continue;
                TBUtil.linkHeads(sysProps.get(0).getTree(), langUtil.getHeadRules());
                
                for (PBInstance goldProp:goldProps) {
                	if (goldProp.getRoleset().endsWith(".LV")) continue;
                	if (!goldProp.getPredicate().getPOS().startsWith("V")) continue;
                	PBInstance sysProp = null;
                    for (PBInstance prop:sysProps)
                        if (goldProp.getPredicate().getTokenIndex()==prop.getPredicate().getTokenIndex()) {
                        	sysProp = prop;
                            break;
                        }
                    if (sysProp==null) continue;
                	
                    for (int i=0; i<argScores.length; ++i) {
                    	SRInstance goldInstance = filterInstance(goldProp, sysProp, intervals, i, true, argMap);
                    	SRInstance sysInstance = filterLabel(goldInstance, sysProp);
                    	argScores[i].addResult(sysInstance, goldInstance, Type.ALL);
                    }

                    for (int i=0; i<allScores.length; ++i) {
                    	SRInstance goldInstance = filterInstance(goldProp, sysProp, intervals, i, false, allMap);
                    	SRInstance sysInstance = filterLabel(goldInstance, sysProp);
                    	allScores[i].addResult(sysInstance, goldInstance, Type.ALL);
                    }
                    
                    score.addResult(new SRInstance(sysProp), new SRInstance(goldProp), Type.ALL);
                }
            }
        }
        
        System.out.println("arg scores");
        for (int i=0; i<argScores.length; ++i)
            System.out.printf("%d %s", intervals[i], argScores[i].toSimpleString());
        
        System.out.println("\nall scores");
        for (int i=0; i<allScores.length; ++i)
            System.out.printf("%d %s", intervals[i], allScores[i].toSimpleString());

        System.out.println("\nnormal: "+score.toSimpleString());
        
    }
}
