package clearsrl.align;

import gnu.trove.TDoubleDoubleHashMap;
import gnu.trove.TDoubleDoubleIterator;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class Scorer {

	public static TIntObjectHashMap<TIntDoubleHashMap> readScore(String filename) throws FileNotFoundException
	{
		TIntObjectHashMap<TIntDoubleHashMap> scores = new TIntObjectHashMap<TIntDoubleHashMap>();
		Scanner scanner = new Scanner(new BufferedReader(new FileReader(filename)));
		
		int    sentenceId;
		int    srcSRLId;
		int    dstSRLId;
		double score;

		TIntDoubleHashMap sMap;
		int i=0;
		while (scanner.hasNextLine())
		{
			StringTokenizer tokenizer = new StringTokenizer(scanner.nextLine(),",");
			if (tokenizer.countTokens() < 4)
				continue;
			try {
				sentenceId = Integer.parseInt(tokenizer.nextToken().trim());
				srcSRLId = Short.parseShort(tokenizer.nextToken().trim());
				dstSRLId = Short.parseShort(tokenizer.nextToken().trim());
				score = Double.parseDouble(tokenizer.nextToken().trim());
				
				if ((sMap=scores.get(sentenceId))==null)
				{
					sMap = new TIntDoubleHashMap();
					scores.put(sentenceId, sMap);
				}
				sMap.put((srcSRLId<<16)|dstSRLId,score);
			} 
			catch (NumberFormatException e)
			{
				System.err.println(e);
			}
			++i;
		}
		System.out.println(filename+": "+i);
		return scores;
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
		TIntObjectHashMap<TIntDoubleHashMap> systemLabel = Scorer.readScore(args[0]);
		TIntObjectHashMap<TIntDoubleHashMap> goldLabel = Scorer.readScore(args[1]);
		float precision = Scorer.score(systemLabel, goldLabel);
		float recall = Scorer.score(goldLabel, systemLabel);
		System.out.printf("precision: %.3f, recall: %.3f, f-measure: %.3f\n", precision, recall, 2*precision*recall/(precision+recall));
	}
}
