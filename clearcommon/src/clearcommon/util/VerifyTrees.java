package clearcommon.util;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.PBFileReader;
import clearcommon.treebank.SerialTBFileReader;

public class VerifyTrees {
	
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    @Option(name="-dir",usage="input directory")
    private String treeDir = null; 
    
    @Option(name="-inList",usage="list of files in the input directory to process (overwrites regex)")
    private String inFileList = null; 
	
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
	public static void main(String[] args) throws Exception {
		
		VerifyTrees parser = new VerifyTrees();
        CmdLineParser cmdParser = new CmdLineParser(parser);
        
        try {
            cmdParser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("invalid options:"+e);
            cmdParser.printUsage(System.err);
            System.exit(0);
        }
        if (parser.help){
            cmdParser.printUsage(System.err);
            System.exit(0);
        }
        
        List<String> fileNames = FileUtil.getFileList(new File(parser.treeDir), new File(parser.inFileList));
        
        for (String fName:fileNames) {
        	System.out.println("Processing "+fName);
        	SerialTBFileReader tReader = new SerialTBFileReader(fName);
        	while (tReader.nextTree()!=null);
        	tReader.close();
        }
	}
}
