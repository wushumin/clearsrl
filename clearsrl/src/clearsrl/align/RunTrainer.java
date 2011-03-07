package clearsrl.align;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntObjectHashMap;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;

public class RunTrainer {
	public static void main(String[] args) throws IOException
	{
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();

		//Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		//Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();

		String filter = props.getProperty("alignment.file_filter");
		
		Aligner aligner = new Aligner(new SentencePairReader(props));
		
		Scanner chScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_idx"))));
        Scanner enScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_idx"))));
        Scanner chenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_alignment"))));
        Scanner enchScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_alignment"))));
		
		Scanner devLines = new Scanner(new BufferedReader(new FileReader(props.getProperty("train.dev.lnum"))));
		int devIdx = devLines.nextInt();
		ArrayList<SentencePair> devSentences = new ArrayList<SentencePair>();
		
		Scanner testLines = new Scanner(new BufferedReader(new FileReader(props.getProperty("train.test.lnum"))));
		int testIdx = testLines.nextInt();
		ArrayList<SentencePair> testSentences = new ArrayList<SentencePair>();
		
		int linenum = 0;
		
		
		boolean isDev = true;
		// read in the training samples
		while (chScanner.hasNextLine() && enScanner.hasNextLine() &&
				chenScanner.hasNextLine() && enchScanner.hasNextLine())
		{
			SentencePair sentence;
			String chLine = chScanner.nextLine(); // Chinese treebank terminals
			String enLine = enScanner.nextLine(); // English treebank terminals
			chenScanner.nextLine(); chenScanner.nextLine(); // skip comment & text
			enchScanner.nextLine(); enchScanner.nextLine(); // skip comment & text
			
			String chenLine = chenScanner.nextLine();
			String enchLine = enchScanner.nextLine();
			
			++linenum;
			
			if (linenum!=devIdx && linenum!=testIdx)
				continue;
			
			if (linenum>devIdx && linenum>testIdx)
				break;
			
			if (linenum==devIdx)
			{
				isDev = true;
				if (devLines.hasNext()) devIdx = devLines.nextInt();
			}
			else if (linenum==testIdx)
			{
				isDev = false;
				if (testLines.hasNext()) testIdx = testLines.nextInt();
			}
				
			if (!Pattern.matches(filter, chLine.substring(0,chLine.indexOf(" "))))
				continue;

			sentence = new SentencePair(linenum);
			
			try {
				//sentence.src.parseSentence(chLine, aligner.srcTB, aligner.srcPB);
				//sentence.dst.parseSentence(enLine, aligner.dstTB, aligner.dstPB);
				
				sentence.parseSrcAlign(chenLine);
				sentence.parseDstAlign(enchLine);
				if (isDev)
					devSentences.add(sentence);
				else
					testSentences.add(sentence);
				System.out.println(linenum);
			} catch (SentencePair.BadInstanceException e) {
				System.err.println(e);
				continue;
			}
		}
		
		// read in gold standard labels
		//TIntObjectHashMap<TIntDoubleHashMap> goldDevLabel = Scorer.readScore(props.getProperty("train.dev.labels"));
		
		// read in gold standard labels
		TIntObjectHashMap<TIntDoubleHashMap> goldTestLabel = Scorer.readScore(props.getProperty("train.test.labels"));

		aligner.getScore(testSentences, goldTestLabel, Aligner.Method.ARGUMENTS);
		aligner.getScore(testSentences, goldTestLabel, Aligner.Method.GIZA);
		aligner.getScore(testSentences, goldTestLabel, Aligner.Method.DEFAULT);
		
		
		
		/*
		for (SentencePair sentence:devSentences)
		{
			System.out.println(sentence.src.tbFile+":");
			for (String s:sentence.src.tokens)
				System.out.print(" "+s);
			System.out.print("\n");
			System.out.print(sentence.dst.tbFile+":");
			for (String s:sentence.dst.tokens)
				System.out.print(" "+s);
			System.out.print("\n");
			aligner.alignArg(sentence);
			
			Alignment[] alignment = aligner.alignGIZA(sentence);
			for (int i=0; i<alignment.length; ++i)
			{
				System.out.println("-----------------------------");
				System.out.printf("# %s => %s, %.4f\n", sentence.src.pbInstances[alignment[i].srcId-1].rolesetId, 
						sentence.dst.pbInstances[alignment[i].dstId-1].rolesetId, alignment[i].score);
			}
		}
		*/
		/*
		for (int i=0; i<3; ++i)
		{
			aligner.train(devSentences, goldDevLabel);
			aligner.getScore(devSentences, goldDevLabel);
		}
		aligner.getScore(testSentences, goldTestLabel);
		*/
		/*	
			System.out.println(sentence.src.tbFile+":");
			for (String s:sentence.src.tokens)
				System.out.print(" "+s);
			System.out.print("\n");
			

			System.out.print(sentence.dst.tbFile+":");
			
			for (String s:sentence.dst.tokens)
				System.out.print(" "+s);
			System.out.print("\n");
			
			
			//System.out.println(sentence.getSrcAlignmentString());
			//System.out.println(sentence.getDstAlignmentString());
			
			srcTokenCnt += sentence.srcAlignment.size();
			dstTokenCnt += sentence.dstAlignment.size();
						
			Alignment[] alignment = aligner.align(sentence);
			
			TObjectIntHashMap<String> tgtMap;


			int srcIdx=1;
			int dstIdx=1;
			for (int i=0; i<alignment.length; ++i)
			{

				//System.out.println("-----------------------------");
				if (alignment[i].src != null && alignment[i].dst != null)
				{
					//System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
					
					String srcRole = alignment[i].src.rolesetId;
					if (srcRole.lastIndexOf('.')>=0)
						srcRole = srcRole.substring(0,srcRole.lastIndexOf('.'));
					String dstRole = alignment[i].dst.rolesetId;
					if (dstRole.lastIndexOf('.')>=0)
						dstRole = dstRole.substring(0,dstRole.lastIndexOf('.'));
					
					if ((tgtMap = srcDstMapping.get(srcRole))==null)
					{
						tgtMap = new TObjectIntHashMap<String>();
						srcDstMapping.put(srcRole, tgtMap);
					}
					tgtMap.put(dstRole, tgtMap.get(dstRole)+1);
					
					if ((tgtMap = dstSrcMapping.get(dstRole))==null)
					{
						tgtMap = new TObjectIntHashMap<String>();
						dstSrcMapping.put(dstRole, tgtMap);
					}
					tgtMap.put(srcRole, tgtMap.get(srcRole)+1);	
					
				}
				if (alignment[i].src!=null)
				{
					++srcIdx;
					//System.out.print(alignment[i].src);
				}
				if (alignment[i].dst!=null)
				{
					++dstIdx;
					//System.out.print(alignment[i].dst);
				}
			}
			//System.out.println("#****************************");
			
			++lines;
		}

		System.out.printf("lines: %d, src tokens: %d, dst tokens: %d\n",lines, srcTokenCnt, dstTokenCnt);
		
		
		int maxSrc=0;
		int maxDst=0;
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			System.out.println(entry.getKey()+":");
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
			}
			if (entry.getValue().size()>maxSrc)
				maxSrc=entry.getValue().size();
		}
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			System.out.println(entry.getKey()+":");
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
			}
			if (entry.getValue().size()>maxDst)
				maxDst=entry.getValue().size();
		}
		System.out.println(srcDstMapping.size());
		System.out.println(dstSrcMapping.size());
		
		// output saturation
		int[] srcCnt = new int[maxSrc];
		int[] dstCnt = new int[maxDst];
	
		double srcCntSum=0;
		double dstCntSum=0;
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			int[] val = new int[entry.getValue().size()];
			int i=0;
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				val[i++] = iter.value();
			}
			Arrays.sort(val);
			for (i=val.length-1;i>=0;--i)
			{
				srcCnt[val.length-1-i]+=val[i];
				srcCntSum+=val[i];
			}
		}
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			int[] val = new int[entry.getValue().size()];
			int i=0;
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				val[i++] = iter.value();
			}
			Arrays.sort(val);
			for (i=val.length-1;i>=0;--i)
			{
				dstCnt[val.length-1-i]+=val[i];
				dstCntSum+=val[i];
			}
		}

		System.out.printf("%f ",srcCnt[0]/srcCntSum);
		for (int i=1; i<srcCnt.length;++i)
		{
			srcCnt[i] += srcCnt[i-1];
			System.out.printf("%f ",srcCnt[i]/srcCntSum);		
		}
		System.out.print("\n");
		
		System.out.printf("%f ",dstCnt[0]/dstCntSum);
		for (int i=1; i<dstCnt.length;++i)
		{
			dstCnt[i] += dstCnt[i-1];
			System.out.printf("%f ",dstCnt[i]/dstCntSum);		
		}
		System.out.print("\n");
		
*/
		
	}
}
