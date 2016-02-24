package edu.colorado.clear.srl.align;

import gnu.trove.set.TIntSet;
import edu.colorado.clear.common.propbank.PBArg;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;

public class ArgAlignment {

    public PBInstance srcInstance;
    public int        srcArgIdx;
    public TIntSet    dstArgList;

    public float       weight;
    public float       factor;
   
    public float       score;
    
    public ArgAlignment(PBInstance srcInstance, int srcArgIdx, TIntSet dstArgList, float[] srcTerminalWeights)
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
