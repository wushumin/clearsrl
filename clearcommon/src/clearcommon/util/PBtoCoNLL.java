package clearcommon.util;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

public class PBtoCoNLL {
	
    @Option(name="-inTB",usage="Treebank input directory")
    private String inTBDir = null; 
    
    @Option(name="-TBregex",usage="Treebank regular expression match (default=.*\\.parse)")
    private String tbRegex = ".*\\.parse";
	
    @Option(name="-inPB",usage="Propbank input directory")
    private String inPBDir = null; 
    
    @Option(name="-PBregex",usage="PropBank regular expression match (default=.*\\.prop)")
    private String pbRegex = ".*\\.prop"; 
    
    @Option(name="-out",usage="CoNLL output directory")
    private String outDir = null;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    public static void main(String[] args) throws Exception {
    	PBtoCoNLL options = new PBtoCoNLL();
    	CmdLineParser cmdParser = new CmdLineParser(options);
        
        try {
            cmdParser.parseArgument(args);
            if (options.inTBDir==null || options.inPBDir==null || options.outDir==null)
            	options.help = true;
        } catch (CmdLineException e) {
            System.err.println("invalid options:"+e);
        } finally {
	        if (options.help){
	            cmdParser.printUsage(System.err);
	            System.exit(0);
	        }
        }
       
        Map<String, TBTree[]> treeBank =  TBUtil.readTBDir(options.inTBDir, options.tbRegex);
        Map<String, SortedMap<Integer, List<PBInstance>>> propBank = PBUtil.readPBDir(options.inPBDir, options.pbRegex, new TBReader(treeBank));
        
        for (Map.Entry<String, TBTree[]>entry:treeBank.entrySet()) {
        	System.out.println("processing "+entry.getKey());
        	
        	SortedMap<Integer, List<PBInstance>> propMap = propBank.get(entry.getKey());
        	
        	File outFile = new File(options.outDir, entry.getKey());
        	outFile.getParentFile().mkdirs();
        	
        	PrintWriter writer = new PrintWriter(outFile);
        	
        	for (TBTree tree:entry.getValue()) {
        		List<PBInstance> props = propMap==null?null:propMap.get(tree.getIndex());
        		writer.print(PBUtil.toCoNLLformat(tree, props));
        		writer.print('\n');
        	}
        	writer.close();
        }
    }
    
}
