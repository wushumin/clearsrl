package clearsrl.align;

import java.util.HashMap;
import java.util.Map;

public class SimilarityPair {
	public static float BETA_SQR = 1.5f;
	public static float[] DEFAULT_POS_WEIGHTS = {1.0f, 1.0f}; 
	public static Map<String,float[]> SRC_POS_WEIGHT=new HashMap<String,float[]>();
	public static Map<String,float[]> DST_POS_WEIGHT=new HashMap<String,float[]>();
	
	public Similarity src;
	public Similarity dst;
	
	public SimilarityPair()
	{
		src = new Similarity();
		dst = new Similarity();
	}
	
	public SimilarityPair(Similarity src, Similarity dst)
	{
		this.src = src;
		this.dst = dst;
	}
	
	public float getCompositeScore()
	{
		float denom = BETA_SQR*src.score + dst.score;
		return denom==0?0:(1+BETA_SQR)*src.score*dst.score/denom;
	}
}
