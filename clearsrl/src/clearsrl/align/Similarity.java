package clearsrl.align;

import java.util.HashMap;
import java.util.Map;

public class Similarity {

	public Similarity()
	{
		posScore = new HashMap<String, float[]>();
	}
	
	public float score;
	public Map<String, float[]> posScore;

}
