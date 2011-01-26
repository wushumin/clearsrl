package clearsrl.align;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;

import clearcommon.alg.HungarianAlgorithm;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;


/**
 * @author shumin
 *
 */
public class Aligner {
	
	public static final float MAX_SIMILARITY = 3.0f;

	public static final float ARG01FACTOR   = 3.0f;
	
	public Map<String, TIntObjectHashMap<List<PBInstance>>> srcPB;
	public Map<String, TIntObjectHashMap<List<PBInstance>>> dstPB;
	
	public Map<String, TBTree[]> srcTB;
	public Map<String, TBTree[]> dstTB;
	
	public Map<String, TObjectIntHashMap<String>> argMap;
	
	public enum Method {
		DEFAULT,
		GIZA,
		ARGUMENTS
	};
	
	public Aligner(){
		srcPB = new HashMap<String, TIntObjectHashMap<List<PBInstance>>>();
		dstPB = new HashMap<String, TIntObjectHashMap<List<PBInstance>>>();
		
		srcTB = new HashMap<String, TBTree[]>();
		dstTB = new HashMap<String, TBTree[]>();
		
		argMap = new TreeMap<String, TObjectIntHashMap<String>>();
	}

	public Aligner(Map<String, TBTree[]> srcTB, Map<String, TIntObjectHashMap<List<PBInstance>>> srcPB,
			Map<String, TBTree[]> dstTB, Map<String, TIntObjectHashMap<List<PBInstance>>> dstPB){
		this.srcTB = srcTB;
		this.srcPB = srcPB;
		
		this.dstTB = dstTB;
		this.dstPB = dstPB;
	}
		
	/**
	 * Perform argument matching based alignment
	 * @param sentence input sentence pair to be aligned
	 * @return alignments
	 */
	public Alignment[] align(SentencePair sentence)
	{
		SimilarityPair [][]simMatrix = computeSimilarity(sentence);
		
		return align(sentence.id, sentence.src.pbInstances, sentence.dst.pbInstances, simMatrix);
	}
	
	/**
	 * align src and dst predicates based on computed similarity
	 * @param id sentence id
	 * @param srcInstances array of src predicates
	 * @param dstInstances array of dst predicates
	 * @param simMatrix computed similarity measure
	 * @return the alignment array
	 */
	public Alignment[] align(int id, PBInstance[] srcInstances, PBInstance[] dstInstances, SimilarityPair [][]simMatrix)
	{		
		if (simMatrix==null)
			return new Alignment[0];
		
		float [][]costMatrix = new float[srcInstances.length>dstInstances.length?srcInstances.length:dstInstances.length][];
		for (int i=0; i<costMatrix.length; ++i)
			costMatrix[i] = new float[costMatrix.length];
			
		for (int i=0; i<simMatrix.length; ++i)
			for (int j=0; j<simMatrix[i].length; ++j)
				costMatrix[i][j] = MAX_SIMILARITY-simMatrix[i][j].getCompositeScore();
		
		int[][] alignIdx = HungarianAlgorithm.computeAssignments(costMatrix);
		
		int i=0;
		
		ArrayList<Alignment> alignment = new ArrayList<Alignment>();
		
		for (; i<dstInstances.length; ++i)
		{
			if (alignIdx[i][0]<simMatrix.length && simMatrix[alignIdx[i][0]][i].getCompositeScore()>=0.05f)
				alignment.add(new Alignment(id, alignIdx[i][0]+1, i+1, simMatrix[alignIdx[i][0]][i].getCompositeScore()));
		}
		
		return alignment.toArray(new Alignment[alignment.size()]);
	}
		
	/**
	 * Perform predicate matching (using GIZ++) based alignment
	 * @param sentence input sentence pair to be aligned
	 * @return alignments
	 */
	public Alignment[] alignGIZA(SentencePair sentence)
	{
		ArrayList<Alignment> alignment = new ArrayList<Alignment>();
		
		for (int i=0; i<sentence.src.pbInstances.length;++i)
		{
			int srcIdx = sentence.src.pbInstances[i].getPredicate().getTerminalIndex() | (sentence.src.pbInstances[i].getTree().getIndex()<<16);
			TIntHashSet idxSet = sentence.srcAlignment.get(srcIdx);
			if (idxSet==null) 
			{
				System.out.printf("%d: %s, %d %s\n", sentence.src.pbInstances[i].getTree().getIndex(), sentence.src.pbInstances[i].getPredicate().getWord(), sentence.src.pbInstances[i].getPredicate().getTerminalIndex(), sentence.srcAlignment.toString());
				continue;
			}
			for (int j=0; j<sentence.dst.pbInstances.length;++j)
			{
				int dstIdx = Arrays.binarySearch(sentence.dst.indices, sentence.dst.pbInstances[j].getPredicate().getTerminalIndex() | sentence.dst.pbInstances[j].getTree().getIndex()<<16);
				if (idxSet.contains(dstIdx))
				{
					//System.out.printf("%s => %s\n", srcInstance.predicateNode.word, dstInstance.predicateNode.word);
					alignment.add(new Alignment(sentence.id, i+1, j+1, 1f));
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
				alignment.add(new Alignment(sentence.id, alignIdx[i][0]+1, i+1, simMatrix[alignIdx[i][0]][i]));
				
				ArrayList<String> argPair = new ArrayList<String>();
				measureSymmetricSimilarity(srcInstances[alignIdx[i][0]], dstInstances[i], sentence, argPair);
				String predicatePair = srcInstances[alignIdx[i][0]].getRoleset()+" "+dstInstances[i].getRoleset();
				System.out.println(predicatePair+": "+argPair);
			}
		}
		
		return alignment.toArray(new Alignment[alignment.size()]);
	}

	
	/**
	 * Compute pairwise similarity measure between predicates
	 * @param sentence
	 * @return
	 */
	public SimilarityPair [][] computeSimilarity(SentencePair sentence)
	{
		if (sentence.src.pbInstances.length==0 || sentence.dst.pbInstances.length==0)
			return null;
		
		SimilarityPair [][]simMatrix = new SimilarityPair[sentence.src.pbInstances.length][];
		
		for (int i=0; i<sentence.src.pbInstances.length; ++i)
		{
			simMatrix[i] = new SimilarityPair[sentence.dst.pbInstances.length];
			for (int j=0; j<sentence.dst.pbInstances.length; ++j)
			{
				simMatrix[i][j] = new SimilarityPair(measureSimilarity(sentence.src.pbInstances[i], sentence.dst.pbInstances[j], sentence, true), 
						measureSimilarity(sentence.dst.pbInstances[j], sentence.src.pbInstances[i], sentence, false));

				//System.out.printf("%s => %s %f\n", srcInstances.get(i).rolesetId, dstInstances.get(j).rolesetId, simMatrix[i][j].getCompositeScore());
			}
		}
		return simMatrix;	
	}

	/**
	 * Measure similarity between a pair of predicates
	 * @param lhs
	 * @param rhs
	 * @param sentence
	 * @param isSrc
	 * @return
	 */
	private Similarity measureSimilarity(PBInstance lhs, PBInstance rhs, SentencePair sentence, boolean isSrc) 
	{
		Similarity simScore = new Similarity();
		
		PBArg[] lhsArgs = lhs.getArgs();
		float predicateScore = 0.0f;
		float predicateDenom = 0.0f;
		float[][] scores = new float[lhsArgs.length][];
	
		float argScore      = 0.0f;
		float argDenom      = 0.0f;
		float arg01Score    = 0.0f;
		
		float arg01Denom    = 0.0f;
		float argOtherScore = 0.0f;
		float argOtherDenom = 0.0f;

		int i=0;
		for (PBArg lhsArg : lhsArgs)
		{
			scores[i] = measureArgAlignment(lhsArg, rhs, lhs.getTree().getIndex(), sentence, isSrc);
			TBNode[] nodes = lhsArg.getTokenNodes();
			argScore = 0.0f;
			argDenom = 0.0f;
			float[] weight;
			for (int c=0; c<scores[i].length; ++c)
			{
				weight = isSrc?SimilarityPair.SRC_POS_WEIGHT.get(nodes[c].getPOS()):SimilarityPair.DST_POS_WEIGHT.get(nodes[c].getPOS());
				weight = weight==null? SimilarityPair.DEFAULT_POS_WEIGHTS : weight;
				argScore += weight[0]*scores[i][c];
				argDenom += weight[1];
				
				float[] val = simScore.posScore.get(nodes[c].getPOS());
				
				if (val==null)
				{
					val = new float[2];
					simScore.posScore.put(nodes[c].getPOS(), val);
				}
				
				val[0] += scores[i][c];
				//val[0] += scores[i][c]>0?1:0;
				++val[1];
			}
			
			argScore = (argDenom==0?0:argScore/argDenom);
			argScore = argScore>1?1:argScore;
			
			if (lhsArg.isLabel("rel"))
			{
				predicateDenom = argDenom;
				predicateScore = argScore;
			}
			else if (lhsArg.isLabel("ARG0") || lhsArg.isLabel("ARG1"))
			{
				arg01Score+=argScore*argDenom;
				arg01Denom+=argDenom;
				/*
				for (float s:scores[i])
					arg01Score+=s;
				arg01Cnt += scores[i].length;*/
			}
			else
			{
				argOtherScore+=argScore*argDenom;
				argOtherDenom+=argDenom;
				/*
				for (float s:scores[i])
					argOtherScore+=s;
				argOtherCnt += scores[i].length;*/
			}
			//System.out.print(lhs.rolesetId+": ");
			//for (float s:scores[i])
			//	System.out.print(" "+s);
			//System.out.print("\n");
			++i;
		}
		arg01Score = arg01Denom==0?0:arg01Score/arg01Denom;
		argOtherScore = argOtherDenom==0?0:argOtherScore/argOtherDenom;
		//System.out.printf("%s -> %s: %.2f,%.2f,%.2f\n",lhs.rolesetId,rhs.rolesetId,predicateScore,arg01Score,argOtherScore);
		
		if (arg01Denom==0 && argOtherDenom==0)
		{
			simScore.score = predicateScore;
			return simScore;
		}
		
		if (arg01Denom>=argOtherDenom)
		{
			argDenom = (arg01Denom*ARG01FACTOR+argOtherDenom)/ARG01FACTOR;
			argScore = (ARG01FACTOR*arg01Denom*arg01Score+argOtherScore*argOtherDenom)/ARG01FACTOR/argDenom;
		}
		else if (arg01Denom!=0.0f && argOtherDenom!=0.0f)
		{
			argDenom = arg01Denom*(1+ARG01FACTOR)/ARG01FACTOR;
			argScore = (ARG01FACTOR*arg01Score+argOtherScore)/(1+ARG01FACTOR);
		}
		else //arg01Denom==0.0f
		{
			argDenom = argOtherDenom/ARG01FACTOR;
			argScore = argOtherScore/ARG01FACTOR;
		}
		
		if (predicateDenom>0 && arg01Denom/predicateDenom>=2f)
		{
			simScore.score = 0.5f*predicateScore+0.5f*arg01Score;
			return simScore;
		}
		simScore.score = 0.7f*predicateScore+0.3f*arg01Score;
		return simScore;
	}
	
	/**
	 * measures the argument alignment based on GIZA++
	 * @param arg the lhs argument
	 * @param treeIndex the tree index
	 * @param rhs rhs PropBank instance
	 * @param sentence the sentence pair
	 * @param isSrc whether the lhs is the src sentence or not
	 * @return the alignment score for each token of the lhs argument
	 */
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
	
	
	private float[] computeArgWeight(PBInstance instance)
	{
		PBArg[] args = instance.getArgs();
		float[] weights = new float[args.length];
		
		float predWeight = 0;
		float argWeight = 0;
		float arg01Weight = 0;
		float argOtherWeight = 0;
		
		float predCnt = 0;
		float arg01Cnt = 0;
		float argCnt = 0;
		float argOtherCnt = 0;
		
		for (PBArg arg:args)
		{
			if (arg.isLabel("rel"))
				predCnt = arg.getTokenNodes().length;
			else if (arg.isLabel("ARG0") || arg.isLabel("ARG1"))
				arg01Cnt += arg.getTokenNodes().length;
			else
				argOtherCnt += arg.getTokenNodes().length;
		}

		if (arg01Cnt==0 && argOtherCnt==0)
			predWeight = 1;
		else
		{
			if (arg01Cnt>=argOtherCnt)
				argCnt= arg01Cnt+argOtherCnt/ARG01FACTOR;
			else if (arg01Cnt>0 && argOtherCnt>0)
				argCnt = arg01Cnt*(1+ARG01FACTOR)/ARG01FACTOR;
			else //arg01Denom==0.0f
				argCnt = argOtherCnt/ARG01FACTOR;
			
			arg01Weight = argCnt==0?0:arg01Cnt/argCnt;
			argOtherWeight = argCnt==0?0:(argCnt-arg01Cnt)/argCnt;
	
			if (predCnt>0 && argCnt/predCnt>=2f)
			{
				predWeight = 0.5f;
				argWeight = 0.5f;
			}
			else
			{
				predWeight = 0.7f;
				argWeight = 0.3f;
			}
		}
		
		predWeight = predCnt==0?0:predWeight/predCnt;
		arg01Weight = argCnt==0?0:argWeight/argCnt;
		argOtherWeight = argOtherCnt==0?0:(argCnt-arg01Cnt)/argCnt*argWeight/argOtherCnt;
		
		int i=0;
		for (PBArg arg:args)
		{
			if (arg.isLabel("rel"))
				weights[i] = arg.getTokenNodes().length*predWeight;
			else if (arg.isLabel("ARG0") || arg.isLabel("ARG1"))
				weights[i] = arg.getTokenNodes().length*arg01Weight;
			else
				weights[i] = arg.getTokenNodes().length*argOtherWeight;
			++i;
		}

		return weights;
	}
	
	/**
	 * Measure similarity between a pair of predicates
	 * @param lhs
	 * @param rhs
	 * @param sentence
	 * @param isSrc
	 * @return
	 */
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

	private float getFScore(float lhs, float rhs, float bias)
	{
		float denom = bias*lhs + rhs;
		return denom==0?0:(1+bias)*lhs*rhs/denom;
	}

	/**
	 * measures the argument alignment based on GIZA++
	 * @param arg the lhs argument
	 * @param index the tree index
	 * @param rhs rhs PropBank instance
	 * @param sentence the sentence pair
	 * @param isSrc whether the lhs is the src sentence or not
	 * @return the alignment score for each token of the lhs argument
	 */
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

	
	public float getScore(ArrayList<SentencePair> sentences, TIntObjectHashMap<TIntDoubleHashMap> goldLabel, Method method)
	{
		TIntObjectHashMap<TIntDoubleHashMap> systemLabel = new TIntObjectHashMap<TIntDoubleHashMap>();
		TIntDoubleHashMap sMap;
		
		for (SentencePair sentence:sentences)
		{
			Alignment[] alignments=null;
			switch (method)
			{
			case DEFAULT:   alignments = align(sentence); break;
			case GIZA:      alignments = alignGIZA(sentence); break;
			case ARGUMENTS: alignments = alignArg(sentence); break;
			}
			
			for (Alignment alignment:alignments)
			{
				if (alignment.srcId==0 || alignment.dstId==0 || alignment.score<0.1f)
					continue;
				if ((sMap=systemLabel.get(sentence.id))==null)
				{
					sMap = new TIntDoubleHashMap();
					systemLabel.put(sentence.id, sMap);
				}
				sMap.put((alignment.srcId<<16)|alignment.dstId,alignment.score);
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
	}
	
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
	
	static HashMap<String,String> argColorMap = new HashMap<String,String>();
	static
	{
		argColorMap.put("rel","#00ff00");
		argColorMap.put("ARG0", "#FFA07A");
		argColorMap.put("ARG1", "#00FFFF");
		argColorMap.put("ARG2", "#EE82EE");
		argColorMap.put("default", "#EEE8AA");
	}
	
	public String toHTMLPB(PBInstance instance, Sentence sentence)
	{
		StringBuilder buffer = new StringBuilder();
		
		String[] colors = new String[instance.getTree().getTerminalCount()];
		String color;
		for (PBArg arg : instance.getArgs())
		{
			if ((color=argColorMap.get(arg.getLabel()))==null)
				color=argColorMap.get("default");
			for (TBNode node:arg.getTokenNodes())
				colors[node.getTerminalIndex()] = color;
		}
		for (int i=0; i<sentence.tokens.length; ++i)
		{
			if ((sentence.indices[i]>>16) != instance.getTree().getIndex() || 
					colors[sentence.indices[i]&0xffff]==null)
			{
				buffer.append(sentence.tokens[i]);
				buffer.append(" ");
			}
			else
			{
				buffer.append("<font style=\"BACKGROUND:");
				buffer.append(colors[sentence.indices[i]&0xffff]);
				buffer.append("\">");
				buffer.append(sentence.tokens[i]);
				buffer.append("</font> ");
			}
		}
		
		return buffer.toString();
	}
	
}
