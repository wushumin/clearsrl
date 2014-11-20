package clearsrl.util;

import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBTokenizer;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.FileUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

public class MakeLDASamples {
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
	
    @Option(name="-parseDir",usage="input file/directory")
    private String inParseDir = null; 
    
    @Option(name="-propDir",usage="input file/directory")
    private String inPropDir = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-outDir",usage="output directory")
    private File outDir = null; 
    
    @Option(name="-prob",usage="argument probability")
	private double prob = -0.5; 
    
    @Option(name="-matchFrame",usage="use only predicates found in frame files")
    private boolean matchFrame = false;
    
    @Option(name="-wt",usage="term threshold")
    private double wCntThreshold = 10;
    
    @Option(name="-dt",usage="document threshold")
    private double docSizeThreshold = 25;
    
    @Option(name="-nominal",usage="include non-verb predicates")
    private boolean useNominal = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    static class PropFile implements Runnable  {

    	String fName;
    	MakeLDASamples options;
    	LanguageUtil langUtil;
    	Map<String, Map<String, TObjectDoubleMap<String>>> globalMap;
    	
    	public PropFile(String fName, MakeLDASamples options, LanguageUtil langUtil, Map<String, Map<String, TObjectDoubleMap<String>>> argMap) {
    		this.fName = fName;
    		this.options = options;
    		this.langUtil = langUtil;
    		this.globalMap = argMap;
    	}
    	
		@Override
        public void run() {
	        // TODO Auto-generated method stub
			
			logger.info("Processsing "+fName);
			
			Map<String, Map<String, TObjectDoubleMap<String>>> argMap = new HashMap<String, Map<String, TObjectDoubleMap<String>>>();
			
        	Map<String, SortedMap<Integer, List<PBInstance>>> pb = 
        			PBUtil.readPBDir(Arrays.asList(fName), new TBReader(options.inParseDir, true),  new DefaultPBTokenizer());

        	for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:pb.entrySet())
        		for (Map.Entry<Integer, List<PBInstance>> e2:entry.getValue().entrySet()) {
        			TBUtil.linkHeads(e2.getValue().get(0).getTree(), langUtil.getHeadRules());
        			for (PBInstance instance:e2.getValue()) {
        				if (!options.useNominal && !langUtil.isVerb(instance.getPredicate().getPOS())) 
        					continue;
        				if (options.matchFrame && langUtil.getFrame(instance.getPredicate())==null)
        					continue;
        				
        				String predicate = instance.getRoleset().substring(0,instance.getRoleset().lastIndexOf('.'));
        				Map<String, TObjectDoubleMap<String>> wordMap = argMap.get(predicate);
    					if (wordMap==null)
    						argMap.put(predicate, wordMap=new HashMap<String, TObjectDoubleMap<String>>());
        				
        				for (PBArg arg:instance.getArgs()) {
        					if (arg.getLabel().equals("rel"))
        						continue;
        					if (options.prob>=0 && options.prob>arg.getScore() || options.prob<0 && -options.prob>arg.getScore())
        						continue;
        					
        					String head = Topics.getTopicHeadword(arg.getNode());
        					if (head==null)
        						continue;
        					
        					TObjectDoubleMap<String> innerMap = wordMap.get(arg.getLabel());
        					if (innerMap==null)
        						wordMap.put(arg.getLabel(), innerMap=new TObjectDoubleHashMap<String>());
        					innerMap.adjustOrPutValue(head, options.prob>=0?1:arg.getScore(), options.prob>=0?1:arg.getScore());
        				}
        			}
        		}
        	synchronized (globalMap) {
        		for (Map.Entry<String, Map<String, TObjectDoubleMap<String>>> entry:argMap.entrySet()) {
        			Map<String, TObjectDoubleMap<String>> gMap = globalMap.get(entry.getKey());
        			if (gMap==null) {
        				globalMap.put(entry.getKey(), entry.getValue());
        				continue;
        			}
        			for (Map.Entry<String, TObjectDoubleMap<String>> e2:entry.getValue().entrySet()) {
        				TObjectDoubleMap<String> g2Map = gMap.get(e2.getKey());
        				if (g2Map==null) {
        					gMap.put(e2.getKey(),e2.getValue());
        					continue;
        				}
        				for (TObjectDoubleIterator<String> iter = e2.getValue().iterator(); iter.hasNext();) {
        					iter.advance();
        					g2Map.adjustOrPutValue(iter.key(), iter.value(), iter.value());
        				}
        			}
        		}
        	}
        	
        	logger.info("Finished processing "+fName+"\n");
        }
    }
    
    public static void makeArgOutput(Map<String, Map<String, TObjectDoubleMap<String>>> argMap, File outDir, double wCntThreshold, double docSizeThreshold) throws IOException {
    	if (!outDir.exists()) outDir.mkdirs();
    	
    	Map<String, String> dict = new HashMap<String, String>();
        
    	Map<String, TObjectDoubleMap<String>> allArgMap = new HashMap<String, TObjectDoubleMap<String>>();
    	
        for (Map.Entry<String, Map<String, TObjectDoubleMap<String>>> predEntry:argMap.entrySet()) {
        	TObjectDoubleMap<String> allArgCntMap = allArgMap.get(predEntry.getKey());
        	if (allArgCntMap==null)
        		allArgMap.put(predEntry.getKey(), allArgCntMap=new TObjectDoubleHashMap<String>());
        	
        	for (Map.Entry<String, TObjectDoubleMap<String>> argEntry:predEntry.getValue().entrySet())
        		for (TObjectDoubleIterator<String> tIter=argEntry.getValue().iterator(); tIter.hasNext();) {
	        		tIter.advance();
	        		allArgCntMap.adjustOrPutValue(argEntry.getKey()+':'+tIter.key(), tIter.value(), tIter.value());
	        	}
        	/*
        	for (Iterator<Map.Entry<String, TObjectDoubleMap<String>>>iter= wordMap.entrySet().iterator(); iter.hasNext();) {
        		Map.Entry<String, TObjectDoubleMap<String>> argEntry=iter.next();
        		TObjectDoubleMap<String> allArgCntMap = allArgMap.get(argEntry.getKey());
	        	if (allArgCntMap==null){
	        		allArgCntMap = new TObjectDoubleHashMap<String>();
	        		allArgMap.put(entry.getKey(), allArgCntMap);
	        	}
	        	for (TObjectDoubleIterator<String> tIter=entry.getValue().iterator(); tIter.hasNext();) {
	        		tIter.advance();
	        		allArgCntMap.adjustOrPutValue(aEntry.getKey()+':'+tIter.key(), tIter.value(), tIter.value());
	        	}
        	}
        		
/*        	TObjectDoubleMap<String> wCntMap = new TObjectDoubleHashMap<String>();
        	
	        logger.info(aEntry.getKey()+" pre-trim size: "+wordMap.size());
	        
	        for (Iterator<Map.Entry<String, TObjectDoubleMap<String>>>iter= wordMap.entrySet().iterator(); iter.hasNext();) {
	        	Map.Entry<String, TObjectDoubleMap<String>> entry=iter.next();
	        	for (TObjectDoubleIterator<String> tIter=entry.getValue().iterator(); tIter.hasNext();) {
	        		tIter.advance();
	        		wCntMap.adjustOrPutValue(tIter.key(), tIter.value(), tIter.value());
	        	}
	        }
	        PrintWriter wordWriter = new PrintWriter(new File(outDir, aEntry.getKey()+"-words.txt"));
	        for (TObjectDoubleIterator<String> tIter=wCntMap.iterator(); tIter.hasNext();) {
	        	tIter.advance();
	        	wordWriter.printf("%s %f\n", tIter.key(), tIter.value());
	        	if (tIter.value()<wCntThreshold)
	        		tIter.remove();
	        }
	        wordWriter.close();
	       
	        for (Iterator<Map.Entry<String, TObjectDoubleMap<String>>>iter= wordMap.entrySet().iterator(); iter.hasNext();) {
	        	int wCnt = 0;
	        	Map.Entry<String, TObjectDoubleMap<String>> entry=iter.next();
	        	
	        	for (TObjectDoubleIterator<String> tIter=entry.getValue().iterator(); tIter.hasNext();) {
	        		tIter.advance();
	        		
	        		if (wCntMap.containsKey(tIter.key()))
	        			wCnt+=tIter.value();
	        		else
	        			tIter.remove();
	        	}
	        	if (wCnt<docSizeThreshold)
	        		iter.remove();
	        }
   
	        logger.info(aEntry.getKey()+" post-trim size: "+wordMap.size());
	        
	        if (wordMap.size()<docSizeThreshold) continue;

	        PrintWriter docWriter = new PrintWriter(new File(outDir, aEntry.getKey()+"-docs.txt"));
            PrintWriter labelWriter = new PrintWriter(new File(outDir, aEntry.getKey()+"-labels.txt"));
	        
	        for (Map.Entry<String, TObjectDoubleMap<String>> entry:wordMap.entrySet()) {
	        	labelWriter.println(entry.getKey());
	        	String[] keys = entry.getValue().keys(new String[entry.getValue().size()]);
	        	for (String key:keys) {
	        		String val = dict.get(key);
	        		if (val==null) {
	        			val = "w"+dict.size();
	        			dict.put(key, val);
	        		}
	        		docWriter.print(val+" "+entry.getValue().get(key)+" ");
	        	}
	        	docWriter.print("\n");
	        }
	        docWriter.close();
	        labelWriter.close();
	        */
        }
        TObjectDoubleMap<String> wCntMap = new TObjectDoubleHashMap<String>();
        for (Map.Entry<String, TObjectDoubleMap<String>> entry:allArgMap.entrySet())
        	for (TObjectDoubleIterator<String> tIter=entry.getValue().iterator(); tIter.hasNext();) {
        		tIter.advance();
        		wCntMap.adjustOrPutValue(tIter.key(), tIter.value(), tIter.value());
        	}

        logger.info("all arg pre-trim entry size: "+wCntMap.size());
        for (TObjectDoubleIterator<String> tIter=wCntMap.iterator(); tIter.hasNext();) {
        	tIter.advance();
        	if (tIter.value()<wCntThreshold)
        		tIter.remove();
        }
        logger.info("all arg post-trim entry size: "+wCntMap.size());
        
        logger.info("all arg pre-trim doc size: "+allArgMap.size());
        for (Iterator<Map.Entry<String, TObjectDoubleMap<String>>>iter= allArgMap.entrySet().iterator(); iter.hasNext();) {
        	int wCnt = 0;
        	Map.Entry<String, TObjectDoubleMap<String>> entry=iter.next();
        	for (TObjectDoubleIterator<String> tIter=entry.getValue().iterator(); tIter.hasNext();) {
        		tIter.advance();
        		if (wCntMap.containsKey(tIter.key()))
        			wCnt+=tIter.value();
        		else
        			tIter.remove();
        	}
        	if (wCnt<docSizeThreshold)
        		iter.remove();
        }
        logger.info("all arg post-trim doc size: "+allArgMap.size());
        
        PrintWriter docWriter = new PrintWriter(new File(outDir, "ALLARG-docs.txt"));
        PrintWriter labelWriter = new PrintWriter(new File(outDir, "ALLARG-labels.txt"));
        Set<String> keySet = new HashSet<String>();
        
        for (Map.Entry<String, TObjectDoubleMap<String>> entry:allArgMap.entrySet()) {
        	labelWriter.println(entry.getKey());
        	String[] keys = entry.getValue().keys(new String[entry.getValue().size()]);
        	for (String key:keys) {
        		String word = key.substring(key.indexOf(':')+1);
        		String val = dict.get(word);
        		if (val==null) {
        			val = "w"+dict.size();
        			dict.put(word, val);
        		}
        		if (Math.round(entry.getValue().get(key))==0) continue;
        		docWriter.print(val+':'+key.substring(0,key.indexOf(':'))+" "+Math.round(entry.getValue().get(key))+" ");
        		keySet.add(word);
        	}
        	docWriter.print("\n");
        }
        docWriter.close();
        labelWriter.close();
        
        PrintWriter dictWriter = new PrintWriter(new File(outDir, "dict.txt"));
        for (String word:keySet)
        	dictWriter.println(word+' '+dict.get(word));
        dictWriter.close();

    }
    
    public static void main(String[] args) throws Exception {

    	Map<String, Map<String, TObjectDoubleMap<String>>> argMap = new HashMap<String, Map<String, TObjectDoubleMap<String>>>();
    	
    	MakeLDASamples options = new MakeLDASamples();
    	CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e)
        {
            System.err.println("invalid options:"+e);
            parser.printUsage(System.err);
            System.exit(0);
        }
        if (options.help){
            parser.printUsage(System.err);
            System.exit(0);
        }
    	
        Properties props = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(options.propFile), "UTF-8");
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language","chinese").trim()+'.', true);
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps)) {
            logger.severe(String.format("Language utility (%s) initialization failed",langProps.getProperty("util-class")));
            System.exit(-1);
        }
        
        if (!options.outDir.exists())
        	options.outDir.mkdirs();
        
        List<String> fileList = FileUtil.getFileList(new File(options.inPropDir), options.inFileList, true);
        
        ExecutorService executor = null;
        int threads = Integer.parseInt(props.getProperty("threads", "2"));
        if (threads>1) 
        	executor = Executors.newFixedThreadPool(threads);
        
        for (String fName:fileList) {
        	PropFile pf = new PropFile(fName, options, langUtil, argMap);
        	if (executor!=null)
	        	executor.execute(pf);
        	else
        		pf.run();
        }
        
        if (executor!=null) {
            executor.shutdown();
            while (true) {
                try {
                    if (executor.awaitTermination(5, TimeUnit.MINUTES)) break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        makeArgOutput(argMap, options.outDir, options.wCntThreshold, options.docSizeThreshold);
	}
}
