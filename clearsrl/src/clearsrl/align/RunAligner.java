package clearsrl.align;

import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.treebank.TBNode;
import clearcommon.util.PropertyUtil;
import clearsrl.RunSRL;

public class RunAligner {
	
	private static Logger logger = Logger.getLogger("clearsrl");

    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
    
    @Option(name="-st",usage="source treebank file")
    private String srcTreeFile = null; 
    
    @Option(name="-sp",usage="source propbank file")
    private String srcPropFile = null; 
    
    @Option(name="-dt",usage="destination (translation) treebank file")
    private String dstTreeFile = null; 
    
    @Option(name="-dp",usage="destination (translation) propbank file")
    private String dstPropFile = null; 
    
    @Option(name="-wa",usage="word alignment file")
    private String alignmentFile = null; 
 
    @Option(name="-t",usage="threshold [0.0, 1.0]")
    private double threshold = -1; 
    
    @Option(name="-out",usage="output file/directory")
    private String outFile = null; 

    @Option(name="-filter",usage="filters the set of properties in the properties file")
    private String filter = ""; 
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
	public static void main(String[] args) throws IOException
	{	
		RunAligner options = new RunAligner();
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
		{
			FileInputStream in = new FileInputStream(options.propFile);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
			props = PropertyUtil.resolveEnvironmentVariables(props);
		}
		
		Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		
		props = PropertyUtil.filterProperties(props, options.filter+"align.");
		
		if (options.srcTreeFile != null) props.setProperty("src.tbfile", options.srcTreeFile);
		if (options.srcPropFile != null) props.setProperty("src.pbfile", options.srcPropFile);
		if (options.dstTreeFile != null) props.setProperty("dst.tbfile", options.dstTreeFile);
		if (options.dstPropFile != null) props.setProperty("dst.pbfile", options.dstPropFile);
		if (options.alignmentFile != null) props.setProperty("token_alignment", options.alignmentFile);
		if (options.threshold > 0) props.setProperty("threshold", Double.toString(options.threshold));
		if (options.outFile != null) props.setProperty("output.txt", options.outFile);
		
		logger.info(PropertyUtil.toString(props));
		
		SentencePairReader sentencePairReader = null;
		
		if (options.filter.startsWith("ldc"))
		{
			if (options.filter.startsWith("ldc09"))
				sentencePairReader = new LDC09SentencePairReader(props, false);
			else
				sentencePairReader = new LDCSentencePairReader(props, false);
		}
		else
		    sentencePairReader = new DefaultSentencePairReader(props, false);
		
		boolean alignPro = !props.getProperty("alignPro", "false").equals("false");
		
		Aligner aligner = new Aligner(sentencePairReader, Float.parseFloat(props.getProperty("threshold", "0.5")));
		
		//Scanner linesIdx = new Scanner(new BufferedReader(new FileReader(props.getProperty("train.all.lnum"))));
		//int lineIdx = linesIdx.nextInt();
		
		int lines = 0;
		
		int srcTokenCnt = 0;
		int dstTokenCnt = 0;
		
		String htmlOutfile = props.getProperty("output.html", null);
		
		if (htmlOutfile==null)
			htmlOutfile = "/dev/null";

		PrintStream alignmentStream;
		try {
		    alignmentStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(props.getProperty("output.txt", null))));
		} catch (Exception e) {
		    alignmentStream = System.out;
		}
		
		PrintStream htmlStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(htmlOutfile)));

		sentencePairReader.initialize();
		
		Aligner.initAlignmentOutput(htmlStream);
		AlignmentStat stat = new AlignmentStat();
		
		while (true)
		{
		    SentencePair sentencePair = sentencePairReader.nextPair();
		    if (sentencePair==null) break;

		    if (sentencePair.id%1000==999)
		    	logger.info(String.format("processing line %d",sentencePair.id+1));
		    
		    if (options.filter.startsWith("ldc09"))
		    {
		    	boolean skip = true;
		    	for (TBNode terminal:sentencePair.src.terminals)
		    		if (terminal.getWord().equals("*pro*"))
		    		{
		    			skip = false;
		    			break;
		    		}
		    	if (skip) continue;
		    }
		    
		    srcTokenCnt += sentencePair.srcAlignment.size();
            dstTokenCnt += sentencePair.dstAlignment.size();

		    //System.out.println("*****************");
		    //System.out.println(sentencePair);
		    
		    Alignment[] alignments = aligner.align(sentencePair);
	       
		    for (Alignment alignment:alignments)
		    {
		        alignmentStream.printf("%d,%s;[%s,%s]\n",sentencePair.id+1, alignment.toString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());
			//alignmentStream.printf("%d,%s;[%s,%s]\n",sentencePair.id+1, alignment.toArgTokenString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());

		        stat.addAlignment(alignment);
		    }
		    
		    Aligner.printAlignment(htmlStream, sentencePair, alignments, true);

            TObjectIntHashMap<String> tgtMap;
            for (int i=0; i<alignments.length; ++i)
            {
                //System.out.println("-----------------------------");
                //System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
                
                String srcRole = sentencePair.src.pbInstances[alignments[i].srcPBIdx].getRoleset();
                String dstRole = sentencePair.dst.pbInstances[alignments[i].dstPBIdx].getRoleset();

                // strip roleset id
                if (srcRole.lastIndexOf('.')>=0) 
                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.')+1);
                if (dstRole.lastIndexOf('.')>=0) 
                    dstRole = dstRole.substring(0,dstRole.lastIndexOf('.')+1);
                
                if ((tgtMap = srcDstMapping.get(srcRole))==null)
                {
                    tgtMap = new TObjectIntHashMap<String>();
                    srcDstMapping.put(srcRole, tgtMap);
                }
                tgtMap.put(dstRole, tgtMap.get(dstRole)+1);
                
                if ((tgtMap = dstSrcMapping.get(dstRole))==null)
                {
                    tgtMap = new TObjectIntHashMap<String>();
                    dstSrcMapping.put(dstRole, tgtMap);
                }
                tgtMap.put(srcRole, tgtMap.get(srcRole)+1);
            }
            /*
            if (chInstances.size()==0 || enInstances.size()==0)
                continue;
            
            float [][]simMatrix = new float[chInstances.size()>enInstances.size()?chInstances.size():enInstances.size()][];
            for (int i=0; i<simMatrix.length; ++i)
                simMatrix[i] = new float[simMatrix.length];
            
            for (int i=0; i<chInstances.size(); ++i)
                for (int j=0; j<enInstances.size(); ++j)
                    simMatrix[i][j] = align.measureSimiliarity(chInstances.get(i), enInstances.get(j), sentence);
            
            float [][]costMatrix = new float[simMatrix.length][];
            for (int i=0; i<costMatrix.length; ++i)
            {
                costMatrix[i] = new float[costMatrix.length];
                for (int j=0; j<costMatrix[i].length; ++j)
                    costMatrix[i][j] = Alignment.MAX_SIMILARITY-simMatrix[i][j];
            }
            HungarianAlgorithm.computeAssignments(costMatrix);
            */
            
		}
		sentencePairReader.close();
		Aligner.finalizeAlignmentOutput(htmlStream);
		if (alignmentStream!=System.out) alignmentStream.close();
		
		logger.info(String.format("lines: %d, src tokens: %d, dst tokens: %d\n",lines, srcTokenCnt, dstTokenCnt));
		//stat.printStats(System.out);
		//aligner.collectStats();
	}
}

