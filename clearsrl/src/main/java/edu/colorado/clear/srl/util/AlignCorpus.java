package edu.colorado.clear.srl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import edu.colorado.clear.common.propbank.DefaultPBTokenizer;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.propbank.PBUtil;
import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.TBUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.align.Aligner;
import edu.colorado.clear.srl.align.Alignment;
import edu.colorado.clear.srl.align.Sentence;
import edu.colorado.clear.srl.align.SentencePair;

public class AlignCorpus {
	private static Logger logger = Logger.getLogger("clearsrl");
	
	public static String[] readAlignment(File file) {
    	List<String> lines = new ArrayList<String>();
    	try (BufferedReader reader = new BufferedReader(new FileReader(file));) {
    		String line=null;
    		while ((line=reader.readLine())!=null)
    			lines.add(line.trim());
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	return lines.toArray(new String[lines.size()]);
    }
	
	static void readSentencePairs(Properties props, String corpus) throws Exception {
	
		props = PropertyUtil.filterProperties(props, corpus+'.', true);
		props = PropertyUtil.filterProperties(props, "align.", true);
		
		Aligner aligner = new Aligner(Float.parseFloat(props.getProperty("threshold")));

		BufferedReader idReader = new BufferedReader(new FileReader(props.getProperty("id.file"))); 
		
		Properties srcProps = PropertyUtil.filterProperties(props, "src.", true);
		Properties dstProps = PropertyUtil.filterProperties(props, "dst.", true);

		String srcParseDir = srcProps.getProperty("tb.dir");
		String srcPropDir = srcProps.getProperty("pb.dir");
		String dstParseDir = dstProps.getProperty("tb.dir");
		String dstPropDir = dstProps.getProperty("pb.dir");
		String waDir = props.getProperty("wa.dir");
		
		String htmlOutDir = props.getProperty("output.html.dir");
		
        String id = null;
        while ((id = idReader.readLine())!=null) {
        	id = id.trim();
        	
        	int cnt = 0;
        	
        	String srcParseName = srcProps.getProperty("tb.prefix","")+id+srcProps.getProperty("tb.suffix","");
        	String srcPropName = srcProps.getProperty("pb.prefix","")+id+srcProps.getProperty("pb.suffix","");
        	String dstParseName = dstProps.getProperty("tb.prefix","")+id+dstProps.getProperty("tb.suffix","");
        	String dstPropName = dstProps.getProperty("pb.prefix","")+id+dstProps.getProperty("pb.suffix","");
        	String waName = props.getProperty("wa.prefix","")+id+props.getProperty("wa.suffix","");
		
        	TBTree[] srcTrees = TBUtil.readTBFile(srcParseDir, srcParseName);
        	Map<String, TBTree[]> srcTB = new TreeMap<String, TBTree[]>();
        	srcTB.put(srcParseName, srcTrees);
        	SortedMap<Integer, List<PBInstance>> srcPB = 
        			PBUtil.readPBDir(Arrays.asList(new File(srcPropDir, srcPropName).getCanonicalPath()), new TBReader(srcTB),  new DefaultPBTokenizer()).values().iterator().next();
        	
        	TBTree[] dstTrees = TBUtil.readTBFile(dstParseDir, dstParseName);
        	Map<String, TBTree[]> dstTB = new TreeMap<String, TBTree[]>();
        	dstTB.put(dstParseName, dstTrees);
        	SortedMap<Integer, List<PBInstance>> dstPB = 
        			PBUtil.readPBDir(Arrays.asList(new File(dstPropDir, dstPropName).getCanonicalPath()), new TBReader(dstTB),  new DefaultPBTokenizer()).values().iterator().next();
        	
        	String[] wa = readAlignment(new File(waDir, waName));
        	
        	PrintStream htmlOut = new PrintStream(new File(htmlOutDir, id+".align.html"));
        	
        	Aligner.initAlignmentOutput(htmlOut);
        	
        	for (int i=0; i<srcTrees.length; ++i) {
        		SentencePair sp = new SentencePair(cnt++);
            	sp.src = Sentence.parseSentence(srcTrees[i], srcPB.get(i));
            	sp.dst = Sentence.parseSentence(dstTrees[i], dstPB.get(i));
            	sp.parseAlign(wa[i], false, true, false);
        		
        		Alignment[] al = aligner.align(sp);
        		Aligner.printAlignment(htmlOut, sp, al);
        	}
        	Aligner.finalizeAlignmentOutput(htmlOut);
        }
        idReader.close();
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

   
        readSentencePairs(props, args[1]);
        
    }
}
