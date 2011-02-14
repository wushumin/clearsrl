package clearsrl.align;

import java.util.List;

import clearcommon.propbank.PBArg;
import clearcommon.treebank.TBNode;

public class ArgAlignment {

    PBArg       srcArg;
    List<PBArg> dstArgList;

    float       srcWeight;
    float       factor;
   
    float       srcScore;
    float       dstScore;
    
    public ArgAlignment(PBArg srcArg, List<PBArg> dstArgList, float[] srcTerminalWeights)
    {
        this.srcArg     = srcArg;
        this.dstArgList = dstArgList;
        this.factor     = 1.0f;
     
        TBNode[] nodes = srcArg.getTokenNodes();
        
        srcWeight=0.0f;
        for (TBNode node:nodes)
        	srcWeight += srcTerminalWeights[node.getTerminalIndex()];
    }
}
