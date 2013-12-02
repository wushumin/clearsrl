package clearsrl.align;

import clearcommon.alg.HungarianAlgorithm;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;

import gnu.trove.map.TIntDoubleMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;


/**
 * @author shumin
 *
 */
public class Aligner {
	
	public static final float MAX_SIMILARITY = 3.0f;

	private static final float BETA_SQR = 1.0f;

	float alignThreshold;
	
	SentencePairReader reader;
	
	public enum Method {
		DEFAULT,
		GIZA,
		ARGUMENTS
	};
	
	public Aligner(SentencePairReader reader){
		this(reader, 0.05f);
	}
	
	public Aligner(SentencePairReader reader, float threshold){
		this.reader = reader;
		this.alignThreshold = threshold;
	}
	
	/**
	 * Perform argument matching based alignment
	 * @param sentence input sentence pair to be aligned
	 * @return alignments
	 */
	public Alignment[] align(SentencePair sentence)
	{
		Alignment[][] alignMatrix = new Alignment[sentence.src.pbInstances.length][sentence.dst.pbInstances.length];
			
		for (int i=0; i<alignMatrix.length; ++i)
			for (int j=0; j<alignMatrix[i].length; ++j)
			{
				alignMatrix[i][j] = new Alignment(sentence, i, j, BETA_SQR);
				alignMatrix[i][j].computeSymmetricAlignment();
			}
		
		return align(sentence.id, sentence.src.pbInstances, sentence.dst.pbInstances, alignMatrix);
	}
	
	/**
	 * align src and dst predicates based on computed similarity
	 * @param id sentence id
	 * @param srcInstances array of src predicates
	 * @param dstInstances array of dst predicates
	 * @param simMatrix computed similarity measure
	 * @return the alignment array
	 */
	public Alignment[] align(int id, PBInstance[] srcInstances, PBInstance[] dstInstances, Alignment[][] alignMatrix)
	{		
		if (alignMatrix.length==0)
			return new Alignment[0];
		
		float [][]costMatrix = new float[srcInstances.length>dstInstances.length?srcInstances.length:dstInstances.length][];
		for (int i=0; i<costMatrix.length; ++i)
			costMatrix[i] = new float[costMatrix.length];
			
		for (int i=0; i<alignMatrix.length; ++i)
			for (int j=0; j<alignMatrix[i].length; ++j)
			{
				costMatrix[i][j] = MAX_SIMILARITY-alignMatrix[i][j].getCompositeScore();
				
				// don't align predicate only prop unless it's to another predicate only prop
				if (srcInstances[i].getAllArgs().length==1 ^ dstInstances[j].getAllArgs().length==1)
					costMatrix[i][j] = MAX_SIMILARITY;
			}
		int[][] alignIdx = HungarianAlgorithm.computeAssignments(costMatrix);
		
		int i=0;
		
		ArrayList<Alignment> alignments = new ArrayList<Alignment>();
		
		for (; i<dstInstances.length; ++i)
		{
			if (alignIdx[i][0]<alignMatrix.length && alignMatrix[alignIdx[i][0]][i].getCompositeScore()>=alignThreshold)
				alignments.add(alignMatrix[alignIdx[i][0]][i]);
		}
		
		Collections.sort(alignments);
		
		return alignments.toArray(new Alignment[alignments.size()]);
	}
		
	/**
	 * Perform predicate matching (using GIZA++) based alignment
	 * @param sentence input sentence pair to be aligned
	 * @return alignments
	 */
	public Alignment[] alignGIZA(SentencePair sentence)
	{
		ArrayList<Alignment> alignment = new ArrayList<Alignment>();
		
		for (int i=0; i<sentence.src.pbInstances.length;++i)
		{
			int[] indices = sentence.srcAlignment.get(Sentence.makeIndex(sentence.src.pbInstances[i].getTree().getIndex(), sentence.src.pbInstances[i].getPredicate().getTerminalIndex()));
			if (indices==null) 
			{
				System.out.printf("%d: %s, %d %s\n", sentence.src.pbInstances[i].getTree().getIndex(), sentence.src.pbInstances[i].getPredicate().getWord(), sentence.src.pbInstances[i].getPredicate().getTerminalIndex(), sentence.srcAlignment.toString());
				continue;
			}
			for (int j=0; j<sentence.dst.pbInstances.length;++j)
			{
				int dstIdx = Arrays.binarySearch(sentence.dst.indices, Sentence.makeIndex(sentence.dst.pbInstances[j].getTree().getIndex(), sentence.dst.pbInstances[j].getPredicate().getTerminalIndex()));
				if (Arrays.binarySearch(indices, dstIdx)>=0)
				{
					//System.out.printf("%s => %s\n", srcInstance.predicateNode.word, dstInstance.predicateNode.word);
					alignment.add(new Alignment(sentence, i, j, BETA_SQR));
					break;
				}
			}
		}
		return alignment.toArray(new Alignment[alignment.size()]);
	}

	/**
	 * Perform argument matching based alignment
	 * @param sentence input sentence pair to be aligned
	 * @return alignments
	 */
	/*
	public Alignment[] alignArg(SentencePair sentence)
	{	
		PBInstance[] srcInstances = sentence.src.pbInstances;
		PBInstance[] dstInstances = sentence.dst.pbInstances;
		
		if (srcInstances.length==0 || dstInstances.length==0)
			return new Alignment[0];
		
		float [][]simMatrix = new float[srcInstances.length][];
		
		for (int i=0; i<srcInstances.length; ++i)
		{
			simMatrix[i] = new float[dstInstances.length];
			for (int j=0; j<dstInstances.length; ++j)
				simMatrix[i][j] = measureSymmetricSimilarity(srcInstances[i], dstInstances[j], sentence, new ArrayList<String>());
		}
		
		float [][]costMatrix = new float[srcInstances.length>dstInstances.length?srcInstances.length:dstInstances.length][];
		for (int i=0; i<costMatrix.length; ++i)
			costMatrix[i] = new float[costMatrix.length];
			
		for (int i=0; i<simMatrix.length; ++i)
			for (int j=0; j<simMatrix[i].length; ++j)
				costMatrix[i][j] = MAX_SIMILARITY-simMatrix[i][j];
		
		int[][] alignIdx = HungarianAlgorithm.computeAssignments(costMatrix);
		
		int i=0;
		
		ArrayList<Alignment> alignment = new ArrayList<Alignment>();
		
		for (; i<dstInstances.length; ++i)
		{
			if (alignIdx[i][0]<simMatrix.length && simMatrix[alignIdx[i][0]][i]>=0.05f)
			{
				alignment.add(new Alignment(sentence, alignIdx[i][0], i+1, simMatrix[alignIdx[i][0]][i]));
				
				ArrayList<String> argPair = new ArrayList<String>();
				measureSymmetricSimilarity(srcInstances[alignIdx[i][0]], dstInstances[i], sentence, argPair);
				String predicatePair = srcInstances[alignIdx[i][0]].getRoleset()+" "+dstInstances[i].getRoleset();
				System.out.println(predicatePair+": "+argPair);
			}
		}
		
		return alignment.toArray(new Alignment[alignment.size()]);
	}
*/
	
	/**
	 * measures the argument alignment based on GIZA++
	 * @param arg the lhs argument
	 * @param treeIndex the tree index
	 * @param rhs rhs PropBank instance
	 * @param sentence the sentence pair
	 * @param isSrc whether the lhs is the src sentence or not
	 * @return the alignment score for each token of the lhs argument
	 */
	/*
	private float[] measureArgAlignment(PBArg arg, PBInstance rhs, int treeIndex, SentencePair sentence, boolean isSrc) 
	{
		TBNode[] lhsNodes = arg.getTokenNodes();
		TBNode[] rhsNodes = rhs.getTree().getRootNode().getTerminalNodes().toArray(new TBNode[0]);
		
		PBArg[] rhsArgs = rhs.getArgs();
		
		boolean isMainArgtype = arg.isLabel("rel") || arg.isLabel("ARG0") || arg.isLabel("ARG1");
		
		// start with predicate
		float[] scores = new float[lhsNodes.length];
		
		for (int i=0; i<lhsNodes.length; ++i)
		{
			float denom=0;
			float [] weight;
			
			int pIndex = (lhsNodes[i].getTerminalIndex()|(treeIndex<<16));
			
			TIntHashSet dstIndices = isSrc?sentence.srcAlignment.get(pIndex):sentence.dstAlignment.get(pIndex);
			//This is possible when part of argument is not part of the "sentence"
			if (dstIndices==null)
				continue;
			
			TIntIterator iter=dstIndices.iterator(); 
			while (iter.hasNext()) 
			{
				int idx = isSrc?sentence.dst.indices[iter.next()]:sentence.src.indices[iter.next()];
				
				// rare (ignore): when GIZA aligns word to a different tree
				if ((idx>>16) != rhs.getTree().getIndex())
					continue;

				// strip the tree index
				idx = (idx&0xffff);
				// add weight to denominator
				weight = isSrc?SimilarityPair.DST_POS_WEIGHT.get(rhsNodes[idx].getPOS()):SimilarityPair.SRC_POS_WEIGHT.get(rhsNodes[idx].getPOS());
				weight = weight==null ? SimilarityPair.DEFAULT_POS_WEIGHTS : weight;
				denom += weight[1];
				
				for (PBArg rhsArg : rhsArgs)
				{
					if (rhsArg.hasTerminal(idx))
					{
						if (rhsArg.isLabel(arg.getLabel()))
							scores[i] += 1.0f*weight[0];
						else if (isMainArgtype &&
								 (rhsArg.isLabel("rel") || rhsArg.isLabel("ARG0") || rhsArg.isLabel("ARG1")))
							scores[i] += 0.7f*weight[0];
						else 
							scores[i] += 0.4f*weight[0];
						break;
					}
				}
			}
			if (denom>0)
				scores[i]/=denom;
		}
		return scores;
	}
	*/	
	/**
	 * Measure similarity between a pair of predicates
	 * @param lhs
	 * @param rhs
	 * @param sentence
	 * @param isSrc
	 * @return
	 */
	/*
	private float measureSymmetricSimilarity(PBInstance src, PBInstance dst, SentencePair sentence, ArrayList<String> argPair) 
	{
		float simScore = 0;
		
		PBArg[] srcArgs = src.getArgs();
		PBArg[] dstArgs = dst.getArgs();
		
		float [][]costMatrix = new float[srcArgs.length>dstArgs.length?srcArgs.length:dstArgs.length][];
		for (int i=0; i<costMatrix.length; ++i)
			costMatrix[i] = new float[costMatrix.length];
		
		float[] srcWeight = computeArgWeight(src);
		float[] dstWeight = computeArgWeight(dst);

		float [][]simMatrix = new float[srcArgs.length][dstArgs.length];
		
		for (int i=0; i<simMatrix.length; ++i)
			for (int j=0; j<simMatrix[i].length; ++j)
			{
				float srcScore = measureArgAlignment(srcArgs[i], dstArgs[j], src.getTree().getIndex(), dst.getTree().getIndex(), sentence, true);
				float dstScore = measureArgAlignment(dstArgs[j], srcArgs[i], dst.getTree().getIndex(), src.getTree().getIndex(), sentence, false);
				if (dstWeight[j]==0)
					simMatrix[i][j] = srcScore;
				else
					simMatrix[i][j] = getFScore(srcScore, dstScore, SimilarityPair.BETA_SQR*(srcWeight[i]/dstWeight[j]));
			}
		
		for (int i=0; i<simMatrix.length; ++i)
			for (int j=0; j<simMatrix[i].length; ++j)
				costMatrix[i][j] = MAX_SIMILARITY-simMatrix[i][j];
		
		int[][] alignIdx = HungarianAlgorithm.computeAssignments(costMatrix);
		
		for (int i=0; i<dstArgs.length; ++i)
		{
			if (alignIdx[i][0]<simMatrix.length)
			{
				float weight = getFScore(srcWeight[alignIdx[i][0]], dstWeight[i],SimilarityPair.BETA_SQR);
				simScore += simMatrix[alignIdx[i][0]][i]*weight;
				if (simMatrix[alignIdx[i][0]][i]>0.0f)
					argPair.add(srcArgs[alignIdx[i][0]].getLabel()+" "+dstArgs[i].getLabel());
			}
		}
		return simScore;
	}
*/

	/**
	 * measures the argument alignment based on GIZA++
	 * @param arg the lhs argument
	 * @param index the tree index
	 * @param rhs rhs PropBank instance
	 * @param sentence the sentence pair
	 * @param isSrc whether the lhs is the src sentence or not
	 * @return the alignment score for each token of the lhs argument
	 */
	/*
	private float measureArgAlignment(PBArg lhs, PBArg rhs, int lhsTIdx, int rhsTIdx, SentencePair sentence, boolean isSrc) 
	{
		TBNode[] lhsNodes = lhs.getTokenNodes();
		TBNode[] rhsNodes = rhs.getTokenNodes();
						
		if (lhsNodes.length==0) return 0;
		
		// start with predicate
		float[] scores = new float[lhsNodes.length];
		
		for (int i=0; i<lhsNodes.length; ++i)
		{
			float denom=0;
			
			int pIndex = (lhsNodes[i].getTerminalIndex()|(lhsTIdx<<16));
			
			TIntHashSet dstIndices = isSrc?sentence.srcAlignment.get(pIndex):sentence.dstAlignment.get(pIndex);
			//This is possible when part of argument is not part of the "sentence"
			if (dstIndices==null)
				continue;
			
			TIntIterator iter=dstIndices.iterator(); 
			while (iter.hasNext()) 
			{
				int idx = isSrc?sentence.dst.indices[iter.next()]:sentence.src.indices[iter.next()];
				
				// rare (ignore): when GIZA aligns word to a different tree
				if ((idx>>16) != rhsTIdx)
					continue;

				// strip the tree index
				idx = (idx&0xffff);

				if (rhs.hasTerminal(idx))
					scores[i]++;
				++denom;
			}
			if (denom>0)
				scores[i]/=denom;
		}
		
		float score = 0;
		for (int i=0; i<scores.length; ++i)
			score += scores[i];
		
		return score/lhsNodes.length;
	}
*/
	
	public float getScore(ArrayList<SentencePair> sentences, TIntObjectMap<TIntDoubleMap> goldLabel, Method method)
	{
		TIntObjectMap<TIntDoubleMap> systemLabel = new TIntObjectHashMap<TIntDoubleMap>();
		TIntDoubleMap sMap;
		
		for (SentencePair sentence:sentences)
		{
			Alignment[] alignments=null;
			switch (method)
			{
			case DEFAULT:   alignments = align(sentence); break;
			case GIZA:      alignments = alignGIZA(sentence); break;
			//case ARGUMENTS: alignments = alignArg(sentence); break;
			}
			
			for (Alignment alignment:alignments)
			{
				if (alignment.srcPBIdx<0 || alignment.dstPBIdx<0 || alignment.getCompositeScore()<0.1f)
					continue;
				if ((sMap=systemLabel.get(sentence.id))==null)
				{
					sMap = new TIntDoubleHashMap();
					systemLabel.put(sentence.id, sMap);
				}
				sMap.put((alignment.srcPBIdx<<16)|alignment.dstPBIdx,alignment.getCompositeScore());
			}
		}
		
		float precision = Scorer.score(systemLabel, goldLabel);
		float recall = Scorer.score(goldLabel, systemLabel);
		float fmeasure = 2*precision*recall/(precision+recall);
		System.out.printf("precision: %.3f, recall: %.3f, f-measure: %.3f\n", precision, recall, fmeasure);
		return fmeasure;
	}
	
	protected void addScore(Map<String, float[]> all, Map<String, float[]> one)
	{
		float[] score;
		for (Map.Entry<String, float[]> entry : one.entrySet())
		{
			if ((score=all.get(entry.getKey()))==null)
			{
				all.put(entry.getKey(), entry.getValue());
				continue;
			}
			score[0]+=entry.getValue()[0];
			score[1]+=entry.getValue()[1];
		}
	}
	
	public static void initAlignmentOutput(PrintStream stream)
	{   
        stream.println("<html>\n<head>\n" +
                "<META http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"+
                "<script type=\"text/javascript\">\n"+
                "function toggleDiv(id)\n"+
                "{\n"+
                "  if (document.getElementById(id).style.display == \"block\")\n"+
                "  {\n"+
                "    document.getElementById(id).style.display = \"none\";\n"+
                "  }\n"+
                "  else\n"+
                "  {\n"+
                "    document.getElementById(id).style.display = \"block\";\n"+
                "  }\n"+
                "}\n"+
                "</script>\n</head>\n"+
                "<body><font size=\"+1\">\n");
        
        stream.println("Color table: ");
        for (Map.Entry<String, String> entry:argColorMap.entrySet())
        {
        	stream.print("<font style=\"BACKGROUND:");
        	stream.print(entry.getValue());
        	stream.print("\">");
        	stream.print(entry.getKey());
        	stream.print("</font> ");
        }
        stream.println("<br>");
	}
	
	public static void printAlignment(PrintStream stream, SentencePair sentencePair, Alignment[] alignments)
	{
		printAlignment(stream, sentencePair, alignments, false);
	}
	
	public static void printAlignment(PrintStream stream, SentencePair sentencePair, Alignment[] alignments, boolean printEC)
	{
		printAlignment(stream, sentencePair, alignments, printEC, new long[0]);
	}
	
	public static void printAlignment(PrintStream stream, SentencePair sentencePair, Alignment[] alignments, boolean printEC, long[] proAlignments)
	{
        stream.printf("<h3>%d</h3>\n", sentencePair.id);
        
        BitSet srcHighLight = new BitSet();
        BitSet dstHighLight = new BitSet();
        
        for (long a:proAlignments)
        {
        	int srcBit = (int)(a>>>32);
        	int dstBit = (int)(a&0xffffffff);
        	
        	if (srcBit>0 && dstBit>0) 
        	{
        		srcHighLight.set(srcBit-1);
        		dstHighLight.set(dstBit-1);
        	}
        }
        
        BitSet srcHighLight2 = new BitSet();
        
        for (long a:proAlignments)
        {
        	int srcBit = (int)(a>>>32);
        	int dstBit = (int)(a&0xffffffff);
        	
        	if (srcBit>0 && dstBit==0) 
        		srcHighLight2.set(srcBit-1);
        }
        
        TBNode[] nodes = printEC?sentencePair.src.terminals:sentencePair.src.tokens;
        for (int i=0; i<nodes.length; ++i)
        	if (srcHighLight.get(i))
        		stream.print(" <font style=\"BACKGROUND:FFFF00\">"+nodes[i].getWord()+"</font>");
        	else if (srcHighLight2.get(i))
        		stream.print(" <font style=\"BACKGROUND:FF0000\">"+nodes[i].getWord()+"</font>");
        	else
        		stream.print(" "+nodes[i].getWord());
        stream.println("<br>");
        
        nodes = printEC?sentencePair.dst.terminals:sentencePair.dst.tokens;
        for (int i=0; i<nodes.length; ++i)
        	if (dstHighLight.get(i))
        		stream.print(" <font style=\"BACKGROUND:FFFF00\">"+nodes[i].getWord()+"</font>");
        	else
        		stream.print(" "+nodes[i].getWord());
        stream.println("<br>");
        
        stream.printf("<!-- %s -->\n", sentencePair.getSrcAlignmentIndex());
        stream.printf("<!-- %s -->\n", sentencePair.getDstAlignmentIndex());

        stream.println("<br><font size=\"-1\">Source:\n<ol>");
        for (int i=0; i<sentencePair.src.pbInstances.length; ++i)
        {
            stream.println("<li> "+toHTMLPB(sentencePair.src.pbInstances[i], sentencePair.src, printEC));
            stream.printf("<!-- %s: %d,%d -->\n", sentencePair.src.pbInstances[i].getTree().getFilename(), sentencePair.src.pbInstances[i].getTree().getIndex(), sentencePair.src.pbInstances[i].getPredicate().getTerminalIndex());
            stream.printf("<!-- %s -->\n", sentencePair.src.pbInstances[i].toText(true));        
        }
        stream.println("</ol>");
        
        stream.println("Dest:\n<ol>");
        for (int i=0; i<sentencePair.dst.pbInstances.length; ++i)
        {
            stream.println("<li> "+toHTMLPB(sentencePair.dst.pbInstances[i], sentencePair.dst, printEC));
            stream.printf("<!-- %s: %d,%d -->\n", sentencePair.dst.pbInstances[i].getTree().getFilename(), sentencePair.dst.pbInstances[i].getTree().getIndex(), sentencePair.dst.pbInstances[i].getPredicate().getTerminalIndex());
            stream.printf("<!-- %s -->\n", sentencePair.dst.pbInstances[i].toText(true));        
        }
        stream.println("</ol></font>");
        
        stream.println("<input type=\"button\" value=\"system\" onclick=\"toggleDiv("+(sentencePair.id+1)+")\">\n"+
                "<div id="+(sentencePair.id+1)+" style=\"display:block;\">\n"+
                "<HR>");
        
        for (int i=0; i<alignments.length; ++i)
            stream.printf("<p> %s </p>\n", alignments[i].toString());

        stream.println("<HR></div>\n");
	}
	
	public static void finalizeAlignmentOutput(PrintStream stream)
	{
	    stream.println("<h3>end<h3>\n</font>\n</body></html>");
	    if (stream != System.out && stream != System.err)
	        stream.close();
	}
	
	/*
	public void reweighPOS(Map<String, float[]> goldPOSScore, Map<String, float[]> otherPOSScore, Map<String, float[]> tgtPOSWeight)
	{
		Map<String, float[]> posWeight = new TreeMap<String, float[]>();
		for (Map.Entry<String, float[]> entry : goldPOSScore.entrySet())
		{
			float[] gScore = entry.getValue();
			float[] oScore = otherPOSScore.get(entry.getKey());
			
			float a = gScore[0]/gScore[1];
			float b = oScore==null?0.0f:oScore[0]/oScore[1];
			float c = (float) (a<0.05f?0:b==0?5:Math.pow(a/b,1));
			
			if (gScore[1] >= 5)
			{
				float[] score = new float[3];
				score[0] = c;
				score[1] = a;
				score[2] = gScore[1];
				posWeight.put(entry.getKey(), score);
			}
			
			System.out.printf("%s: %f/%f, %f, %f, %d/%d\n", entry.getKey(), a, b, c, a*c, (int)gScore[1], oScore==null?0:(int)oScore[1]);
		}
		
		float weight=0.0f;
		float weightd=0.0f;
		float denom=0.0f;
		
		for (Map.Entry<String, float[]> entry : posWeight.entrySet())
		{
			if (entry.getValue()[0]==0.0f) continue;
			weight += entry.getValue()[0]*entry.getValue()[2];
			weightd += entry.getValue()[1]*entry.getValue()[2];
			denom += entry.getValue()[2];
		}
		weightd /= denom;
		weight /= denom;
		System.out.printf("Weight: %f\n",weightd);
		
		tgtPOSWeight.clear();
		for (Map.Entry<String, float[]> entry : posWeight.entrySet())
		{
			float[] val = new float[2];
			val[0] = entry.getValue()[0]/weight;
			//val[1] = entry.getValue()[1]*val[0]/weightd;
			val[1] = entry.getValue()[1]*val[0]/0.6f;
			System.out.printf("%s: %f %f\n", entry.getKey(), val[0], val[1]);
			SimilarityPair.SRC_POS_WEIGHT.put(entry.getKey(), val);
		}
		System.out.println("--------------------------");
	}*/
	/*
	public void train(ArrayList<SentencePair> sentences, TIntObjectHashMap<TIntDoubleHashMap> goldLabel)
	{
		float fmeasure = getScore(sentences, goldLabel, Method.DEFAULT);
		
		TIntDoubleHashMap goldMap;
		Map<String, float[]> goldSrcPOSScore = new TreeMap<String, float[]>();
		Map<String, float[]> goldDstPOSScore = new TreeMap<String, float[]>();
		
		Map<String, float[]> otherSrcPOSScore = new HashMap<String, float[]>();
		Map<String, float[]> otherDstPOSScore = new HashMap<String, float[]>();
		
		for (SentencePair sentence:sentences)
		{
			goldMap = goldLabel.get(sentence.id);
			
			SimilarityPair [][]simMatrix = computeSimilarity(sentence);
			
			if (goldMap==null) continue;
			
			for (TIntDoubleIterator iter=goldMap.iterator(); iter.hasNext();)
			{
				iter.advance();
				int srcIdx = (iter.key()>>16)-1;
				int dstIdx = (iter.key()&0xffff)-1;
				SimilarityPair sim = simMatrix[srcIdx][dstIdx];
				
				float baseScore=0.0f;
				int   baseIdx=0;
				boolean addScore=false;
				
				if (sim.dst.score>=0.1f)
				{
					for (int i=0;i<sentence.src.pbInstances.length;++i)

					{
						if (i==srcIdx) continue;
						if (simMatrix[i][dstIdx].dst.score>=sim.dst.score)
						{
							//System.out.printf("%f <=> %f, %d, %d\n", simMatrix[i][dstIdx].dst.score, sim.dst.score, i, srcIdx);
							//addScore(otherDstPOSScore, simMatrix[i][dstIdx].dst.posScore);
							//addScore = true;
						}
						else if (simMatrix[i][dstIdx].dst.score>baseScore)
						{
							baseScore = simMatrix[i][dstIdx].dst.score;
							baseIdx = i;
						}
					}
					if (baseScore > 0.0f)
					{
						System.out.printf("%f <=> %f, %d, %d\n", simMatrix[baseIdx][dstIdx].dst.score,
								sim.dst.score, baseIdx, srcIdx);
						addScore(otherDstPOSScore, simMatrix[baseIdx][dstIdx].dst.posScore);	
						addScore = true;
					}
					if (addScore) addScore(goldDstPOSScore, sim.dst.posScore);
				}
				
				baseScore=0.0f;
				addScore = false;
				if (sim.src.score>=0.1f)
				{
					for (int i=0;i<sentence.dst.pbInstances.length;++i)
					{
						if (i==dstIdx) continue;
						if (simMatrix[srcIdx][i].src.score>=sim.src.score)
						{
							//System.out.printf("%f <=> %f, %d, %d\n", simMatrix[srcIdx][i].src.score, sim.src.score, i, dstIdx);
							//addScore(otherSrcPOSScore, simMatrix[srcIdx][i].src.posScore);
							//addScore = true;
						}
						else if (simMatrix[srcIdx][i].src.score>baseScore)
						{
							baseScore = simMatrix[srcIdx][i].src.score;
							baseIdx = i;
						}
					}
					if (baseScore > 0.0f)
					{
						System.out.printf("%f <=> %f, %d, %d\n", simMatrix[srcIdx][baseIdx].src.score, sim.src.score, baseIdx, dstIdx);
						addScore(otherSrcPOSScore, simMatrix[srcIdx][baseIdx].src.posScore);
						addScore = true;
					}
					if (addScore) addScore(goldSrcPOSScore, sim.src.posScore);
				}
				
				System.out.println("--------------------------");
			}
		}
		
		reweighPOS(goldSrcPOSScore, otherSrcPOSScore, SimilarityPair.SRC_POS_WEIGHT);
		reweighPOS(goldDstPOSScore, otherDstPOSScore, SimilarityPair.DST_POS_WEIGHT);
	}
	*/
	class FileFilter implements FilenameFilter{

		String regex;
		
		public FileFilter(String filter)
		{
			regex = filter;
		}
		
		@Override
		public boolean accept(File arg0, String arg1) {
			return Pattern.matches(regex, arg1);
		}
	};
	
	static TreeMap<String,String> argColorMap = new TreeMap<String,String>();
	static
	{
		argColorMap.put("rel","#00ff00");
		argColorMap.put("ARG0", "#FFA07A");	
		argColorMap.put("ARG1", "#EE82EE");
		argColorMap.put("ARG2", "#00FFFF");
		argColorMap.put("ARG3", "#9090E0");
		argColorMap.put("ARG4", "#996666");
		argColorMap.put("ARG5", "#FFE8AA");
		argColorMap.put("ARGM", "#EEE8AA");
	}
	
	
	static void markNode(TBNode node, String[] preMarkup, String[] postMarkup, String color, boolean printEC)
	{
		List<TBNode> nodes = printEC?node.getTerminalNodes():node.getTokenNodes();
		preMarkup[nodes.get(0).getTerminalIndex()] = "<font style=\"BACKGROUND:"+color+"\">";
		postMarkup[nodes.get(nodes.size()-1).getTerminalIndex()] = "</font>";		
	}
	
	static void markArg(PBArg arg, String[] preMarkup, String[] postMarkup, String color, boolean printEC)
	{
		if (printEC)
			for (TBNode node:arg.getAllNodes())
				markNode(node, preMarkup, postMarkup, color, printEC);
		else
			markNode(arg.getNode(), preMarkup, postMarkup, color, printEC);
		
		for (PBArg narg:arg.getNestedArgs())
			markArg(narg, preMarkup, postMarkup, color, printEC);
	}
	
	public static String toHTMLPB(PBInstance instance, Sentence sentence, boolean printEC)
	{
		StringBuilder buffer = new StringBuilder();

		String[] preMarkup = new String[instance.getTree().getTerminalCount()];
		String[] postMarkup = new String[instance.getTree().getTerminalCount()];
		
		Arrays.fill(preMarkup, "");
		Arrays.fill(postMarkup, "");
		
		String color;
		for (PBArg arg : (printEC?instance.getAllArgs():instance.getArgs()))
		{
			if ((color=argColorMap.get(arg.getBaseLabel()))==null)
				color=argColorMap.get("ARGM");
			
			markArg(arg, preMarkup, postMarkup, color, printEC);

		}
		
		long[] indices = printEC?sentence.terminalIndices:sentence.indices;
		TBNode[] nodes = printEC?sentence.terminals:sentence.tokens;
		for (int i=0; i<indices.length; ++i)
		{
		    int treeIndex = Sentence.getTreeIndex(indices[i]);
		    int terminalIndex = Sentence.getTerminalIndex(indices[i]);
			if (treeIndex != instance.getTree().getIndex())
			{
				buffer.append(nodes[i].getWord());
				buffer.append(" ");
			}
			else
			{
				buffer.append(preMarkup[terminalIndex]);
				buffer.append(nodes[i].getWord());
				buffer.append(postMarkup[terminalIndex]);
				buffer.append(" ");
			}
		}
		
		return buffer.toString();
	}
	/*
	public static String toHTMLPB(PBInstance instance, Sentence sentence, boolean printEC)
	{
		StringBuilder buffer = new StringBuilder();

		String[] colors = new String[instance.getTree().getTerminalCount()];
		String color;
		for (PBArg arg : (printEC?instance.getAllArgs():instance.getArgs()))
		{
			if ((color=argColorMap.get(arg.getLabel()))==null)
				color=argColorMap.get("ARGM");
			for (TBNode node:(printEC?arg.getTerminalNodes():arg.getTokenNodes()))
				colors[node.getTerminalIndex()] = color;
		}
		
		long[] indices = printEC?sentence.terminalIndices:sentence.indices;
		TBNode[] nodes = printEC?sentence.terminals:sentence.tokens;
		for (int i=0; i<indices.length; ++i)
		{
		    int treeIndex = Sentence.getTreeIndex(indices[i]);
		    int terminalIndex = Sentence.getTerminalIndex(indices[i]);
			if (treeIndex != instance.getTree().getIndex() || 
					colors[terminalIndex]==null)
			{
				buffer.append(nodes[i].getWord());
				buffer.append(" ");
			}
			else
			{
				buffer.append("<font style=\"BACKGROUND:");
				buffer.append(colors[terminalIndex]);
				buffer.append("\">");
				buffer.append(nodes[i].getWord());
				buffer.append("</font> ");
			}
		}
		
		return buffer.toString();
	}
	*/
	
	public void collectStats()
	{
		try {
			reader.initialize();
		} catch (Exception e) {
			e.printStackTrace();
		}
		int srcCoverageCnt = 0;
        int dstCoverageCnt = 0;
        
        int alignedSrcCoverageCnt = 0;
        int alignedDstCoverageCnt = 0;
	        
		int srcPBInstanceCnt = 0;
		int dstPBInstanceCnt = 0;
		
		int alignedSrcPBInstanceCnt = 0;
		int alignedDstPBInstanceCnt = 0;
		
		int srcTokenCnt = 0;
		int dstTokenCnt = 0;
		
		int alignedSrcTokenCnt = 0;
		int alignedDstTokenCnt = 0;
		
        SortedMap<String, int[]> srcPBArgMap = new TreeMap<String, int[]>();
        SortedMap<String, int[]> dstPBArgMap = new TreeMap<String, int[]>();
        
		SentencePair sentencePair;
		while ((sentencePair=reader.nextPair())!=null)
		{
		    if (sentencePair.id<0) continue;
		    
		    srcCoverageCnt += sentencePair.src.tokens.length;
		    dstCoverageCnt += sentencePair.dst.tokens.length;
		    
		    BitSet srcCoverageBitset = new BitSet();
		    BitSet dstCoverageBitset = new BitSet();
		    
		    Alignment[] alignments = align(sentencePair);
		    
		    srcPBInstanceCnt += sentencePair.src.pbInstances.length;
		    dstPBInstanceCnt += sentencePair.dst.pbInstances.length;

		    alignedSrcPBInstanceCnt += alignments.length;
		    alignedDstPBInstanceCnt += alignments.length;

		    for (Alignment alignment:alignments)
		    {
		        for (PBArg arg:alignment.getSrcPBInstance().getArgs())
		        {
		            int[] cnt = srcPBArgMap.get(arg.getLabel());
		            if (cnt==null) srcPBArgMap.put(arg.getLabel(), cnt=new int[2]);
		            cnt[1]++;
		            
		            srcTokenCnt+= arg.getTokenNodes().length;
		        }
		        for (PBArg arg:alignment.getDstPBInstance().getArgs())
                {
                    int[] cnt = dstPBArgMap.get(arg.getLabel());
                    if (cnt==null)
                        dstPBArgMap.put(arg.getLabel(), cnt=new int[2]);
                    cnt[1]++;
                    
                    dstTokenCnt += arg.getTokenNodes().length;
                }
		        BitSet srcArgBitSet = new BitSet();
		        BitSet dstArgBitSet = new BitSet();
		        
		        for (ArgAlignmentPair pair:alignment.getArgAlignmentPairs())
		        {
		            srcArgBitSet.set(pair.srcArgIdx);
		            dstArgBitSet.set(pair.dstArgIdx);
		        }
		        
		        for (int i=srcArgBitSet.nextSetBit(0); i>=0; i=srcArgBitSet.nextSetBit(i+1))
		        {
		            srcPBArgMap.get(alignment.getSrcPBArg(i).getLabel())[0]++;
		            alignedSrcTokenCnt += alignment.getSrcPBArg(i).getTokenNodes().length;
		            srcCoverageBitset.or(alignment.getSrcPBArg(i).getTokenSet());
		        }
		        
		        for (int i=dstArgBitSet.nextSetBit(0); i>=0; i=dstArgBitSet.nextSetBit(i+1))
		        {
		            dstPBArgMap.get(alignment.getDstPBArg(i).getLabel())[0]++;
		            alignedDstTokenCnt += alignment.getDstPBArg(i).getTokenNodes().length;
		            dstCoverageBitset.or(alignment.getDstPBArg(i).getTokenSet());
		        }
		        
		    }
		    alignedSrcCoverageCnt += srcCoverageBitset.cardinality();
		    alignedDstCoverageCnt += dstCoverageBitset.cardinality();
		}
		System.out.print("\nSrc\n");
		System.out.printf("Props   : %.3f (%d/%d)\n", alignedSrcPBInstanceCnt*1.0/srcPBInstanceCnt, alignedSrcPBInstanceCnt, srcPBInstanceCnt);
		System.out.printf("Tokens  : %.3f (%d/%d)\n", alignedSrcTokenCnt*1.0/srcTokenCnt, alignedSrcTokenCnt, srcTokenCnt);
		System.out.printf("Coverage: %.3f (%d/%d)\n", alignedSrcCoverageCnt*1.0/srcCoverageCnt, alignedSrcCoverageCnt, srcCoverageCnt);
		for (Map.Entry<String,int[]> entry:srcPBArgMap.entrySet())
		    System.out.printf("%s: %.3f (%d/%d)\n", entry.getKey(), entry.getValue()[0]*1.0/entry.getValue()[1], entry.getValue()[0], entry.getValue()[1]);
		
		System.out.print("\nDst\n");
		System.out.printf("Props   : %.3f (%d/%d)\n", alignedDstPBInstanceCnt*1.0/dstPBInstanceCnt, alignedDstPBInstanceCnt, dstPBInstanceCnt);
		System.out.printf("Tokens  : %.3f (%d/%d)\n", alignedDstTokenCnt*1.0/dstTokenCnt, alignedDstTokenCnt, dstTokenCnt);
		System.out.printf("Coverage: %.3f (%d/%d)\n", alignedDstCoverageCnt*1.0/dstCoverageCnt, alignedDstCoverageCnt, dstCoverageCnt);
		for (Map.Entry<String,int[]> entry:dstPBArgMap.entrySet())
            System.out.printf("%s: %.3f (%d/%d)\n", entry.getKey(), entry.getValue()[0]*1.0/entry.getValue()[1], entry.getValue()[0], entry.getValue()[1]);
		
		
	}
	
}
