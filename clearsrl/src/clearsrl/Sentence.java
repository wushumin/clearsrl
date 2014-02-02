package clearsrl;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.OntoNotesTokenizer;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBTokenizer;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.FileUtil;
import clearsrl.ec.ECCommon;

public class Sentence implements Serializable{
	
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;

	public enum Source {
		TREEBANK,
		TB_DEP,
		PROPBANK,
		PARSE,
		PARSE_DEP,
		SRL,
		EC_DEP,
		NAMED_ENTITY
	}

	public TBTree treeTB;
	public List<PBInstance> propPB;
	
	public TBTree parse;
	public List<PBInstance> props;
	public String[][] depEC;


	public String[] namedEntities;
	
	public Sentence(TBTree treeTB, List<PBInstance> propPB, TBTree parse, List<PBInstance> props, String[][] depEC, String[] namedEntities) {
		this.treeTB = treeTB;
		this.propPB = propPB;
		this.parse = parse;
		this.props = props;
		this.depEC = depEC;
		this.namedEntities = namedEntities;
	}
	
	public static EnumSet<Source> readSources(String input) {
		List<Source> srcs = new ArrayList<Source>();
		for (String srcStr:input.trim().split("\\s*,\\s*"))
			srcs.add(Source.valueOf(srcStr));
		return EnumSet.copyOf(srcs);
	}
	
	static Map<String, SortedMap<Integer, List<PBInstance>>> readProps(Properties props, String prefix, Map<String, TBTree[]> treeMap) {
		String propDir = props.getProperty(prefix+".dir");
		String filename = props.getProperty(prefix+".filelist");
		String propRegex = props.getProperty(prefix+".regex");
		
		List<String> fileList = filename==null?FileUtil.getFiles(new File(propDir), propRegex, true)
                :FileUtil.getFileList(new File(propDir), new File(filename), true);
		
		PBTokenizer tokenzier = null;
		try {
	        tokenzier = props.getProperty(prefix+".tokenizer")==null
	        		?(props.getProperty("data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer())
	        		:(PBTokenizer)Class.forName(props.getProperty(prefix+".tokenizer")).newInstance();
        } catch (Exception e) {
	        e.printStackTrace();
	        return null;
        }
		return PBUtil.readPBDir(fileList, new TBReader(treeMap), tokenzier);
	}
			
	public static Map<String, Sentence[]> readCorpus(Properties props, Source headSource, EnumSet<Source> sources) {
		Map<String, Sentence[]> sentenceMap = new TreeMap<String, Sentence[]>();
		
		String propPrefix = headSource.equals(Source.PARSE)?"parse":"tb";
		
		String treeDir = props.getProperty(propPrefix+".dir");
		String filename = props.getProperty(propPrefix+".filelist");
		String treeRegex = props.getProperty(propPrefix+".regex");
		
		List<String> fileList = filename==null?FileUtil.getFiles(new File(treeDir), treeRegex, false)
                 :FileUtil.getFileList(new File(treeDir), new File(filename), false);
		
		Map<String, TBTree[]> sourceMap = TBUtil.readTBDir(treeDir, fileList);

		Map<String, TBTree[]> treeMap=null;
		if (sources.contains(Source.TREEBANK))
			treeMap = headSource.equals(Source.TREEBANK)?sourceMap:TBUtil.readTBDir(props.getProperty("tb.dir"), fileList);
		if (sources.contains(Source.TB_DEP) && treeMap!=null) 
			TBUtil.addDependency(treeMap, new File(props.getProperty("tb.dep.dir")), 8, 10);
		
		Map<String, SortedMap<Integer, List<PBInstance>>> pbMap=null;
		if (sources.contains(Source.PROPBANK) && treeMap!=null)
			pbMap = readProps(props, "pb", treeMap);
		
		Map<String, TBTree[]> parseMap=null;
		if (sources.contains(Source.PARSE))
			parseMap = headSource.equals(Source.PARSE)?sourceMap:TBUtil.readTBDir(props.getProperty("parse.dir"), fileList);
		if (sources.contains(Source.PARSE_DEP) && parseMap!=null) 
			TBUtil.addDependency(parseMap, new File(props.getProperty("parse.dep.dir")), 8, 10);
		
		Map<String, SortedMap<Integer, List<PBInstance>>> srlMap=null;
		if (sources.contains(Source.SRL) && parseMap!=null)
			srlMap = readProps(props, "prop", parseMap);
		
		Map<String, Map<Integer, String[][]>> ecDepMap=null;
		if (sources.contains(Source.EC_DEP) && parseMap!=null)
			ecDepMap = ECCommon.readDepEC(new File(props.getProperty("ecdep.dir")), parseMap);
		
		for (Map.Entry<String, TBTree[]> entry:sourceMap.entrySet()) {
			Sentence[] sentences = new Sentence[entry.getValue().length];
			sentenceMap.put(entry.getKey(), sentences);
			
			TBTree[] trees = treeMap==null?null:treeMap.get(entry.getKey());
			Map<Integer, List<PBInstance>> propPBs = pbMap==null?null:pbMap.get(entry.getKey());
			
			TBTree[] parses = parseMap==null?null:parseMap.get(entry.getKey());
			Map<Integer, List<PBInstance>> srls = srlMap==null?null:srlMap.get(entry.getKey());
			Map<Integer, String[][]> ecDeps = ecDepMap==null?null:ecDepMap.get(entry.getKey());
		
			for (int i=0; i<entry.getValue().length; ++i)
				sentences[i] = new Sentence(trees==null?null:trees[i], 
											!sources.contains(Source.PROPBANK)?null:(propPBs==null?new ArrayList<PBInstance>():(propPBs.get(i)==null?new ArrayList<PBInstance>():propPBs.get(i))), 
								            parses==null?null:parses[i], 
										    srls==null?null:srls.get(i), 
									        ecDeps==null?null:ecDeps.get(i), 
										    null);
		}	
		return sentenceMap;
	}
}
