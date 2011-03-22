package clearsrl.align;

public class ArgAlignmentPair {
	
	public ArgAlignmentPair(int srcArgIdx, int dstArgIdx, float score)
	{
		this.srcArgIdx = srcArgIdx;
		this.dstArgIdx = dstArgIdx;
		this.score     = score;
	}
	
	public int srcArgIdx;
	public int dstArgIdx;
	public float score;
}
