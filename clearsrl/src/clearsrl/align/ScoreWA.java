package clearsrl.align;

import gnu.trove.TLongHashSet;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.util.PropertyUtil;

public class ScoreWA {
	long pCount;
	long rCount;
	
	long pTotal;
	long rTotal;
	
	public ScoreWA() {
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
	
	static public void printAlignment(PrintStream out, long[] align)
	{
		Arrays.sort(align);
		for (long a:align)
			out.print((a>>>32)+"-"+((a<<32)>>>32)+" ");
		out.print("\n");
	}
	
	public void printStats(PrintStream out)
    {
		double p = pTotal==0?0:pCount*1.0/pTotal;
		double r = rTotal==0?0:rCount*1.0/rTotal;
		double f = p==0?0:(r==0?0:2*p*r/(p+r));
		out.printf("precision(%d): %.3f, recall(%d): %.3f, f-score: %.3f\n", (int)pTotal, p, (int)rTotal, r, f);
    }
	
	static long[] getWA(String line)
	{		
		TLongHashSet aSet = new TLongHashSet();

		if (line.trim().isEmpty())
			return aSet.toArray();
		
		String[] tokens = line.trim().split("\\s+");
		
		for (String token:tokens)
		{
			int s = Integer.parseInt(token.substring(0, token.indexOf('-')));
			int d = Integer.parseInt(token.substring(token.indexOf('-')+1));
			aSet.add(((long)(s))<<32|((long)d));
		}
		
		return aSet.toArray();
	}
	
	public static void main(String[] args) throws IOException
	{		
		
		ScoreWA score = new ScoreWA();
		BufferedReader rgold = new BufferedReader(new FileReader(args[0]));
		BufferedReader rsys = new BufferedReader(new FileReader(args[1]));
		
		String gLine, sLine;
		
		while ((gLine = rgold.readLine())!=null && (sLine = rsys.readLine())!=null)
		{
			score.addAlignment(getWA(gLine), getWA(sLine));
		}
		
		score.printStats(System.out);
	}
}
