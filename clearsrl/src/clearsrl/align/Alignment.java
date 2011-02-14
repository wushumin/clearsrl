package clearsrl.align;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;

public class Alignment{

	private static final float ARGNUMFACTOR = 2.0f;
	private static final float ARGFACTOR = 1.0f;
	private static final float PREDICATEFACTOR = 2.0f;
	
	public SentencePair sentence;
	public int          srcPBIdx;
	public int          dstPBIdx;
	public float        beta_sqr;
	
	public float        srcScore;
	public float        dstScore;
	
	public float[]      srcTerminalWeights;
	public float[]      dstTerminalWeights;
	
	public List<ArgAlignment> srcDstAlignment;
	public List<ArgAlignment> dstSrcAlignment;
	
	public Alignment(SentencePair sentence, int srcPBIdx, int dstPBIdx, float beta_sqr) 
	{
		this.sentence = sentence;
		this.srcPBIdx = srcPBIdx;
		this.dstPBIdx = dstPBIdx;
		this.beta_sqr = beta_sqr;
	}
	
    float measureArgAlignment(PBArg lhs, PBArg rhs, 
    		                  float[] lhsWeights, float[] rhsWeights, 
    		                  int lhsTreeIdx, int rhsTreeIdx, 
    		                  SortedMap<Integer, int[]> wordAlignment, 
    		                  Sentence rhsSentence) 
    {	    	
        TBNode[] lhsNodes = lhs.getTokenNodes();
        
        float argScore = 0;

        for (int i=0; i<lhsNodes.length; ++i)
        {   
            int pIndex = (lhsNodes[i].getTerminalIndex()|(lhsTreeIdx<<16));
            int[] dstIndices = wordAlignment.get(pIndex);
            float score=0;
            float weight=0;

            if (dstIndices!=null) //possibly false when part of argument is not part of the "sentence"
            {
	            for (int dstIdx: dstIndices)
	            {
	                int idx = rhsSentence.indices[dstIdx];
	
	                // rare (ignore): when GIZA aligns word to a different tree
	                if ((idx>>16) != rhsTreeIdx) continue;
	                
	                // strip the tree index
	                idx = (idx&0xffff);
	
	                if (rhs.hasTerminal(idx)) 
	                	score+=rhsWeights[idx];
	                
	                weight+=rhsWeights[idx];
	            }
	            if (weight>0) score/=weight;
            }
            argScore += score*lhsWeights[lhsNodes[i].getTerminalIndex()];
        }
        
        return argScore;
    }

    public float getCompositeScore()
    {
    	float denom = beta_sqr*srcScore + dstScore;
		return denom==0?0:(1+beta_sqr)*srcScore*dstScore/denom;
    }

    void computeArgWeight(PBArg arg, float[] weights)
    {
    	//TODO: better weight computation
    	BitSet terminals = arg.getTerminalSet();
    	for (int i=terminals.nextSetBit(0); i>=0; i=terminals.nextSetBit(i+1))
    		weights[i] = 1.0f;
    }
    
    List<ArgAlignment> initArgAlignment(PBInstance instance, float[] weights)
    {
    	PBArg[] args = instance.getArgs();
    	
    	List<ArgAlignment> argAlignments = new LinkedList<ArgAlignment>();
    	for (PBArg arg:args)
    	{
    		computeArgWeight(arg, weights);
    		argAlignments.add(new ArgAlignment(arg, new LinkedList<PBArg>(), weights));
    	}
		float predicateFactor = 0.0f;
		float predicateWeight = 0.0f;
		float argWeight       = 0.0f;
		float argNumFactor    = 1.0f;
		float argNumWeight    = 0.0f;
		float argMFactor      = 1/ARGNUMFACTOR;
		float argMWeight      = 0.0f;
		
		for (ArgAlignment argAlignment:argAlignments)
		{
			if (argAlignment.srcArg.getLabel().equals("rel"))
				predicateWeight += argAlignment.srcWeight;
			else if (argAlignment.srcArg.getLabel().matches(".*ARG\\d"))
				argNumWeight += argAlignment.srcWeight;
			else
				argMWeight += argAlignment.srcWeight;
		}
		
		if (argNumWeight>0 && argNumWeight<argMWeight)
			argMFactor = argNumWeight/argMWeight*argMFactor;
    	
    	argWeight = argNumFactor*argNumWeight+argMFactor*argMWeight;
    	
    	if (predicateWeight>0 && argWeight>predicateWeight*ARGFACTOR)
    		predicateFactor = argWeight/ARGFACTOR/predicateWeight;
    	else
    		predicateFactor =  PREDICATEFACTOR/predicateWeight;
    	
    	float denom = 1/(predicateFactor*predicateWeight+argWeight);
    	
    	predicateFactor/=denom;
    	argNumFactor/=denom;
    	argMFactor/=denom;
    	
		for (ArgAlignment argAlignment:argAlignments)
		{
			if (argAlignment.srcArg.getLabel().equals("rel"))
				argAlignment.factor = predicateFactor;
			else if (argAlignment.srcArg.getLabel().matches(".*ARG\\d"))
				argAlignment.factor = argNumFactor;
			else
				argAlignment.factor = argMFactor;
		}
		
		return argAlignments;
    }
    
    
	public void computeAlignment() {
		
		PBArg[] srcArgs = sentence.src.pbInstances[srcPBIdx].getArgs();
		PBArg[] dstArgs = sentence.dst.pbInstances[dstPBIdx].getArgs();
			
		srcTerminalWeights = new float[sentence.src.pbInstances[srcPBIdx].getTree().getTerminalCount()];
		dstTerminalWeights = new float[sentence.dst.pbInstances[dstPBIdx].getTree().getTerminalCount()];
		
		srcDstAlignment = initArgAlignment(sentence.src.pbInstances[srcPBIdx], srcTerminalWeights);
		dstSrcAlignment = initArgAlignment(sentence.dst.pbInstances[dstPBIdx], dstTerminalWeights);
		
		float[][] srcDstScore = new float[srcArgs.length][dstArgs.length];
		float[][] dstSrcScore = new float[srcArgs.length][dstArgs.length];
		
		for (int i=0; i<srcDstScore.length; ++i)
			for (int j=0; j<srcDstScore[i].length;++j)
			{
				srcDstScore[i][j] = measureArgAlignment(srcArgs[i], dstArgs[j],
						srcTerminalWeights, dstTerminalWeights, 
						sentence.src.pbInstances[srcPBIdx].getTree().getIndex(), 
						sentence.dst.pbInstances[dstPBIdx].getTree().getIndex(),
		                sentence.srcAlignment, sentence.dst);
				srcDstScore[i][j] /= srcDstAlignment.get(i).srcWeight;
				dstSrcScore[i][j] = measureArgAlignment(dstArgs[j], srcArgs[i],
						dstTerminalWeights, srcTerminalWeights, 
						sentence.dst.pbInstances[dstPBIdx].getTree().getIndex(),
						sentence.src.pbInstances[srcPBIdx].getTree().getIndex(), 
		                sentence.dstAlignment, sentence.src);
				dstSrcScore[i][j] /= dstSrcAlignment.get(j).srcWeight;
			}
		
		
	
		
		
		float precision = 0.0f;
		float recall    = 0.0f;
		float fScore    = 0.0f;
		
		
		
		srcScore = 0.0f;
		for (ArgAlignment argAlign: dstSrcAlignment)
			srcScore += argAlign.srcScore/argAlign.srcWeight*argAlign.factor;
		
		dstScore = 0.0f;
		for (ArgAlignment argAlign: srcDstAlignment)
			dstScore += argAlign.srcScore/argAlign.srcWeight*argAlign.factor;
	}

}