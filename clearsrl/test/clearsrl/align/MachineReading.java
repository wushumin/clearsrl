package clearsrl.align;

import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.RunSRL;

public class MachineReading {
	
	@Option(name="-prop",usage="properties file")
    private File propFile = null; 
	
	@Option(name="-prefix",usage="prefix")
    private String prefix = "";
	
	@Option(name="-vnMember",usage="VerbNet member file")
    private String vnmFilename = null;
	
	@Option(name="-threshold",usage="cutoff threshold")
	private int threshold = 1;
    
    @Option(name="-printFull",usage="whether to print full example")
	private boolean printFull = false;
    
    @Option(name="-useWA",usage="whether to use word alignment")
	private boolean useWA = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
	
	static Map<String, String> readMonoVerbNet(String file) throws IOException
	{
		Map<String, String> verbMap = new HashMap<String, String>();
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line;
		while ((line = reader.readLine())!=null)
		{
			String [] tokens = line.trim().split(" ");
			if (tokens.length==2)
				verbMap.put(tokens[0].trim(), tokens[1].trim());
		}
		
		reader.close();
		return verbMap;
		
	}
	
	static Map<String, SortedMap<Long, String>> readVerbNetId(String file, Map<String, TBTree[]> treeBank) throws IOException
	{
		Map<String, SortedMap<Long, String>> idMap = new TreeMap<String, SortedMap<Long, String>>();
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		
		final String prefix = "on/english/annotations/parse/";
		
		String line;
		while ((line = reader.readLine())!=null)
		{
			String [] tokens = line.trim().split(";");
			String fName = tokens[0].substring(prefix.length());
			fName = fName.substring(0, fName.indexOf('.'))+".parse";
			
			TBTree[] trees = treeBank.get(fName);
			
			int treeId = Integer.parseInt(tokens[1]);
			int terminalId = Integer.parseInt(tokens[2]);
			
			int tokenId = trees[treeId].getRootNode().getNodeByTerminalIndex(terminalId).getTokenIndex();
			
			String verbNetId = tokens[6];
			
			if (!verbNetId.matches("\\d.*")) continue;
				//System.out.printf("%s:%d:%d %s\n", fName, treeId, terminalId, verbNetId);
			
			SortedMap<Long, String> innerMap = idMap.get(fName);
			if (innerMap==null)
			{
				innerMap = new TreeMap<Long, String>();
				idMap.put(fName, innerMap);
			}
			innerMap.put((((long)treeId)<<32)|tokenId, verbNetId);
		}
		
		reader.close();
		/*
		for (Map.Entry<String, SortedMap<Long, String>> entry:idMap.entrySet())
		{
			String fName = entry.getKey();
			for (Map.Entry<Long,String> e2:entry.getValue().entrySet())
			{
				int treeId = (int) (e2.getKey().longValue()>>>32);
				int terminalId = (int) (e2.getKey().longValue()&0xffffffff);
				System.out.printf("%s:%d:%d %s\n", fName, treeId, terminalId, e2.getValue());
			}
		}
		*/
		
		return idMap;
		
	}
	
	
	public static void main(String[] args) throws IOException
	{	
	    MachineReading options = new MachineReading();
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
		
		
		//boolean printFull = false;
		//boolean useWA = false;
		
		Properties props = new Properties();
		{
			FileInputStream in = new FileInputStream(options.propFile);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		
		Map<String, String> verbMap = readMonoVerbNet(options.vnmFilename);
		
		
		Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, Map<String, List<String>>> vnetMapping = new TreeMap<String, Map<String, List<String>>>();
		
		String baseFilter = options.prefix;
		if (!baseFilter.isEmpty())
			props = PropertyUtil.filterProperties(props, baseFilter,true);
		props = PropertyUtil.filterProperties(props, "align.");
		
		System.out.print(PropertyUtil.toString(props));
		
		SentencePairReader sentencePairReader = null;
		
		if (baseFilter.startsWith("ldc"))
		{
			if (baseFilter.startsWith("ldc09"))
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

		System.out.println("#****************************");
		
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
		
        TObjectIntHashMap<String> tgtMap;
		int aTotal = 0;
		while (true)
		{
		    SentencePair sentencePair = sentencePairReader.nextPair();
		    if (sentencePair==null) break;
		    sentencePair.mergeAlignment();
		    
		    for (PBInstance pb:sentencePair.dst.pbInstances)
		    {
		    	String fName = pb.getTree().getFilename();
		    	int treeId = pb.getTree().getIndex();
		    	int terminalId = pb.getPredicate().getTerminalIndex();
		    	int tokenId = pb.getPredicate().getTokenIndex();

		    	//SortedMap<Long, String> innerMap = idMap.get(fName);
		    	//if (innerMap==null) continue;
		    	//pb.setVerbnetId(innerMap.get((((long)treeId)<<32)|tokenId));
		    	pb.setVerbnetId(verbMap.get(pb.getRoleset().substring(0, pb.getRoleset().indexOf('.'))));
		    	
		    	/*
		    	if (pb.getVerbnetId()!=null)
		    		System.out.printf("%s:%d:%d %s %s\n", fName, treeId, terminalId, pb.getRoleset(), pb.getVerbnetId());
		    	else
		    		System.err.printf("%s:%d:%d %s\n", fName, treeId, terminalId, pb.getRoleset());
		    	*/
		    }
		    
		    
		    //if (sentencePair.id%1000==999)
		   // {
		    	System.out.println(sentencePair.id+1);
		    //}
		    
		    if (baseFilter.startsWith("ldc09"))
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

		    
		    if (options.useWA)
		    {
		    	
		    	PBInstance[] srcPBs = new PBInstance[sentencePair.src.tokens.length];
		    	
		    	for (PBInstance srcPB:sentencePair.src.pbInstances)
		    	{
		    		 for (int i=0; i<sentencePair.src.tokens.length; ++i)
		    		 {
		    			 if (srcPB.getPredicate() == sentencePair.src.tokens[i])
		    			 {
		    				 srcPBs[i] = srcPB;

		    				 break;
		    			 }
		    		 }
		    	}
		    	
		    	for (PBInstance dstPB:sentencePair.dst.pbInstances)
		    	{
		    		String vnId = dstPB.getVerbnetId();
	                if (vnId==null) continue;
	                
	                int[] WAs = sentencePair.dstAlignment.get(Sentence.makeIndex(dstPB.getTree().getIndex(), dstPB.getPredicate().getTerminalIndex()));

	                for (int wa:WAs)
	                	if (srcPBs[wa]!=null)
	                	{
			                Map<String, List<String>> contextMap = null;
			                if ((contextMap = vnetMapping.get(vnId))==null)
			                    vnetMapping.put(vnId,  contextMap = new TreeMap<String, List<String>>());
			                String srcRole = srcPBs[wa].getRoleset();
		    				 if (srcRole.lastIndexOf('.')>=0) 
		    					 srcRole = srcRole.substring(0,srcRole.lastIndexOf('.'));
			                
			                List<String> samples = null;
			                if ((samples = contextMap.get(srcRole))==null)
			                	contextMap.put(srcRole, samples=new ArrayList<String>());
			                samples.add(srcPBs[wa].toText());
			                
					    	break;
	                	}
		    	}
		    }

            for (Alignment a:alignments)
            {	
            	boolean ppMatch = false;
            	boolean paMatch = false;
            	boolean apMatch = false;
            	
                //System.out.println("-----------------------------");
                //System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
                
                String srcRole = sentencePair.src.pbInstances[a.srcPBIdx].getRoleset();
                String dstRole = sentencePair.dst.pbInstances[a.dstPBIdx].getRoleset();
                
    			for (ArgAlignmentPair p:a.getArgAlignmentPairs())
    				if (a.getSrcPBArg(p.srcArgIdx).isPredicate())
    				{
    					if (a.getDstPBArg(p.dstArgIdx).isPredicate())
    						ppMatch = true;
    					if (a.getDstPBArg(p.dstArgIdx).isMainArg())
    						paMatch = true;
    				}
    				else if (a.getDstPBArg(p.dstArgIdx).isPredicate()&& a.getSrcPBArg(p.srcArgIdx).isMainArg())
    					apMatch = true;
                
                
                // strip roleset id
                if (srcRole.lastIndexOf('.')>=0) 
                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.'));
                if (dstRole.lastIndexOf('.')>=0) 
                    dstRole = dstRole.substring(0,dstRole.lastIndexOf('.'));
                
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
                
                if (!options.useWA)
                {
                
	                String vnId = sentencePair.dst.pbInstances[a.dstPBIdx].getVerbnetId();
	                if (vnId==null) continue;
	                
	                aTotal++;
	                
	                //if (!ppMatch && (paMatch || apMatch)) continue;
	                if (!ppMatch) continue;
	
	                Map<String, List<String>> contextMap = null;
	                if ((contextMap = vnetMapping.get(vnId))==null)
	                    vnetMapping.put(vnId,  contextMap = new TreeMap<String, List<String>>());
	                
	                List<String> samples = null;
	                if ((samples = contextMap.get(srcRole))==null)
	                	contextMap.put(srcRole, samples=new ArrayList<String>());
	                samples.add(sentencePair.src.pbInstances[a.srcPBIdx].toText());
                }
                
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
		
		System.out.printf("lines: %d, src tokens: %d, dst tokens: %d\n",lines, srcTokenCnt, dstTokenCnt);
		stat.printStats(System.out);
		aligner.collectStats();
		
		// Get rid of singleton mapping and light verbs
		Set<String> srcLightVerbs = new HashSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("srcLightVerbs"),",");
			while(tok.hasMoreTokens())
				srcLightVerbs.add(tok.nextToken().trim()+".");
			//System.out.println(srcLightVerbs);
		}
		
		Set<String> dstLightVerbs = new HashSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("dstLightVerbs"),",");
			while(tok.hasMoreTokens())
				dstLightVerbs.add(tok.nextToken().trim()+".");
			//System.out.println(dstLightVerbs);
		}
		int iTotal = 0;
		for (Iterator<Map.Entry<String, Map<String, List<String>>>> iter = vnetMapping.entrySet().iterator(); iter.hasNext();)
		{
			Map.Entry<String, Map<String, List<String>>> entry = iter.next();

			for (Iterator<Map.Entry<String, List<String>>> i2 = entry.getValue().entrySet().iterator(); i2.hasNext();)
			{
				Map.Entry<String, List<String>> e2 = i2.next();
				if (e2.getValue().size()<=options.threshold || srcLightVerbs.contains(e2.getKey())) i2.remove();
			}

			if (entry.getValue().isEmpty()) iter.remove();
		}
		
		Set<String> allCVerbs = new HashSet<String>();
		Set<String> polyCVerbs = new HashSet<String>();
		
		for (Map.Entry<String, Map<String, List<String>>> entry:vnetMapping.entrySet())
		{
			System.out.println(entry.getKey());
			
			for (Map.Entry<String, List<String>> e2:entry.getValue().entrySet())
			{
				if (allCVerbs.contains(e2.getKey()))
					polyCVerbs.add(e2.getKey());
				else
					allCVerbs.add(e2.getKey());
				
				if (options.printFull)
				{
					for (int i=0; i<e2.getValue().size();++i)
						System.out.println("\t"+e2.getKey()+"."+i+" "+e2.getValue().get(i));
				}
				else
					System.out.println("\t"+e2.getKey()+" "+e2.getValue().size());

				iTotal+=e2.getValue().size();
			}
		}
		
		System.out.println("total instances: "+iTotal+"/"+aTotal);
		System.out.println("classes: "+vnetMapping.size());
		System.out.println("verbs: "+polyCVerbs.size()+"/"+allCVerbs.size());
		System.out.println(polyCVerbs);
		
		System.exit(0);

		TObjectIntHashMap<String> cntMap = new TObjectIntHashMap<String>();
		Map<String, String> chVerbId = new TreeMap<String, String>();
		
		for (Map.Entry<String, Map<String, List<String>>> entry:vnetMapping.entrySet())
			for (Map.Entry<String, List<String>> e2:entry.getValue().entrySet())
				if (cntMap.get(e2.getKey())<e2.getValue().size())
				{
					chVerbId.put(e2.getKey(), entry.getKey());
					cntMap.put(e2.getKey(), e2.getValue().size());
				}

		Map<String, TObjectIntHashMap<String>> vvnetMapping = new TreeMap<String, TObjectIntHashMap<String>>();

		
		sentencePairReader.initialize();
		
		while (true)
		{
		    SentencePair sentencePair = sentencePairReader.nextPair();
		    if (sentencePair==null) break;
		    
		    for (PBInstance pb:sentencePair.dst.pbInstances)
		    {
		    	String fName = pb.getTree().getFilename();
		    	int treeId = pb.getTree().getIndex();
		    	int terminalId = pb.getPredicate().getTerminalIndex();
		    	int tokenId = pb.getPredicate().getTokenIndex();
		    	//SortedMap<Long, String> innerMap = idMap.get(fName);
		    	//if (innerMap==null) continue;
		    	//pb.setVerbnetId(innerMap.get((((long)treeId)<<32)|tokenId));
		    	
		    	pb.setVerbnetId(verbMap.get(pb.getRoleset().substring(0, pb.getRoleset().indexOf('.'))));
		    	
		    	/*
		    	if (pb.getVerbnetId()!=null)
		    		System.out.printf("%s:%d:%d %s %s\n", fName, treeId, terminalId, pb.getRoleset(), pb.getVerbnetId());
		    	else
		    		System.err.printf("%s:%d:%d %s\n", fName, treeId, terminalId, pb.getRoleset());
		    	*/
		    }
		    
		    if (options.useWA)
		    {		    	
		    	PBInstance[] dstPBs = new PBInstance[sentencePair.dst.tokens.length];
		    	
		    	for (PBInstance dstPB:sentencePair.dst.pbInstances)
		    	{
		    		 for (int i=0; i<sentencePair.dst.tokens.length; ++i)
		    		 {
		    			 if (dstPB.getPredicate() == sentencePair.dst.tokens[i])
		    			 {
		    				 dstPBs[i] = dstPB;
		    				 break;
		    			 }
		    		 }
		    	}
		    	
		    	for (PBInstance srcPB:sentencePair.src.pbInstances)
		    	{
		    		String srcRole = srcPB.getRoleset();
		    		if (srcRole.lastIndexOf('.')>=0) 
	                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.'));
		    		
		    		String vnId1 = chVerbId.get(srcRole);
	                if (vnId1==null) continue;
	                
	                int[] WAs = sentencePair.srcAlignment.get(Sentence.makeIndex(srcPB.getTree().getIndex(), srcPB.getPredicate().getTerminalIndex()));

	                for (int wa:WAs)
	                	if (dstPBs[wa]!=null)
	                	{
	                		String vnId2 = dstPBs[wa].getVerbnetId();
	                		
	                		if (vnId2==null) continue;
	                		
	                		if ((tgtMap = vvnetMapping.get(vnId1))==null)
	                            vvnetMapping.put(vnId1,  tgtMap = new TObjectIntHashMap<String>());
	
	                        tgtMap.put(vnId2, tgtMap.get(vnId2)+1);

					    	break;
	                	}
		    	}
		    }

		    
		    Alignment[] alignments = aligner.align(sentencePair);
		    for (Alignment a:alignments)
		    {
            	boolean ppMatch = false;
            	boolean paMatch = false;
            	boolean apMatch = false;
            	
                //System.out.println("-----------------------------");
                //System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
                
                String srcRole = sentencePair.src.pbInstances[a.srcPBIdx].getRoleset();
                String dstRole = sentencePair.dst.pbInstances[a.dstPBIdx].getRoleset();
                
    			for (ArgAlignmentPair p:a.getArgAlignmentPairs())
    				if (a.getSrcPBArg(p.srcArgIdx).isPredicate())
    				{
    					if (a.getDstPBArg(p.dstArgIdx).isPredicate())
    						ppMatch = true;
    					if (a.getDstPBArg(p.dstArgIdx).isMainArg())
    						paMatch = true;
    				}
    				else if (a.getDstPBArg(p.dstArgIdx).isPredicate()&& a.getSrcPBArg(p.srcArgIdx).isMainArg())
    					apMatch = true;
                
    			if (!options.useWA)
    			{
	                
	                // strip roleset id
	                if (srcRole.lastIndexOf('.')>=0) 
	                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.'));
	                if (dstRole.lastIndexOf('.')>=0) 
	                    dstRole = dstRole.substring(0,dstRole.lastIndexOf('.'));
	                
	                String vnId1 = chVerbId.get(srcRole);
	                String vnId2 = sentencePair.dst.pbInstances[a.dstPBIdx].getVerbnetId();
	                
	                if (vnId1==null || vnId2==null) continue;
	                
	                //if (!ppMatch && (paMatch || apMatch)) continue;
	                if (!ppMatch) continue;
	                
	                if ((tgtMap = vvnetMapping.get(vnId1))==null)
	                    vvnetMapping.put(vnId1,  tgtMap = new TObjectIntHashMap<String>());
	
	                tgtMap.put(vnId2, tgtMap.get(vnId2)+1);
    			}
		    }
		    
		}
				
		sentencePairReader.close();
		
		int total = 0;
		int correct = 0;
		
		for (Map.Entry<String, TObjectIntHashMap<String>>entry:vvnetMapping.entrySet())
		{
			System.out.println(entry.getKey());
			for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
			{
				tIter.advance();
				total += tIter.value();
				if (tIter.key().startsWith(entry.getKey())) 
					correct+=tIter.value();
				System.out.println("\t"+tIter.key()+" "+tIter.value());
			}
		}
		System.out.println(correct+"/"+total+" "+vvnetMapping.size());
			
		System.exit(0);

		
		for (Iterator<Map.Entry<String, TObjectIntHashMap<String>>> iter = srcDstMapping.entrySet().iterator(); iter.hasNext();)
		{
			Map.Entry<String, TObjectIntHashMap<String>> entry = iter.next();
			
			for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
			{
				tIter.advance();
				if (tIter.value()==1 || dstLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
					tIter.remove();
			}
			if (entry.getValue().isEmpty() || srcLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				iter.remove();
		}
		for (Iterator<Map.Entry<String, TObjectIntHashMap<String>>> iter = dstSrcMapping.entrySet().iterator(); iter.hasNext();)
		{
			Map.Entry<String, TObjectIntHashMap<String>> entry = iter.next();
			
			for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
			{
				tIter.advance();
				if (tIter.value()==1 || srcLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
					tIter.remove();
			}
			if (entry.getValue().isEmpty() || dstLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				iter.remove();
		}

		
		Set<String> dstVerbs = new TreeSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.dstVerbs"),",");
			while(tok.hasMoreTokens())
				dstVerbs.add(tok.nextToken().trim()+".");
		}
		
		/*
		int idx =0;
		{
			int []cnt = new int[dstSrcMapping.size()];
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
				cnt[idx++] = -entry.getValue().size();
			Arrays.sort(cnt);
			idx = cnt.length>=60?-cnt[59]:-cnt[cnt.length-1];
		}
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (entry.getValue().size()>=idx)
				dstVerbs.add(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1));
		}

		if (dstVerbs.size()>50)
		{
			idx = 0;
			int []cnt = new int[dstSrcMapping.size()];
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
			{
				if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
					continue;
				
				for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					cnt[idx]-= iter.value();
				}
				++idx;
			}
			Arrays.sort(cnt);
			idx = cnt.length>=65?-cnt[64]:-cnt[cnt.length-1];
			
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
			{
				if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
					continue;
				
				int size = 0;
				TObjectIntIterator<String> iter=entry.getValue().iterator();
				while(iter.hasNext())
				{
					iter.advance();
					size += iter.value();
				}
				if (size<idx)
					dstVerbs.remove(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1));
			}
		}
*/	
		System.out.print(dstVerbs.size()+" [");
		for (String word:dstVerbs)
			System.out.print(word.substring(0,word.length()-1)+" ");
		System.out.println("]");


		Set<String> srcRoles = new TreeSet<String>();
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				continue;

			System.out.println(entry.getKey()+":");
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
				srcRoles.add(iter.key());
			}
		}		
		Set<String> dstVerbsMapped = new TreeSet<String>();
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			if (!srcRoles.contains(entry.getKey()))
				continue;
			//System.out.println(entry.getKey()+":");
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				dstVerbsMapped.add(iter.key().substring(0,iter.key().lastIndexOf('.')+1));

			}
		}
		dstVerbsMapped.removeAll(dstLightVerbs);
		
		System.out.print(dstVerbsMapped.size()+" [");
		for (String word:dstVerbsMapped)
			System.out.print(word.substring(0,word.length()-1)+" ");
		System.out.println("]");
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			if (srcRoles.contains(entry.getKey()))
				continue;

			System.out.println(entry.getKey()+":");
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
			}
		}
		
		Map<String, TObjectDoubleHashMap<String>> srcDstMap2 = new TreeMap<String, TObjectDoubleHashMap<String>>();
		Map<String, TObjectDoubleHashMap<String>> dstDstMap2 = new TreeMap<String, TObjectDoubleHashMap<String>>();
		
		for (String key:srcRoles)
			srcDstMap2.put(key, new TObjectDoubleHashMap<String>());
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			TObjectDoubleHashMap<String> map = srcDstMap2.get(entry.getKey());
			if (map==null) continue;
			double cnt = 0;
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				cnt += iter.value();
			}
			
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				map.put(iter.key(),iter.value()/cnt);
			}			
		}

		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				continue;
			TObjectDoubleHashMap<String> map = new TObjectDoubleHashMap<String>();
			dstDstMap2.put(entry.getKey(), map);
			
			for (TObjectIntIterator<String> sIter=entry.getValue().iterator();sIter.hasNext();)
			{
				sIter.advance();
				for (TObjectDoubleIterator<String> iter=srcDstMap2.get(sIter.key()).iterator();iter.hasNext();)
				{
					iter.advance();
					map.put(iter.key(),map.get(iter.key())+iter.value()*sIter.value());
				}
			}
		}
		{
			double []cnt = new double[dstDstMap2.size()];
			int idx=0; 
			for (Map.Entry<String, TObjectDoubleHashMap<String>> entry:dstDstMap2.entrySet())
			{
				for (TObjectDoubleIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					cnt[idx] = cnt[idx]>iter.value()?cnt[idx]:iter.value();
				}
				idx++;
			}
			idx=0;
			for (Map.Entry<String, TObjectDoubleHashMap<String>> entry:dstDstMap2.entrySet())
			{
				System.out.println(entry.getKey()+":");
				for (TObjectDoubleIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					if (iter.value() >= cnt[idx]*0.1)
						System.out.printf("\t%s %.3f\n", iter.key(), iter.value());
					else if (iter.value()>=2 && iter.value() >= cnt[idx]*0.05)
						System.out.printf("\t[%s %.3f]\n", iter.key(), iter.value());
				}
				idx++;
			}
		}
	}
}

