package clearsrl.align;

import gnu.trove.TIntHashSet;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;

public class ArgAlignment {

	PBInstance  srcInstance;
    int         srcArgIdx;
    TIntHashSet dstArgList;

    float       weight;
    float       factor;
   
    float       score;
    
    public ArgAlignment(PBInstance srcInstance, int srcArgIdx, TIntHashSet dstArgList, float[] srcTerminalWeights)
    {
    	this.srcInstance = srcInstance;
        this.srcArgIdx   = srcArgIdx;
        this.dstArgList  = dstArgList;
        this.factor      = 1.0f;
     
        TBNode[] nodes = srcInstance.getArgs()[srcArgIdx].getTokenNodes();
        
        weight=0.0f;
        for (TBNode node:nodes)
        	weight += srcTerminalWeights[node.getTerminalIndex()];
    }
    
    public PBArg getSrcArg()
    {
    	return srcInstance.getArgs()[srcArgIdx];
    }
    
    public float getFactoredWeight()
    {
        return weight*factor;
    }
    
}
