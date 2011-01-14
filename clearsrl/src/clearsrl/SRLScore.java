package clearsrl;

import java.io.PrintStream;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SRLScore {
	Map<String, int[]> score;
	
	public SRLScore(Set<String> argTypes)
	{
		score = new TreeMap<String, int[]>();
		//for (String argType:argTypes)
		//	score.put(argType, new int[3]);
	}
	
	public void addResult(Map<String, BitSet> systemSRL, Map<String, BitSet> goldSRL)
	{
		for (Map.Entry<String, BitSet> entry:systemSRL.entrySet())
		{
			if (entry.getKey().equals("rel")) continue;
			if (!score.containsKey(entry.getKey()))
				score.put(entry.getKey(), new int[3]);
			int[] count = score.get(entry.getKey());
			count[0] += entry.getValue().cardinality();
		}
		
		BitSet tokenSet;
		for (Map.Entry<String, BitSet> entry:goldSRL.entrySet())
		{
			if (entry.getKey().equals("rel")) continue;
			if (!score.containsKey(entry.getKey()))
				score.put(entry.getKey(), new int[3]);
			
			int[] count = score.get(entry.getKey());
			count[1] += entry.getValue().cardinality();
			if ((tokenSet=systemSRL.get(entry.getKey()))!=null)
			{
				tokenSet = (BitSet)tokenSet.clone();
				tokenSet.and(entry.getValue());
				count[2] += tokenSet.cardinality();
			}
		}
		
	}
	
	public void printResults(PrintStream pStream)
	{
		int pTotal=0, rTotal=0, fTotal=0;
		double p, r, f;
		for (Map.Entry<String, int[]> entry:score.entrySet())
		{
			pTotal += entry.getValue()[0];
			rTotal += entry.getValue()[1];
			fTotal += entry.getValue()[2];
			
			p = entry.getValue()[0]==0?0:((double)entry.getValue()[2])/entry.getValue()[0];
			r = entry.getValue()[1]==0?0:((double)entry.getValue()[2])/entry.getValue()[1];
			f = p==0?0:(r==0?0:2*p*r/(p+r));
			System.out.printf("%s(%d,%d,%d): precision: %f recall: %f, f-measure: %f\n", entry.getKey(), entry.getValue()[2], entry.getValue()[0], entry.getValue()[1], p, r,f);
		}
		
		p = pTotal==0?0:((double)fTotal)/pTotal;
		r = rTotal==0?0:((double)fTotal)/rTotal;
		f = p==0?0:(r==0?0:2*p*r/(p+r));
		System.out.printf("%s(%d,%d,%d): precision: %f recall: %f, f-measure: %f\n", "all", fTotal, pTotal, rTotal, p, r,f);
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		
		return builder.toString();
	}
}
