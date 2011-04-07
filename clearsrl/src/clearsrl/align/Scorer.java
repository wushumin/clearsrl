package clearsrl.align;

import gnu.trove.TDoubleDoubleHashMap;
import gnu.trove.TDoubleDoubleIterator;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;

public class Scorer {

	public static TLongObjectHashMap<Set<String>> readScore(String filename) throws FileNotFoundException
	{
		TLongObjectHashMap<Set<String>> scores = new TLongObjectHashMap<Set<String>>();
		Scanner scanner = new Scanner(new BufferedReader(new FileReader(filename)));
		
		long   sentenceId;
		int    srcSRLId;
		int    dstSRLId;
		double score;

		int i=0, j=0;
		while (scanner.hasNextLine())
		{
			Set<String> argSet = new HashSet<String>();
			
			String[] segments = scanner.nextLine().trim().split(";");
			
			if (segments.length<1) continue;
			
			String[] tokens = segments[0].split(",");
		
			if (tokens.length<4) continue;
			try {
				sentenceId = Long.parseLong(tokens[0].trim());
				srcSRLId = Short.parseShort(tokens[1].trim());
				dstSRLId = Short.parseShort(tokens[2].trim());
				score = Double.parseDouble(tokens[3].trim());
				

				scores.put((sentenceId<<32)|(srcSRLId<<16)|dstSRLId,argSet);
			} 
			catch (NumberFormatException e)
			{
				System.err.println(e);
			}
			++i;
			
			for (int s=1; s<segments.length; ++s)
			{
				if (segments[s].startsWith("[")) continue;
				
				tokens = segments[s].split(",");
				
				if (tokens.length<3) continue;
				argSet.add(tokens[0].trim()+"<->"+tokens[1].trim());
				++j;
			}
			
		}
		System.out.println(filename+": "+i+" "+j);
		return scores;
	}
	
	public static float[] score(TLongObjectHashMap<Set<String>> lhs, TLongObjectHashMap<Set<String>> rhs)
	{
		float[] ret = new float[2];
		float score=0, scoreArg=0;
		float cnt=0, cntArg=0;

		for (TLongObjectIterator<Set<String>> iter=lhs.iterator(); iter.hasNext();)
		{
			iter.advance();
			Set<String> lhsArgSet = iter.value();
			Set<String> rhsArgSet = null;
			
			if ((rhsArgSet=rhs.get(iter.key()))!=null)
			{
				score++;
				for (String s:lhsArgSet)
					if (rhsArgSet.contains(s))
						scoreArg++;
			}
			cntArg+=lhsArgSet.size();
			cnt++;
		}
		
		ret[0] = cnt==0?0:score/cnt;
		ret[1] = cntArg==0?0:scoreArg/cntArg;
		return ret;
	}

	public static float score(TIntObjectHashMap<TIntDoubleHashMap> lhs, TIntObjectHashMap<TIntDoubleHashMap> rhs)
	{
		float score=0;
		float cnt=0;
		
		TIntDoubleHashMap sMap;
		
		TDoubleDoubleHashMap dMap = new TDoubleDoubleHashMap();
		TDoubleDoubleHashMap nMap = new TDoubleDoubleHashMap();
		
		for (TIntObjectIterator<TIntDoubleHashMap> iter=lhs.iterator(); iter.hasNext();)
		{
			iter.advance();
			cnt += iter.value().size();
			if ((sMap=rhs.get(iter.key()))==null) continue;
				
			for (TIntDoubleIterator sIter=iter.value().iterator(); sIter.hasNext();)
			{
				sIter.advance();
				if (sMap.containsKey(sIter.key()))
				{
					++score;
					dMap.put(sIter.value(),dMap.get(sIter.value())+1);
					nMap.put(sIter.value(),nMap.get(sIter.value())+sMap.get(sIter.key()));
				}
			}
		}
		
		if (dMap.size()<=10)
		{
			for (TDoubleDoubleIterator iter=dMap.iterator(); iter.hasNext();)
			{
				iter.advance();
				System.out.printf("%f: %d, %f\n",iter.key(), (int)(iter.value()), nMap.get(iter.key())/(iter.value()));
			}
		}
		
		return cnt==0?0:score/cnt;
	}

	
	public static void main(String[] args) throws IOException
	{
		TLongObjectHashMap<Set<String>> systemLabel = Scorer.readScore(args[0]);
		TLongObjectHashMap<Set<String>> goldLabel = Scorer.readScore(args[1]);
		float[] p = Scorer.score(systemLabel, goldLabel);
		float[] r = Scorer.score(goldLabel, systemLabel);
		System.out.printf("predicate precision: %.3f, recall: %.3f, f-score: %.3f\n", p[0], r[0], 2*p[0]*r[0]/(p[0]+r[0]));
		System.out.printf("argument precision: %.3f, recall: %.3f, f-score: %.3f\n", p[1], r[1], 2*p[1]*r[1]/(p[1]+r[1]));
	}
}
