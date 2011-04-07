package clearsrl.align;

import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import clearcommon.util.PropertyUtil;

public class RunAligner {

	public static void main(String[] args) throws IOException
	{			
		Properties props = new Properties();
		{
			FileInputStream in = new FileInputStream(args[0]);
			InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
			props.load(iReader);
			iReader.close();
			in.close();
		}
		
		Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		
		String baseFilter = "";
		if (args.length>1) baseFilter = args[1];
		
		props = PropertyUtil.filterProperties(props, baseFilter+"align.");
		
		for(Entry<Object, Object> prop: props.entrySet())
		    System.out.println(prop.getKey()+" = "+prop.getValue());
		
		SentencePairReader sentencePairReader = null;
		
		if (baseFilter.startsWith("ldc"))
		    sentencePairReader = new LDCSentencePairReader(props, false);
		else
		    sentencePairReader = new DefaultSentencePairReader(props, false);
		
		Aligner aligner = new Aligner(sentencePairReader, Float.parseFloat(props.getProperty("threshold", "0.5")));
		
		//Scanner linesIdx = new Scanner(new BufferedReader(new FileReader(props.getProperty("train.all.lnum"))));
		//int lineIdx = linesIdx.nextInt();
		
		int lines = 0;
		
		int srcTokenCnt = 0;
		int dstTokenCnt = 0;

		System.out.println("#****************************");
		
		String htmlOutfile = props.getProperty("output.html", null);
		
		if (htmlOutfile==null)
			htmlOutfile = "/dev/null";

		PrintStream alignmentStream;
		try {
		    alignmentStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(props.getProperty("output.txt", null))));
		} catch (Exception e) {
		    alignmentStream = System.out;
		}
		
		PrintStream htmlStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(htmlOutfile)));

		sentencePairReader.initialize();
		
		Aligner.initAlignmentOutput(htmlStream);
		AlignmentStat stat = new AlignmentStat();
		
		while (true)
		{
		    SentencePair sentencePair = sentencePairReader.nextPair();
		    if (sentencePair==null) break;
		    
            srcTokenCnt += sentencePair.srcAlignment.size();
            dstTokenCnt += sentencePair.dstAlignment.size();
		    
		    if (sentencePair.id%1000==999)
		    {
		    	System.out.println(sentencePair.id+1);
		    }
		    //System.out.println("*****************");
		    //System.out.println(sentencePair);
		    
		    Alignment[] alignments = aligner.align(sentencePair);
	       
		    for (Alignment alignment:alignments)
		    {
		        alignmentStream.printf("%d,%s;[%s,%s]\n",sentencePair.id+1, alignment.toString(), alignment.getSrcPBInstance().getRoleset(),alignment.getDstPBInstance().getRoleset());
		        stat.addAlignment(alignment);
		    }
		    
		    Aligner.printAlignment(htmlStream, sentencePair, alignments);

            TObjectIntHashMap<String> tgtMap;
            for (int i=0; i<alignments.length; ++i)
            {
                //System.out.println("-----------------------------");
                //System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
                
                String srcRole = sentencePair.src.pbInstances[alignments[i].srcPBIdx].getRoleset();
                String dstRole = sentencePair.dst.pbInstances[alignments[i].dstPBIdx].getRoleset();

                // strip roleset id
                if (srcRole.lastIndexOf('.')>=0) 
                    srcRole = srcRole.substring(0,srcRole.lastIndexOf('.')+1);
                if (dstRole.lastIndexOf('.')>=0) 
                    dstRole = dstRole.substring(0,dstRole.lastIndexOf('.')+1);
                
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
            /*
            if (chInstances.size()==0 || enInstances.size()==0)
                continue;
            
            float [][]simMatrix = new float[chInstances.size()>enInstances.size()?chInstances.size():enInstances.size()][];
            for (int i=0; i<simMatrix.length; ++i)
                simMatrix[i] = new float[simMatrix.length];
            
            for (int i=0; i<chInstances.size(); ++i)
                for (int j=0; j<enInstances.size(); ++j)
                    simMatrix[i][j] = align.measureSimiliarity(chInstances.get(i), enInstances.get(j), sentence);
            
            float [][]costMatrix = new float[simMatrix.length][];
            for (int i=0; i<costMatrix.length; ++i)
            {
                costMatrix[i] = new float[costMatrix.length];
                for (int j=0; j<costMatrix[i].length; ++j)
                    costMatrix[i][j] = Alignment.MAX_SIMILARITY-simMatrix[i][j];
            }
            HungarianAlgorithm.computeAssignments(costMatrix);
            */
            
		}
		sentencePairReader.close();
		Aligner.finalizeAlignmentOutput(htmlStream);
		if (alignmentStream!=System.out) alignmentStream.close();
		
		System.out.printf("lines: %d, src tokens: %d, dst tokens: %d\n",lines, srcTokenCnt, dstTokenCnt);
		stat.printStats(System.out);
		aligner.collectStats();
		
		System.exit(0);
		
		// Get rid of singleton mapping and light verbs
		Set<String> srcLightVerbs = new HashSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.srcLightVerbs"),",");
			while(tok.hasMoreTokens())
				srcLightVerbs.add(tok.nextToken().trim()+".");
			System.out.println(srcLightVerbs);
		}
		
		Set<String> dstLightVerbs = new HashSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.dstLightVerbs"),",");
			while(tok.hasMoreTokens())
				dstLightVerbs.add(tok.nextToken().trim()+".");
			System.out.println(dstLightVerbs);
		}
		
		for (Iterator<Map.Entry<String, TObjectIntHashMap<String>>> iter = srcDstMapping.entrySet().iterator(); iter.hasNext();)
		{
			Map.Entry<String, TObjectIntHashMap<String>> entry = iter.next();
			
			for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
			{
				tIter.advance();
				if (tIter.value()==1 || dstLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
					tIter.remove();
			}
			if (entry.getValue().isEmpty() || srcLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				iter.remove();
		}
		for (Iterator<Map.Entry<String, TObjectIntHashMap<String>>> iter = dstSrcMapping.entrySet().iterator(); iter.hasNext();)
		{
			Map.Entry<String, TObjectIntHashMap<String>> entry = iter.next();
			
			for (TObjectIntIterator<String> tIter=entry.getValue().iterator();tIter.hasNext();)
			{
				tIter.advance();
				if (tIter.value()==1 || srcLightVerbs.contains(tIter.key().substring(0,tIter.key().lastIndexOf('.')+1)))
					tIter.remove();
			}
			if (entry.getValue().isEmpty() || dstLightVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				iter.remove();
		}

		Set<String> dstVerbs = new TreeSet<String>();
		{
			StringTokenizer tok = new StringTokenizer(props.getProperty("aligner.dstVerbs"),",");
			while(tok.hasMoreTokens())
				dstVerbs.add(tok.nextToken().trim()+".");
		}
		
		/*
		int idx =0;
		{
			int []cnt = new int[dstSrcMapping.size()];
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
				cnt[idx++] = -entry.getValue().size();
			Arrays.sort(cnt);
			idx = cnt.length>=60?-cnt[59]:-cnt[cnt.length-1];
		}
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (entry.getValue().size()>=idx)
				dstVerbs.add(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1));
		}

		if (dstVerbs.size()>50)
		{
			idx = 0;
			int []cnt = new int[dstSrcMapping.size()];
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
			{
				if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
					continue;
				
				for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					cnt[idx]-= iter.value();
				}
				++idx;
			}
			Arrays.sort(cnt);
			idx = cnt.length>=65?-cnt[64]:-cnt[cnt.length-1];
			
			for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
			{
				if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
					continue;
				
				int size = 0;
				TObjectIntIterator<String> iter=entry.getValue().iterator();
				while(iter.hasNext())
				{
					iter.advance();
					size += iter.value();
				}
				if (size<idx)
					dstVerbs.remove(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1));
			}
		}
*/	
		System.out.print(dstVerbs.size()+" [");
		for (String word:dstVerbs)
			System.out.print(word.substring(0,word.length()-1)+" ");
		System.out.println("]");


		Set<String> srcRoles = new TreeSet<String>();
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				continue;

			System.out.println(entry.getKey()+":");
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
				srcRoles.add(iter.key());
			}
		}		
		Set<String> dstVerbsMapped = new TreeSet<String>();
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			if (!srcRoles.contains(entry.getKey()))
				continue;
			//System.out.println(entry.getKey()+":");
			TObjectIntIterator<String> iter=entry.getValue().iterator();
			while(iter.hasNext())
			{
				iter.advance();
				dstVerbsMapped.add(iter.key().substring(0,iter.key().lastIndexOf('.')+1));

			}
		}
		dstVerbsMapped.removeAll(dstLightVerbs);
		
		System.out.print(dstVerbsMapped.size()+" [");
		for (String word:dstVerbsMapped)
			System.out.print(word.substring(0,word.length()-1)+" ");
		System.out.println("]");
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			if (srcRoles.contains(entry.getKey()))
				continue;

			System.out.println(entry.getKey()+":");
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				System.out.printf("\t%s %d\n", iter.key(), iter.value());
			}
		}
		
		Map<String, TObjectDoubleHashMap<String>> srcDstMap2 = new TreeMap<String, TObjectDoubleHashMap<String>>();
		Map<String, TObjectDoubleHashMap<String>> dstDstMap2 = new TreeMap<String, TObjectDoubleHashMap<String>>();
		
		for (String key:srcRoles)
			srcDstMap2.put(key, new TObjectDoubleHashMap<String>());
		
		for (Map.Entry<String, TObjectIntHashMap<String>> entry:srcDstMapping.entrySet())
		{
			TObjectDoubleHashMap<String> map = srcDstMap2.get(entry.getKey());
			if (map==null) continue;
			double cnt = 0;
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				cnt += iter.value();
			}
			
			for (TObjectIntIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
			{
				iter.advance();
				map.put(iter.key(),iter.value()/cnt);
			}			
		}

		for (Map.Entry<String, TObjectIntHashMap<String>> entry:dstSrcMapping.entrySet())
		{
			if (!dstVerbs.contains(entry.getKey().substring(0,entry.getKey().lastIndexOf('.')+1)))
				continue;
			TObjectDoubleHashMap<String> map = new TObjectDoubleHashMap<String>();
			dstDstMap2.put(entry.getKey(), map);
			
			for (TObjectIntIterator<String> sIter=entry.getValue().iterator();sIter.hasNext();)
			{
				sIter.advance();
				for (TObjectDoubleIterator<String> iter=srcDstMap2.get(sIter.key()).iterator();iter.hasNext();)
				{
					iter.advance();
					map.put(iter.key(),map.get(iter.key())+iter.value()*sIter.value());
				}
			}
		}
		{
			double []cnt = new double[dstDstMap2.size()];
			int idx=0; 
			for (Map.Entry<String, TObjectDoubleHashMap<String>> entry:dstDstMap2.entrySet())
			{
				for (TObjectDoubleIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					cnt[idx] = cnt[idx]>iter.value()?cnt[idx]:iter.value();
				}
				idx++;
			}
			idx=0;
			for (Map.Entry<String, TObjectDoubleHashMap<String>> entry:dstDstMap2.entrySet())
			{
				System.out.println(entry.getKey()+":");
				for (TObjectDoubleIterator<String> iter=entry.getValue().iterator();iter.hasNext();)
				{
					iter.advance();
					if (iter.value() >= cnt[idx]*0.1)
						System.out.printf("\t%s %.3f\n", iter.key(), iter.value());
					else if (iter.value()>=2 && iter.value() >= cnt[idx]*0.05)
						System.out.printf("\t[%s %.3f]\n", iter.key(), iter.value());
				}
				idx++;
			}
		}
	}
}

