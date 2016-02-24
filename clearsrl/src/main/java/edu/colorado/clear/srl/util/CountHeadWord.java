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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
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

public class CountHeadWord {
	
	private static Logger logger = Logger.getLogger("clearsrl");
	
	@Option(name="-prop",usage="properties file")
	private File propFile = null; 
	
    @Option(name="-parseDir",usage="input file/directory")
    private String inParseDir = null; 
    
    @Option(name="-propDir",usage="input file/directory")
    private String inPropDir = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private File inFileList = null; 
    
    @Option(name="-outAll",usage="output File")
    private File outAll = null;
    
    @Option(name="-outArg",usage="output File")
    private File outArg = null;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;

    public static void main(String[] args) throws Exception {

    	TObjectIntMap<String> argMap = new TObjectIntHashMap<String>();
    	TObjectIntMap<String> allMap = new TObjectIntHashMap<String>();
    	
    	CountHeadWord options = new CountHeadWord();
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
        
        
        LanguageUtil langUtil = (LanguageUtil) Class.forName(props.getProperty("chinese.util-class")).newInstance();
        if (!langUtil.init(PropertyUtil.filterProperties(props,"chinese."))) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        List<String> fileList = FileUtil.getFileList(new File(options.inPropDir), options.inFileList, true);
        for (String fName:fileList) {
        	Map<String, SortedMap<Integer, List<PBInstance>>> pb = PBUtil.readPBDir(Arrays.asList(fName), new TBReader(options.inParseDir, true),  new DefaultPBTokenizer());
        	for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:pb.entrySet())
        		for (Map.Entry<Integer, List<PBInstance>> e2:entry.getValue().entrySet()) {
        			TBUtil.linkHeads(e2.getValue().get(0).getTree(), langUtil.getHeadRules());
        			for (PBInstance instance:e2.getValue()) {
        				if (!langUtil.isVerb(instance.getPredicate().getPOS())) 
        					continue;
        				for (PBArg arg:instance.getArgs()) {
        					if (arg.getLabel().equals("rel")) continue;
        					String head = Topics.getTopicHeadword(arg.getNode(), langUtil);
        					if (head==null)
        						continue;
        					argMap.adjustOrPutValue(head+':'+arg.getLabel(), 1, 1);
        					allMap.adjustOrPutValue(head, 1, 1);
        				}
        			}
        		}
        }
        if (options.outArg!=null) {
	        PrintWriter writer = new PrintWriter(options.outArg);
	        for (TObjectIntIterator<String> iter=argMap.iterator(); iter.hasNext();) {
	        	iter.advance();
	        	writer.printf("%s %d\n", iter.key(), iter.value());
	        }
	        writer.close();
        }
        
        if (options.outAll!=null) {
	        PrintWriter writer = new PrintWriter(options.outAll);
	        for (TObjectIntIterator<String> iter=allMap.iterator(); iter.hasNext();) {
	        	iter.advance();
	        	writer.printf("%s %d\n", iter.key(), iter.value());
	        }
	        writer.close();
        }
        
	}
}
