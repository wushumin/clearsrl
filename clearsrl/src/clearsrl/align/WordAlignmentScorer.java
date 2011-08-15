package clearsrl.align;

import java.io.PrintStream;
import java.util.Arrays;

import gnu.trove.TLongHashSet;

public class WordAlignmentScorer {
	
	long pCount;
	long rCount;
	
	long pTotal;
	long rTotal;
	
	public WordAlignmentScorer() {
		pCount = 0;
		rCount = 0;
		pTotal = 0;
		rTotal = 0;
	}
	
	public void addAlignment(long[] gold, long[] sys)
	{
		rTotal += gold.length;
		pTotal += sys.length;
		
		TLongHashSet intersection = new TLongHashSet(gold);
		intersection.retainAll(sys);
		
		pCount += intersection.size();
		rCount += intersection.size();
	}
	
	static void printAlignment(PrintStream out, long[] align)
	{
		Arrays.sort(align);
		for (long a:align)
			out.print((a>>>32)+"-"+((a<<32)>>>32)+" ");
		out.print("\n");
	}
	
	void printStats(PrintStream out)
    {
		double p = pTotal==0?0:pCount*1.0/pTotal;
		double r = rTotal==0?0:rCount*1.0/rTotal;
		double f = p==0?0:(r==0?0:2*p*r/(p+r));
		System.out.printf("precision(%d): %.3f, recall(%d): %.3f, f-score: %.3f\n", (int)pTotal, p, (int)rTotal, r, f);
    }
	
}
