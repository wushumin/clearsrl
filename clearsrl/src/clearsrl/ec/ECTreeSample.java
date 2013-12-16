package clearsrl.ec;

import clearcommon.treebank.TBTree;

public class ECTreeSample {
	TBTree tree;
	ECSample[] samples;
	
	public ECTreeSample(TBTree tree, ECSample[] samples){
		this.tree = tree;
		this.samples = samples;
	}
	
	public ECSample[] getECSamples() {
		return samples;
	}
}
