package clearsrl.ec;
import java.util.BitSet;

import clearcommon.treebank.TBTree;

public class ECDepTreeSample extends ECTreeSample {
	BitSet[] headMasks;
	
	public ECDepTreeSample(TBTree tree, ECSample[] samples, BitSet[] headMasks) {		
		super(tree, samples);
		this.headMasks = headMasks;
		int cnt=0;
		for (BitSet headMask:headMasks)
			cnt+=headMask.cardinality();
		if (cnt!=samples.length)
			System.err.println("wtf?");
		
	}
	
	public String[][] makeLabels(String[] labels) {
		return makeLabels(headMasks, labels);
	}
	
	public static String[][] makeLabels(BitSet[] headMasks, String[] labels) {
    	String[][] labelArray = new String[headMasks.length][headMasks.length+1];
    	
    	int cnt=0;
    	for (int h=0; h<headMasks.length; ++h)
    		for (int t=headMasks[h].nextSetBit(0);t>=0;t=headMasks[h].nextSetBit(t+1))
    			labelArray[h][t] = labels[cnt++];
    	return labelArray;
	}
}
