package edu.colorado.clear.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.TBUtil;

public class ExtractCorpusText {
    @Option(name="-in",usage="input parse directory")
    private File treeDir = null; 
    
    @Option(name="-out",usage="list of files in the input directory to process (overwrites regex)")
    private File txtDir = null; 

    @Option(name="-regex",usage="regular expression matching the files (default .*\\.parse)")
    private String regex = ".*\\.parse";
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    public static void main(String[] args) throws Exception {
        ExtractCorpusText options = new ExtractCorpusText();
        CmdLineParser cmdParser = new CmdLineParser(options);
        
        try {
            cmdParser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("invalid options:"+e);
            cmdParser.printUsage(System.err);
            System.exit(0);
        }
        if (options.help) {
            cmdParser.printUsage(System.err);
            System.exit(0);
        }

        if (!options.txtDir.exists()) 
            options.txtDir.mkdirs();
            
        if (!options.txtDir.isDirectory()) {
            System.err.println(options.txtDir.getPath()+" cannot be created!");
            return;
        }

        List<String> files = FileUtil.getFiles(options.treeDir, options.regex);
        
        for (String fName:files) {
            TBTree[] trees =  TBUtil.readTBFile(options.treeDir.getPath(), fName);
            
            if (trees==null) continue;
            
            if (fName.endsWith(".parse"))
                fName = fName.substring(0, fName.length()-5)+"txt";

            File file = new File(options.txtDir, fName);
            file.getParentFile().mkdirs();
            
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
            TBUtil.extractText(writer, trees);
            writer.close();
        }
    } 
}
