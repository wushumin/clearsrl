package edu.colorado.clear.srl.ec;

import edu.colorado.clear.common.treebank.TBTree;

public class ECTreeSample {
    TBTree goldTree;
    TBTree parsedTree;
    ECSample[] samples;
    
    public ECTreeSample(TBTree goldTree, TBTree parsedTree, ECSample[] samples){
        this.goldTree = goldTree;
        this.parsedTree = parsedTree;
        this.samples = samples;
    }
    
    public ECSample[] getECSamples() {
        return samples;
    }
}
