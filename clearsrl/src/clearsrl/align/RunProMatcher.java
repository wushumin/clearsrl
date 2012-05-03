package clearsrl.align;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TLongArrayList;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

public class RunProMatcher {
	
	static final Set<String> UNIQUE_DET = new TreeSet<String>(Arrays.asList("the","this","that","these","those"));
	
	static class ProAlignment implements Comparable<ProAlignment>{
		public enum Type
		{
			PRO,
			PRONOUN,
			EXISTENTIAL,
			NP,
			PASSIVE,
			UNSPECIFIED
		}
		
		public int src;
		public int dst;
		public Type type;
		
		public ProAlignment(int src, int dst, Type type)
		{
			this.src = src;
			this.dst = dst;
			this.type = type;
		}

		public int compareTo(ProAlignment o) {
			if (src!=o.src)
				return src-o.src;
			if (dst!=o.dst)
				return dst-o.dst;
			return type.ordinal()-o.type.ordinal();
		}
		
		public long toLong()
		{
			return ((long)src)<<32|(long)dst;
		}
		
		public String toString()
		{
			if (dst<=0)
				return String.format("%d::%s", src, type.toString().toLowerCase());
			else
				return String.format("%d:%d:%s", src, dst, type.toString().toLowerCase());
		}
	}
	
	public static ProAlignment[] findProAlignments(SentencePair s, Alignment[] alignments, LanguageUtil srcLangUtil, LanguageUtil dstLangUtil)
	{
		TreeSet<ProAlignment> pAlignSet = new TreeSet<ProAlignment>();
		
		//TLongHashSet pAlignSet = new TLongHashSet();
		
		for (Alignment a:alignments)
		{
			PBInstance srcPB = s.src.pbInstances[a.srcPBIdx];
			PBInstance dstPB = s.dst.pbInstances[a.dstPBIdx];
			
			boolean ppMatch = false;
			boolean paMatch = false;
			boolean apMatch = false;
			
			for (ArgAlignmentPair p:a.getArgAlignmentPairs())
				if (a.getSrcPBArg(p.srcArgIdx).isPredicate())
				{
					if (a.getDstPBArg(p.dstArgIdx).isPredicate())
						ppMatch = true;
					paMatch = true;
				}
				else if (a.getDstPBArg(p.dstArgIdx).isPredicate())
					apMatch = true;

			
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
				if (!arg.getLabel().matches("ARG[01]")) continue;
				
				TBNode[] srcNodes = arg.getTerminalNodes();
				if (srcNodes.length!=1 || !srcNodes[0].getWord().equals("*pro*")) continue;
				
				TBNode proNode = srcNodes[0];			
				
				//found *pro* source argument
				for (PBArg dstArg:unAlignedDstArgs)
				{
					if (!dstArg.getLabel().matches("ARG[01]")) continue;

					// look for the syntactic subject first
					List<TBNode> terminals = dstArg.getAllNodes()[0].getTerminalNodes();
					if (terminals.size()==1 && (terminals.get(0).getWord().startsWith("*PRO")))
					{
						found = true;
						addIndex(pAlignSet, ProAlignment.Type.PRO, s, srcPB.getTree(), proNode, dstPB.getTree(), terminals.get(0));
						break;
					}
					
					TBNode[] tokens = dstArg.getTokenNodes();
					if (tokens.length==1 && tokens[0].getPOS().startsWith("PRP"))
					{
						found = true;
						addIndex(pAlignSet, ProAlignment.Type.PRONOUN, s, srcPB.getTree(), proNode, dstPB.getTree(), tokens[0]);
						break;
					}
					if (terminals.size()==1 && (terminals.get(0).isTrace()))
					{
						found = true;

						//System.out.println(s.id+" "+srcPB.toText(true));
						//System.out.println(s.id+" "+dstPB.toText(true));
						
						addIndex(pAlignSet, ProAlignment.Type.PRO, s, srcPB.getTree(), proNode, dstPB.getTree(), terminals.get(0));
						break;
					}
					
					
				}
						
				if (found) break;
				for (PBArg dstArg:dstEmptyArgs)
				{
					List<TBNode> terminals = dstArg.getNode().getTerminalNodes();
					
					if (!dstArg.getLabel().matches("ARG[01]")) continue;
					
					if (terminals.size()==1 && (terminals.get(0).getWord().startsWith("*PRO")||terminals.get(0).isTrace()))
					{
						found = true;
						addIndex(pAlignSet, ProAlignment.Type.PRO, s, srcPB.getTree(), proNode, dstPB.getTree(), terminals.get(0));
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
							addIndex(pAlignSet, ProAlignment.Type.EXISTENTIAL, s, srcPB.getTree(), proNode, dstPB.getTree(), tokens.get(0));
							found = true;
						}
					}
				}
				if (found) break;
				
				for (PBArg dstArg:unAlignedDstArgs)
				{
					if (!dstArg.getLabel().matches("ARG[01]")) continue;
					
					List<TBNode> tokens = dstArg.getNode().getTokenNodes();
					if (UNIQUE_DET.contains(tokens.get(0).getWord().toLowerCase()))
					{
						//TODO: add to alignment
						
						List<TBNode> tokenList = tokens.get(0).getParent().getTokenNodes();
						
						if (tokenList.size()>2) continue;
						
						if (tokenList.size()==2)
						{
							long t2 = SentencePair.makeLong(dstPB.getTree().getIndex(), tokenList.get(1).getTerminalIndex());
							int[] as=s.dstAlignment.get(t2);
							if (as!=null && as.length!=0)
							{
								//System.err.println(s.id+" "+srcPB.toText(true));
								//System.err.println(s.id+" "+dstPB.toText(true));
								continue;
							}
							//System.out.println(s.id+" "+srcPB.toText(true));
							//System.out.println(s.id+" "+dstPB.toText(true));
						}
						
						addIndex(pAlignSet, ProAlignment.Type.NP, s, srcPB.getTree(), proNode, dstPB.getTree(), tokens.get(0));

						break;
					}
				}

				
				if (!srcPassive && dstPassive)
				{
					// mark the node 						
					
					if (!ppMatch)
					{
						System.err.println(s.id+" "+srcPB.toText(true));
						System.err.println(s.id+" "+dstPB.toText(true));
						continue;
					}
					else
					{
						System.out.println(s.id+" "+srcPB.toText(true));
						System.out.println(s.id+" "+dstPB.toText(true));
					}
					
					addIndex(pAlignSet, ProAlignment.Type.PASSIVE, s, srcPB.getTree(), proNode, dstPB.getTree(), null);
					break;
				}
			}
				
		}
		
		BitSet srcSet = new BitSet();
		List<ProAlignment> aList = new ArrayList<ProAlignment>();
		for (ProAlignment a:pAlignSet)
		{
			if (srcSet.get(a.src)) continue;
			srcSet.set(a.src);
			aList.add(a);
		}
		
		return aList.toArray(new ProAlignment[aList.size()]);
	}
	
	static void addIndex(TreeSet<ProAlignment> longSet, ProAlignment.Type type, SentencePair s, TBTree srcTree, TBNode srcNode, TBTree dstTree, TBNode dstNode)
	{
		long srcTIndex = Sentence.makeIndex(srcTree.getIndex(), srcNode.getTerminalIndex());
		int srcPos = Arrays.binarySearch(s.src.terminalIndices, srcTIndex);
		
		if (dstNode==null && srcPos>=0)
		{
			longSet.add(new ProAlignment(srcPos+1, 0, type));
			return;
		}
		
		long dstTIndex = Sentence.makeIndex(dstTree.getIndex(), dstNode.getTerminalIndex());
		int dstPos = Arrays.binarySearch(s.dst.terminalIndices, dstTIndex);
		
		if (srcPos>=0 && dstPos>=0)
			longSet.add(new ProAlignment(srcPos+1, dstPos+1, type));
	}
	
	@SuppressWarnings("unchecked")
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
		
		System.err.print(PropertyUtil.toString(props));
		
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

		System.err.println("#****************************");
		
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

		TIntIntHashMap proTypeCnt = new TIntIntHashMap();
		
		int sentenceCnt = 0;
		int proCnt = 0;
		
		int argProCnt = 0;
		int mappedProCnt = 0;
		
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
*/
		    if (baseFilter.startsWith("ldc09"))
		    {
		    	srcTokenCnt +=sentencePair.src.tokens.length;
		    	dstTokenCnt +=sentencePair.dst.tokens.length;
		    	
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
		    
		    TLongHashSet argProSet = new TLongHashSet();
		    TLongHashSet mappedProSet = new TLongHashSet();
		    
		    for (PBInstance i:sentencePair.src.pbInstances)
		    	for (PBArg arg:i.getAllArgs())
		    	{
		    		if (arg.getNode().getTerminalNodes().size()==1 && arg.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
		    			argProSet.add(SentencePair.makeLong(i.getTree().getIndex(), (arg.getNode().getTerminalNodes().get(0).getTerminalIndex())));
		    		for (PBArg a:arg.getNestedArgs())
		    			if (a.getNode().getTerminalNodes().size()==1 && a.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
			    			argProSet.add(SentencePair.makeLong(i.getTree().getIndex(), (a.getNode().getTerminalNodes().get(0).getTerminalIndex())));
		    	}
		    
		    argProCnt += argProSet.size();
		    
		    
		    //System.out.println(sentencePair);
		    
		    /*
		    
		    Map<BitSet,BitSet> walign = sentencePair.getWordAlignmentSet(SentencePair.WordAlignmentType.UNION);
		    
		    Object[] wa = new Object[sentencePair.src.indices.length];
		    
		    for (Map.Entry<BitSet,BitSet> entry:walign.entrySet())
		    	wa[entry.getKey().nextSetBit(0)-1] = entry;
		    
		    System.out.print(sentencePair.id+" ");
		    
		    for (Object o:wa)
		    {
		    	if (o==null) continue;
		    	
		    	BitSet sSet = ((Map.Entry<BitSet,BitSet>)o).getKey();
		    	BitSet dSet = ((Map.Entry<BitSet,BitSet>)o).getValue();
		    	
		    	int sBit = sSet.nextSetBit(0);
		    	System.out.print(sentencePair.src.tokenToTerminalMap[sBit-1]+1);
		    	for (int i = sSet.nextSetBit(sBit+1); i>=0; i = sSet.nextSetBit(i+1))
		    		System.out.print(","+(sentencePair.src.tokenToTerminalMap[i-1]+1));
		    	System.out.print(":");
		    	
		    	int dBit = dSet.nextSetBit(0);
		    	System.out.print(sentencePair.dst.tokenToTerminalMap[dBit-1]+1);
		    	for (int i = dSet.nextSetBit(dBit+1); i>=0; i = dSet.nextSetBit(i+1))
		    		System.out.print(","+(sentencePair.dst.tokenToTerminalMap[i-1]+1));
		    	System.out.print(":unspec ");
		    }
		    System.out.println("");
		    */
		    
		    /*
		    System.out.println(sentencePair);
		    for (Map.Entry<BitSet,BitSet> entry:walign.entrySet())
		    	System.out.print(entry.getKey()+":"+entry.getValue()+" ");
		    System.out.println("");
		    */
		    
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
		    	PBInstance srcPB = sentencePair.src.pbInstances[alignment.srcPBIdx];
		    	
		    	for (PBArg arg:srcPB.getAllArgs())
		    	{
		    		if (arg.getNode().getTerminalNodes().size()==1 && arg.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
		    			mappedProSet.add(SentencePair.makeLong(srcPB.getTree().getIndex(), (arg.getNode().getTerminalNodes().get(0).getTerminalIndex())));
		    		for (PBArg a:arg.getNestedArgs())
		    			if (a.getNode().getTerminalNodes().size()==1 && a.getNode().getTerminalNodes().get(0).getWord().equals("*pro*"))
		    				mappedProSet.add(SentencePair.makeLong(srcPB.getTree().getIndex(), (a.getNode().getTerminalNodes().get(0).getTerminalIndex())));
		    	}
		    	
		        alignmentStream.printf("%d,%s;[%s,%s]\n",sentencePair.id+1, alignment.toString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());
			//alignmentStream.printf("%d,%s;[%s,%s]\n",sentencePair.id+1, alignment.toArgTokenString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());

		        stat.addAlignment(alignment);
		    }
		    mappedProCnt += mappedProSet.size();
		    
		    
		    ProAlignment[] proMatches = findProAlignments(sentencePair, alignments, srcLangUtil, dstLangUtil);
		    
		    for (ProAlignment a:proMatches)
		    	proTypeCnt.put(a.type.ordinal(), proTypeCnt.get(a.type.ordinal())+1);
		    
		    /*
		    System.out.print(sentencePair.id);
		    for (ProAlignment a:proMatches)
		    	System.out.print(" "+a);
		    System.out.print("\n");
		    */
		    long[] matches = new long[proMatches.length];
		    for (int i=0; i<matches.length; ++i)
		    	matches[i] = proMatches[i].toLong();
		    
		    Aligner.printAlignment(htmlStream, sentencePair, alignments, alignPro, matches);

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
		
		System.out.println("src tokens: "+srcTokenCnt);
		System.out.println("dst tokens: "+dstTokenCnt);
		System.out.println("pro sentences: "+sentenceCnt);
		System.out.println("pro count: "+proCnt);
		
		System.out.println("arg pro count: "+argProCnt);
		System.out.println("mapped pro count: "+mappedProCnt);
		for (ProAlignment.Type t:ProAlignment.Type.values())
			System.out.println(t+": "+proTypeCnt.get(t.ordinal()));
		
		
		
		//System.out.printf("sentences: %d, pros: %d\n", sentenceCnt, proCnt);
		
		//System.out.printf("src tokens: %d, dst tokens: %d\n", srcTokenCnt, dstTokenCnt);
		//stat.printStats(System.out);
		//aligner.collectStats();
		
	}

}
