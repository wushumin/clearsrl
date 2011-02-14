package clearsrl.align;

import java.util.List;

import clearcommon.propbank.PBArg;
import clearcommon.treebank.TBNode;

public class ArgAlignment {

    PBArg       srcArg;
    List<PBArg> dstArgList;

    float       weight;
    float       factor;
   
    float       score;
    
    public ArgAlignment(PBArg srcArg, List<PBArg> dstArgList, float[] srcTerminalWeights)
    {
        this.srcArg     = srcArg;
        this.dstArgList = dstArgList;
        this.factor     = 1.0f;
     
        TBNode[] nodes = srcArg.getTokenNodes();
        
        weight=0.0f;
        for (TBNode node:nodes)
        	weight += srcTerminalWeights[node.getTerminalIndex()];
    }
    
    public float getFactoredWeight()
    {
        return weight*factor;
    }
    
}
