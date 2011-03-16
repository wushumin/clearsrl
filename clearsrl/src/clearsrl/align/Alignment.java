package clearsrl.align;

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
	
    static float getFScore(float lhs, float rhs, float bias)
    {
        float denom = bias*lhs + rhs;
        return denom==0?0:(1+bias)*lhs*rhs/denom;
    }
	
    float measureArgAlignment(PBArg lhs, PBArg rhs, 
    		                  float[] lhsWeights, float[] rhsWeights, 
    		                  int lhsTreeIdx, int rhsTreeIdx, 
    		                  SortedMap<Long, int[]> wordAlignment, 
    		                  Sentence rhsSentence) 
    {	    	
        TBNode[] lhsNodes = lhs.getTokenNodes();
        
        float argScore = 0;

        for (int i=0; i<lhsNodes.length; ++i)
        {   
            int[] dstIndices = wordAlignment.get(Sentence.makeIndex(lhsTreeIdx, lhsNodes[i].getTerminalIndex()));
            float score=0;
            float weight=0;

            if (dstIndices!=null) //possibly false when part of argument is not part of the "sentence"
            {
	            for (int dstIdx: dstIndices)
	            {
	                int treeIdx = Sentence.getTreeIndex(rhsSentence.indices[dstIdx]);
	
	                // rare (ignore): when GIZA aligns word to a different tree
	                if (treeIdx != rhsTreeIdx) continue;
	                
	                int terminalIdx = Sentence.getTerminalIndex(rhsSentence.indices[dstIdx]);
	
	                if (rhs.hasTerminal(terminalIdx)) 
	                	score+=rhsWeights[terminalIdx];
	                
	                weight+=rhsWeights[terminalIdx];
	            }
	            if (weight>0) score/=weight;
            }
            argScore += score*lhsWeights[lhsNodes[i].getTerminalIndex()];
        }
        
        return argScore;
    }

    public float getCompositeScore()
    {
        return getFScore(srcScore, dstScore, beta_sqr);
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
				predicateWeight += argAlignment.weight;
			else if (argAlignment.srcArg.getLabel().matches(".*ARG\\d"))
				argNumWeight += argAlignment.weight;
			else
				argMWeight += argAlignment.weight;
		}
		
		if (argNumWeight>0 && argNumWeight<argMWeight)
			argMFactor = argNumWeight/argMWeight*argMFactor;
    	
    	argWeight = argNumFactor*argNumWeight+argMFactor*argMWeight;
    	
    	if (predicateWeight>0 && argWeight>predicateWeight*ARGFACTOR)
    		predicateFactor = argWeight/ARGFACTOR/predicateWeight;
    	else
    		predicateFactor =  PREDICATEFACTOR/predicateWeight;
    	
    	float denom = predicateFactor*predicateWeight+argWeight;
    	
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

    float computeAlignment(float[][] lhsScore, float[][] rhsScore, List<ArgAlignment> lhsAlignments, List<ArgAlignment> rhsAlignments, float bias)
    {
        float fScore=0.0f;
        float precision = 0.0f;
        float recall = 0.0f;
        
        float[] argScores = new float[rhsScore.length];
        int[] argIndices = new int[rhsScore.length];
        
        for (int i=0; i<rhsScore.length; ++i)
        {
            for (int j=0;j<rhsScore[i].length; ++j)
            {
                ArgAlignment lhsAlignment = lhsAlignments.get(j);
                ArgAlignment rhsAlignment = rhsAlignments.get(i);
                
                float score = getFScore(rhsScore[i][j], lhsScore[j][i], 
                        bias*(float)Math.pow(lhsAlignment.getFactoredWeight()/rhsAlignment.getFactoredWeight(), 2));
                if (score>argScores[i])
                {
                    argScores[i] = score;
                    argIndices[i] = j;
                }
            }
        }
        for (;;)
        {
            int index = getMaxIndex(argScores);
            if (argScores[index]<fScore) break;
            
            ArgAlignment lhsAlignment = lhsAlignments.get(argIndices[index]);
            ArgAlignment rhsAlignment = rhsAlignments.get(index);
            
            precision+=lhsAlignment.getFactoredWeight()*lhsScore[argIndices[index]][index];
            recall+=rhsAlignment.getFactoredWeight()*rhsScore[index][argIndices[index]];
            
            fScore = getFScore(precision, recall, bias);
            
            lhsAlignment.dstArgList.add(rhsAlignment.srcArg);
            lhsAlignment.score+=lhsScore[argIndices[index]][index];
            
            argScores[index] = Float.NEGATIVE_INFINITY;
        }
        
        return fScore;
    }
    
    static int getMaxIndex(float[] vals)
    {
        int index=0;
        float maxVal = vals[0];
        for (int i=1; i<vals.length; ++i)
            if (vals[i]>maxVal)
            {
                maxVal = vals[i];
                index=i;
            }
        return index;
    }
    
	public void computeSymmetricAlignment() {
		
		PBArg[] srcArgs = sentence.src.pbInstances[srcPBIdx].getArgs();
		PBArg[] dstArgs = sentence.dst.pbInstances[dstPBIdx].getArgs();
			
		srcTerminalWeights = new float[sentence.src.pbInstances[srcPBIdx].getTree().getTerminalCount()];
		dstTerminalWeights = new float[sentence.dst.pbInstances[dstPBIdx].getTree().getTerminalCount()];
		
		srcDstAlignment = initArgAlignment(sentence.src.pbInstances[srcPBIdx], srcTerminalWeights);
		dstSrcAlignment = initArgAlignment(sentence.dst.pbInstances[dstPBIdx], dstTerminalWeights);
		
		float[][] srcDstScore = new float[srcArgs.length][dstArgs.length];
		float[][] dstSrcScore = new float[dstArgs.length][srcArgs.length];
		
		for (int i=0; i<srcDstScore.length; ++i)
			for (int j=0; j<srcDstScore[i].length;++j)
			{
				srcDstScore[i][j] = measureArgAlignment(srcArgs[i], dstArgs[j],
						srcTerminalWeights, dstTerminalWeights, 
						sentence.src.pbInstances[srcPBIdx].getTree().getIndex(), 
						sentence.dst.pbInstances[dstPBIdx].getTree().getIndex(),
		                sentence.srcAlignment, sentence.dst);
				srcDstScore[i][j] /= srcDstAlignment.get(i).weight;
				dstSrcScore[j][i] = measureArgAlignment(dstArgs[j], srcArgs[i],
						dstTerminalWeights, srcTerminalWeights, 
						sentence.dst.pbInstances[dstPBIdx].getTree().getIndex(),
						sentence.src.pbInstances[srcPBIdx].getTree().getIndex(), 
		                sentence.dstAlignment, sentence.src);
				dstSrcScore[j][i] /= dstSrcAlignment.get(j).weight;
			}
		
		computeAlignment(srcDstScore, dstSrcScore, srcDstAlignment, dstSrcAlignment, beta_sqr);
		computeAlignment(dstSrcScore, srcDstScore, dstSrcAlignment, srcDstAlignment, 1/beta_sqr);
		
		srcScore = 0.0f;
		for (ArgAlignment argAlign: dstSrcAlignment)
			srcScore += argAlign.score*argAlign.getFactoredWeight();
		
		dstScore = 0.0f;
		for (ArgAlignment argAlign: srcDstAlignment)
			dstScore += argAlign.score*argAlign.getFactoredWeight();
	}

	public String toString() {
	    boolean foundm = false;
	    for (ArgAlignment argAlignment:dstSrcAlignment)
	        if (argAlignment.dstArgList.size()>1)
	            foundm = true;
	    for (ArgAlignment argAlignment:srcDstAlignment)
            if (argAlignment.dstArgList.size()>1)
                foundm = true;
	    if (!foundm) return "";
	    StringBuilder builder = new StringBuilder();
	    builder.append(sentence.id+","+srcPBIdx+","+dstPBIdx+","+getCompositeScore()+",");
	    builder.append(sentence.src.pbInstances[srcPBIdx].getRoleset()+"<->"+sentence.dst.pbInstances[dstPBIdx].getRoleset()+",");
	    builder.append("[");
	    for (ArgAlignment argAlignment:srcDstAlignment)
	    {
	        builder.append(argAlignment.srcArg.getLabel()+"<->");
	        for (PBArg arg:argAlignment.dstArgList)
	            builder.append(arg.getLabel()+" ");
	        builder.append(",");
	    }
	    builder.append("][");
        for (ArgAlignment argAlignment:dstSrcAlignment)
        {
            builder.append(argAlignment.srcArg.getLabel()+"<->");
            for (PBArg arg:argAlignment.dstArgList)
                builder.append(arg.getLabel()+" ");
            builder.append(",");
        }
        builder.append("]");
	    return builder.toString();
	}
}