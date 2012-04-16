package clearsrl.align;

import gnu.trove.TLongHashSet;
import gnu.trove.TObjectIntHashMap;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

public class RunProMatcher {
	
	public static long[] findProAlignments(SentencePair s, Alignment[] alignments, LanguageUtil srcLangUtil, LanguageUtil dstLangUtil)
	{
		TLongHashSet pAlignSet = new TLongHashSet();
		
		for (Alignment a:alignments)
		{
			PBInstance srcPB = s.src.pbInstances[a.srcPBIdx];
			PBInstance dstPB = s.dst.pbInstances[a.dstPBIdx];
			
			boolean srcPassive = srcLangUtil.getPassive(srcPB.getPredicate())!=0;
			boolean dstPassive = dstLangUtil.getPassive(dstPB.getPredicate())!=0;
			
			PBArg[] srcEmptyArgs = srcPB.getEmptyArgs();
			PBArg[] dstEmptyArgs = dstPB.getEmptyArgs();
			
			List<PBArg> unAlignedDstArgs = new ArrayList<PBArg>();
			{
				BitSet dstArgAligned = new BitSet();
				
				for (ArgAlignmentPair argPair:a.getArgAlignmentPairs())
					dstArgAligned.set(argPair.dstArgIdx);
				
				for (int i=0; i<dstPB.getArgs().length; ++i)
					if (!dstArgAligned.get(i)) unAlignedDstArgs.add(dstPB.getArgs()[i]);
			}

			boolean found = false;
			for (PBArg arg:srcEmptyArgs)
			{
				if (!arg.getLabel().matches("ARG\\d")) continue;
				
				TBNode[] srcNodes = arg.getTerminalNodes();
				if (srcNodes.length!=1 || !srcNodes[0].getWord().equals("*pro*")) continue;
				
				TBNode proNode = srcNodes[0];			
				
				//found *pro* source argument
				for (PBArg dstArg:unAlignedDstArgs)
				{
					if (!dstArg.getLabel().matches("ARG\\d")) continue;

					// look for the syntactic subject first
					List<TBNode> terminals = dstArg.getAllNodes()[0].getTerminalNodes();
					if (terminals.size()==1 && terminals.get(0).getWord().startsWith("*PRO"))
					{
						found = true;
						addIndex(pAlignSet, s, srcPB.getTree(), proNode, dstPB.getTree(), terminals.get(0));
						break;
					}
					
					TBNode[] tokens = dstArg.getTokenNodes();
					if (tokens.length==1 && tokens[0].getPOS().startsWith("PRP"))
					{
						found = true;
						addIndex(pAlignSet, s, srcPB.getTree(), proNode, dstPB.getTree(), tokens[0]);
						break;
					}
				}
						
				if (found) break;
				for (PBArg dstArg:dstEmptyArgs)
				{
					List<TBNode> terminals = dstArg.getNode().getTerminalNodes();
					if (terminals.size()==1 && terminals.get(0).getWord().startsWith("*PRO"))
					{
						found = true;
						addIndex(pAlignSet, s, srcPB.getTree(), proNode, dstPB.getTree(), terminals.get(0));
						break;
					}
				}
				
				// look for existential word
				if (!found)
				{
					TBNode[] nodes = TBUtil.findConstituents(dstPB.getPredicate());
					
					if (nodes[0]!=null)
					{
						List<TBNode> tokens = nodes[0].getTokenNodes();
						
						if (tokens.size()==1 && tokens.get(0).getPOS().equals("EX"))
						{
							addIndex(pAlignSet, s, srcPB.getTree(), proNode, dstPB.getTree(), tokens.get(0));
							found = true;
						}
					}
				}
				if (found) break;
				
				if (!srcPassive && dstPassive)
				{
					// mark the node 
					addIndex(pAlignSet, s, srcPB.getTree(), proNode, dstPB.getTree(), null);
					break;
				}
			}
				
		}
		
		return pAlignSet.toArray();
	}
	
	static void addIndex(TLongHashSet longSet, SentencePair s, TBTree srcTree, TBNode srcNode, TBTree dstTree, TBNode dstNode)
	{
		long srcTIndex = Sentence.makeIndex(srcTree.getIndex(), srcNode.getTerminalIndex());
		int srcPos = Arrays.binarySearch(s.src.terminalIndices, srcTIndex);
		
		if (dstNode==null && srcPos>=0)
		{
			longSet.add(Sentence.makeIndex(srcPos+1, 0));
			return;
		}
		
		long dstTIndex = Sentence.makeIndex(dstTree.getIndex(), dstNode.getTerminalIndex());
		int dstPos = Arrays.binarySearch(s.dst.terminalIndices, dstTIndex);
		
		if (srcPos>=0 && dstPos>=0)
			longSet.add(Sentence.makeIndex(srcPos+1, dstPos+1));
	}
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{			
		Properties props = new Properties();
		{
			FileInputStream in = new FileInputStream(args[0]);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		LanguageUtil srcLangUtil  = (LanguageUtil) Class.forName(props.getProperty("src.language.util-class")).newInstance();
		if (!srcLangUtil.init(PropertyUtil.filterProperties(props, "dst.")))
		    System.exit(-1);

		LanguageUtil dstLangUtil  = (LanguageUtil) Class.forName(props.getProperty("dst.language.util-class")).newInstance();
		if (!dstLangUtil.init(PropertyUtil.filterProperties(props, "dst.")))
		    System.exit(-1);
		
		Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		
		String baseFilter = args.length>1?args[1]:"";
		props = PropertyUtil.filterProperties(props, baseFilter+"align.");
		
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
		
		int sentenceCnt = 0;
		int proCnt = 0;
		
		while (true)
		{
		    SentencePair sentencePair = sentencePairReader.nextPair();
		    if (sentencePair==null) break;
		    /*
		    System.out.print(sentencePair.src.tbFile);
		    for (int i=0; i<sentencePair.src.indices.length; ++i)
		    {
		    	int treeIndex = (int)(sentencePair.src.indices[i]>>>32);
		    	int tokenIndex = sentencePair.src.tokens[i].getTokenIndex();
		    	System.out.printf(" %d-%d",treeIndex,tokenIndex);
		    }
		    System.out.print("\n");
		    
		    System.out.print(sentencePair.dst.tbFile);
		    for (int i=0; i<sentencePair.dst.indices.length; ++i)
		    {
		    	int treeIndex = (int)(sentencePair.dst.indices[i]>>>32);
		    	int tokenIndex = sentencePair.dst.tokens[i].getTokenIndex();
		    	System.out.printf(" %d-%d",treeIndex,tokenIndex);
		    }

		    System.out.print("\n");
		    
		    for (long a:sentencePair.getWordAlignment(SentencePair.WordAlignmentType.UNION))
    		{
		    	int src = (int)(a>>>32);
		    	int dst = (int)(a&0xffffffff);
		    	System.out.printf("%d-%d ",src,dst);
    		}
		    System.out.print("\n");
		    */
		    
		    System.out.print(sentencePair.src.tbFile);
		    for (long index:sentencePair.src.terminalIndices)
		    {
		    	int treeIndex = (int)(index>>>32);
		    	int terminalIndex = (int)(index&0xffffffff);
		    	System.out.printf(" %d-%d",treeIndex,terminalIndex);
		    }
		    System.out.print("\n");
		    
		    System.out.print(sentencePair.dst.tbFile);
		    for (long index:sentencePair.dst.terminalIndices)
		    {
		    	int treeIndex = (int)(index>>>32);
		    	int terminalIndex = (int)(index&0xffffffff);
		    	System.out.printf(" %d-%d",treeIndex,terminalIndex);
		    }		    System.out.print("\n");
		    
		    for (long a:sentencePair.getWordAlignment(SentencePair.WordAlignmentType.UNION))
    		{
		    	int src = (int)(a>>>32);
		    	int dst = (int)(a&0xffffffff);
		    	System.out.printf("%d-%d ",sentencePair.src.tokenToTerminalMap[src-1]+1,sentencePair.dst.tokenToTerminalMap[dst-1]+1);
    		}
		    System.out.print("\n");

		    if (baseFilter.startsWith("ldc09"))
		    {
		    	boolean skip = true;
		    	for (TBNode terminal:sentencePair.src.terminals)
		    		if (terminal.getWord().equals("*pro*"))
		    		{
		    			skip = false;
		    			++proCnt;
		    		}
		    	if (skip) continue;
		    }
		    
		    sentenceCnt++;
		    
		    //System.out.println(sentencePair.id);
		    //for (TBNode terminal:sentencePair.src.terminals)
		    //	System.out.print(" "+terminal.getWord());
		    //System.out.print("\n");
		    //for (TBNode terminal:sentencePair.dst.terminals)
		    //	System.out.print(" "+terminal.getWord());
		    //System.out.print("\n");
		    
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
		    
		    Aligner.printAlignment(htmlStream, sentencePair, alignments, alignPro, findProAlignments(sentencePair, alignments, srcLangUtil, dstLangUtil));

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
		
		System.out.printf("sentences: %d, pros: %d\n", sentenceCnt, proCnt);
		
		System.out.printf("src tokens: %d, dst tokens: %d\n", srcTokenCnt, dstTokenCnt);
		//stat.printStats(System.out);
		aligner.collectStats();
		
	}

}
