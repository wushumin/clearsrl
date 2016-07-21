package edu.colorado.clear.srl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import edu.colorado.clear.common.propbank.DefaultPBTokenizer;
import edu.colorado.clear.common.propbank.OntoNotesTokenizer;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.propbank.PBTokenizer;
import edu.colorado.clear.common.propbank.PBUtil;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.TBUtil;
import edu.colorado.clear.common.util.FileUtil;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.srl.ec.ECCommon;
import gnu.trove.map.TObjectIntMap;

public class Sentence implements Serializable{
	
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    static Logger logger = Logger.getLogger("clearsrl");
    
	public enum Source {
		TEXT("txt", false),
		TREEBANK("tb", true),
		TB_HEAD("tb.headed", true),
		PROPBANK("pb"),
		PREDICATE_LIST("pred"),
		PARSE("parse", true),
		PARSE_HEAD("parse.headed", true),
		PARSE_DEP("parse.dep"),
		AUTOPROP("prop"),
		SRL("srl"),
		EC_DEP("ecdep"),
		NAMED_ENTITY("ne");
		
		Source(String prefix) {
			this(prefix, false);
		}
		
		Source(String prefix, boolean isTree) {
			this.prefix = prefix;
			this.isTree = isTree;
		}
		
		public String prefix;
		public boolean isTree;
	}

	public String[] tokens;
	
	public TBTree treeTB;
	public List<PBInstance> propPB;
	
	public BitSet predicates;

	public TBTree parse;
	public List<SRInstance> srls;
	public List<PBInstance> props;
	public String[][] depEC;

	public String[] namedEntities;
	
	public Set<String> annotatedNominals = null;
	
	public Sentence(String[] tokens, TBTree treeTB, List<PBInstance> propPB, TBTree parse, List<SRInstance> srls, List<PBInstance> props, BitSet predicates, String[][] depEC, String[] namedEntities) {
		this.tokens = tokens;
		this.treeTB = treeTB;
		this.propPB = propPB;
		this.parse = parse;
		this.srls = srls;
		this.props = props;
		this.predicates = predicates;
		this.depEC = depEC;
		this.namedEntities = namedEntities;
	}
	
	public static EnumSet<Source> readSources(String input) {
		List<Source> srcs = new ArrayList<Source>();
		for (String srcStr:input.trim().split("\\s*,\\s*"))
			srcs.add(Source.valueOf(srcStr));
		return EnumSet.copyOf(srcs);
	}
	
	static final Pattern nePattern = Pattern.compile("<(/??[A-Z]{3,}?)>");
	static String[][] readNE(File file, TBTree[] trees) {
		List<String[]> neList = new ArrayList<String[]>();
		logger.info("Reading NE from "+file.getPath());
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			while ((line=reader.readLine())!=null) {
				String[] tokens = line.trim().split(" +");
				if (trees!=null) {
					if (neList.size()>=trees.length) {
						logger.severe("NE line length exceeded trees for "+file.getPath());
						break;
					}
					if (tokens.length!=trees[neList.size()].getTokenCount()) {
						logger.warning(String.format("NE mismatch found for %s:%d\n", file.getPath(), neList.size()));
						neList.add(null);
						break;
					}
				}
				String[] nes = new String[tokens.length];
				String currNE=null;
				for (int i=0; i<tokens.length; ++i) {
					Matcher matcher = nePattern.matcher(tokens[i]);
					while (matcher.find()) {
						String match = matcher.group(1);
						if (match.charAt(0)=='/')
							currNE = null;
						else
							nes[i] = currNE = match;
					}
					if (currNE!=null)
						nes[i] = currNE;
				}
				
				neList.add(nes);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return neList.toArray(new String[trees==null?neList.size():trees.length][]);
	}
	
	static Map<String, String[][]> readNamedEntities(Properties props, String prefix, Map<String, TBTree[]> treeMap) {
		Map<String, String[][]> neMap = new HashMap<String, String[][]>();
		String neDir = props.getProperty(prefix+".dir");
		String neRegex = props.getProperty(prefix+".regex");
		
		List<String> fileList = FileUtil.getFiles(new File(neDir), neRegex, false);
		for (String fName:fileList) {
			String key = fName.endsWith(".ner")?fName.substring(0,fName.length()-4)+".parse":fName;
			
			if (treeMap!=null && !treeMap.containsKey(key)) continue;

			neMap.put(key, readNE(new File(neDir, fName), treeMap==null?null:treeMap.get(key)));
		}
		return neMap;
	}
	
	static Map<String, List<BitSet>> readPredicates(Properties props, String prefix, Map<String, TBTree[]> treeMap) {
		Map<String, List<BitSet>> predMap = new HashMap<String, List<BitSet>>();
		String predDir = props.getProperty(prefix+".dir");
		String predRegex = props.getProperty(prefix+".regex");
		
		List<String> fileList = FileUtil.getFiles(new File(predDir), predRegex, false);
		for (String fName:fileList) {
			String key = fName.endsWith(".pred")?fName.substring(0,fName.length()-5)+".parse":fName;
			
			//System.out.println("reading predicates from "+fName+" "+key);
			//System.out.println(treeMap.keySet());
			if (treeMap!=null && !treeMap.containsKey(key)) continue;
			
			System.out.println("reading predicates from "+key);

			List<BitSet> predList = new ArrayList<BitSet>();
			
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(predDir, fName))))) {
				String line;
				while ((line=reader.readLine())!=null) {
					BitSet predicates = new BitSet();
					line = line.trim();
					if (!line.isEmpty())
						for (String token:line.split("\\s+"))
							predicates.set(Integer.parseInt(token));
					predList.add(predicates);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			predMap.put(key, predList);
		}
		return predMap;
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
	
	static Map<String, SortedMap<Integer, List<SRInstance>>> readSRLs(Properties props, String prefix, Map<String, TBTree[]> treeMap) {
		String propDir = props.getProperty(prefix+".dir");
		String filename = props.getProperty(prefix+".filelist");
		String propRegex = props.getProperty(prefix+".regex");
		
		List<String> fileList = filename==null?FileUtil.getFiles(new File(propDir), propRegex, true)
                :FileUtil.getFileList(new File(propDir), new File(filename), true);
		
		Map<String, SortedMap<Integer, List<SRInstance>>> srlMap = new HashMap<String, SortedMap<Integer, List<SRInstance>>>();
	
		for (String fName: fileList) {
        	try (ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(fName)))) {
        		Object obj;
                while ((obj = mIn.readObject())!=null) {
           		 
                	SRInstance instance = (SRInstance) obj;
                	
                	SortedMap<Integer, List<SRInstance>> instances = srlMap.get(instance.tree.getFilename());
                    if (instances == null) {
                        instances = new TreeMap<Integer, List<SRInstance>>();
                        srlMap.put(instance.tree.getFilename(), instances);
                    }
                    
                    List<SRInstance> instanceList = instances.get(instance.tree.getIndex());
                    if (instanceList == null) {
                        instanceList = new ArrayList<SRInstance>();
                        instances.put(instance.tree.getIndex(), instanceList);
                    }
                    instanceList.add(instance);
                }
        	} catch (Exception e) {
        		e.printStackTrace();
            }
        }

		for (Map.Entry<String, SortedMap<Integer, List<SRInstance>>> entry:srlMap.entrySet()) {
			TBTree[] trees = treeMap.get(entry.getKey());
			if (trees==null) continue;
			int lastId = -1;
			for (SortedMap.Entry<Integer, List<SRInstance>> treeEntry:entry.getValue().entrySet()) {
				if (treeEntry.getKey().intValue()==lastId)
					continue;
				lastId = treeEntry.getKey().intValue();
				
				trees[lastId].setRootNode(treeEntry.getValue().get(0).getTree().getRootNode());
			}
		}
		
		return null; //PBUtil.readPBDir(fileList, new TBReader(treeMap), tokenzier);
	}
			
	public static Map<String, Sentence[]> readCorpus(Properties props, Source headSource, EnumSet<Source> sources, LanguageUtil langUtil) {
		Map<String, Sentence[]> sentenceMap = new TreeMap<String, Sentence[]>();

		if (headSource!=Source.TEXT && !headSource.isTree) {
			Logger.getLogger("clearsrl").warning("head source is not a text or tree source!!!");
			return null;
		}
		
		String sourceDir = props.getProperty(headSource.prefix+".dir");
		String filename = props.getProperty(headSource.prefix+".filelist");
		String sourceRegex = props.getProperty(headSource.prefix+".regex");
		
		List<String> fileList = filename==null?FileUtil.getFiles(new File(sourceDir), sourceRegex, false)
                 :FileUtil.getFileList(new File(sourceDir), new File(filename), false);
		
		Map<String, TBTree[]> sourceMap = TBUtil.readTBDir(sourceDir, fileList, headSource.equals(Source.PARSE)||headSource.equals(Source.TREEBANK)?langUtil.getHeadRules():null);

		Map<String, TBTree[]> treeMap=null;
		if (sources.contains(Source.TREEBANK))
			treeMap = headSource.equals(Source.TREEBANK)?sourceMap:TBUtil.readTBDir(props.getProperty(Source.TREEBANK.prefix+".dir"), fileList, langUtil.getHeadRules());
		else if (sources.contains(Source.TB_HEAD))
			treeMap = headSource.equals(Source.TB_HEAD)?sourceMap:TBUtil.readTBDir(props.getProperty(Source.TB_HEAD.prefix+".dir"), fileList);
		
		Map<String, SortedMap<Integer, List<PBInstance>>> pbMap=null;
		if (sources.contains(Source.PROPBANK) && treeMap!=null)
			pbMap = readProps(props, Source.PROPBANK.prefix, treeMap);
		
		Map<String, TBTree[]> parseMap=null;
		if (sources.contains(Source.PARSE))
			parseMap = headSource.equals(Source.PARSE)?sourceMap:TBUtil.readTBDir(props.getProperty(Source.PARSE.prefix+".dir"), fileList, langUtil.getHeadRules());
		else if (sources.contains(Source.PARSE_HEAD))
			parseMap = headSource.equals(Source.PARSE_HEAD)?sourceMap:TBUtil.readTBDir(props.getProperty(Source.PARSE_HEAD.prefix+".dir"), fileList);
		
		if (sources.contains(Source.PARSE_DEP))
			TBUtil.addDependency(parseMap, new File(props.getProperty(Source.PARSE_DEP.prefix+".dir")), 
					Integer.parseInt(props.getProperty(Source.PARSE_DEP.prefix+".idxcol", "6")), 
					Integer.parseInt(props.getProperty(Source.PARSE_DEP.prefix+".labelcol", "7")));
		
		Map<String, SortedMap<Integer, List<SRInstance>>> srlMap=null;
		if (sources.contains(Source.SRL) && parseMap!=null)
			srlMap = readSRLs(props, Source.SRL.prefix, parseMap);
		
		Map<String, SortedMap<Integer, List<PBInstance>>> propMap=null;
		if (sources.contains(Source.AUTOPROP) && parseMap!=null)
			propMap = readProps(props, Source.AUTOPROP.prefix, parseMap);
		
		Map<String, Map<Integer, String[][]>> ecDepMap=null;
		if (sources.contains(Source.EC_DEP) && parseMap!=null)
			ecDepMap = ECCommon.readDepEC(new File(props.getProperty(Source.EC_DEP.prefix+".dir")), parseMap);
	
		Map<String, String[][]> neMap=null;
		if (sources.contains(Source.NAMED_ENTITY))
			neMap = readNamedEntities(props, Source.NAMED_ENTITY.prefix, parseMap);
		
		Map<String, List<BitSet>> predMap=null;
		if (sources.contains(Source.PREDICATE_LIST))
			predMap = readPredicates(props, Source.PREDICATE_LIST.prefix, parseMap);
		
		for (Map.Entry<String, TBTree[]> entry:sourceMap.entrySet()) {
			Sentence[] sentences = new Sentence[entry.getValue().length];
			sentenceMap.put(entry.getKey(), sentences);
			
			TBTree[] trees = treeMap==null?null:treeMap.get(entry.getKey());
			Map<Integer, List<PBInstance>> propPBs = pbMap==null?null:pbMap.get(entry.getKey());
			
			TBTree[] parses = parseMap==null?null:parseMap.get(entry.getKey());
			Map<Integer, List<SRInstance>> srls = srlMap==null?null:srlMap.get(entry.getKey());
			Map<Integer, List<PBInstance>> autoProps = propMap==null?null:propMap.get(entry.getKey());
			Map<Integer, String[][]> ecDeps = ecDepMap==null?null:ecDepMap.get(entry.getKey());
	
			String[][] namedEntities = neMap==null?null:neMap.get(entry.getKey());
			
			List<BitSet> predList = predMap==null?null:predMap.get(entry.getKey());
			
			for (int i=0; i<entry.getValue().length; ++i)
				sentences[i] = new Sentence(null,
						trees==null?null:trees[i], 
						!sources.contains(Source.PROPBANK)?null:(propPBs==null?new ArrayList<PBInstance>():(propPBs.get(i)==null?new ArrayList<PBInstance>():propPBs.get(i))), 
			            parses==null?null:parses[i], 
			            srls==null?null:srls.get(i),
					    autoProps==null?null:autoProps.get(i), 
					    predList==null?null:i<predList.size()?predList.get(i):new BitSet(),
				        ecDeps==null?null:ecDeps.get(i), 
				        namedEntities==null?null:namedEntities[i]);
		}	
		return sentenceMap;
	}
}
