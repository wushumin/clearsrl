package clearsrl.align;

public class Alignment{
	public Alignment()
	{
	}
	
	public Alignment(int id, int src, int dst, float score)
	{
		this.sentId = id;
		this.srcId = src;
		this.dstId = dst;
		this.score = score;
	}
	
	public int        sentId;
	public int        srcId;
	public int        dstId;
	public float      score;
}