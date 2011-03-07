package clearsrl.align;

import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

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

		boolean sentenceAligned = !(props.getProperty("alignment.sentence_aligned")==null||props.getProperty("alignment.sentence_aligned").equals("false"));

		
		
		Map<String, TObjectIntHashMap<String>> srcDstMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		Map<String, TObjectIntHashMap<String>> dstSrcMapping = new TreeMap<String, TObjectIntHashMap<String>>();
		
		//String filter = "\\Achtb_1\\d{3}\\.[a-zA-Z]+\\z";
		//String filter = "\\Achtb_0100\\.[a-zA-Z]+\\z";
		String tbFilter = props.getProperty("tb.file_filter");
		String pbFilter = props.getProperty("pb.file_filter");
		
		
		
	
		Aligner aligner = new Aligner();
		aligner.srcTB = TBUtil.readTBDir(props.getProperty("src.tb.dir"), tbFilter);
		aligner.dstTB = TBUtil.readTBDir(props.getProperty("dst.tb.dir"), tbFilter);
		aligner.srcPB = PBUtil.readPBDir(props.getProperty("src.pb.dir"), pbFilter, null, aligner.srcTB, null);
		aligner.dstPB = PBUtil.readPBDir(props.getProperty("dst.pb.dir"), pbFilter, null, aligner.dstTB, null);
					
		Scanner chScanner = sentenceAligned?null:new Scanner(new BufferedReader(new FileReader(props.getProperty("alignment.src.token_idx"))));
		Scanner enScanner = sentenceAligned?null:new Scanner(new BufferedReader(new FileReader(props.getProperty("alignment.dst.token_idx"))));
		Scanner chenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("alignment.src"))));
		Scanner enchScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("alignment.dst"))));
		
		//Scanner linesIdx = new Scanner(new BufferedReader(new FileReader(props.getProperty("train.all.lnum"))));
		//int lineIdx = linesIdx.nextInt();
		
		int lines = 0;
		int linenum = 0;
		
		int srcTokenCnt = 0;
		int dstTokenCnt = 0;
		
		SentencePair sentence;
		System.out.println("#****************************");
		
		String htmlOutfile = props.getProperty("alignment.output.html", null);
		
		if (htmlOutfile==null)
			htmlOutfile = "/dev/null";

		PrintStream stream = new PrintStream(htmlOutfile);

		stream.println("<html>\n<head>\n" +
				"<META http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n"+
				"<script type=\"text/javascript\">\n"+
				"function toggleDiv(id)\n"+
				"{\n"+
				"  if (document.getElementById(id).style.display == \"block\")\n"+
				"  {\n"+
				"    document.getElementById(id).style.display = \"none\";\n"+
				"  }\n"+
				"  else\n"+
				"  {\n"+
				"    document.getElementById(id).style.display = \"block\";\n"+
        		"  }\n"+
      			"}\n"+
      			"</script>\n</head>\n"+
				"<body><font size=\"+1\">\n");
		
		TBTree[] srcTrees = null;
		TBTree[] dstTrees = null;
		if (sentenceAligned)
		{
		    srcTrees = (TBTree[])aligner.srcTB.values().toArray()[0];
		    dstTrees = (TBTree[])aligner.dstTB.values().toArray()[0];
		}
		
		while ((sentenceAligned || (chScanner.hasNextLine() && enScanner.hasNextLine())) &&
				chenScanner.hasNextLine() && enchScanner.hasNextLine())
		{
		    
	        chenScanner.nextLine(); chenScanner.nextLine(); // skip comment & text
	        enchScanner.nextLine(); enchScanner.nextLine(); // skip comment & text
	            
	        String chenLine = chenScanner.nextLine();
	        String enchLine = enchScanner.nextLine();
	          
	        String chLine = null;
	        String enLine = null;
            ++linenum;
            sentence = new SentencePair(linenum);
            
		    if (sentenceAligned)
		    {
		        sentence.src.parseSentence(aligner.srcTB.entrySet().iterator().next().getValue()[linenum-1], 
		                aligner.srcPB.entrySet().iterator().next().getValue().get(linenum-1));
		        sentence.dst.parseSentence(aligner.dstTB.entrySet().iterator().next().getValue()[linenum-1], 
		                aligner.dstPB.entrySet().iterator().next().getValue().get(linenum-1));
		    }
		    else
		    {
		        chLine = chScanner.nextLine(); // Chinese treebank terminals
	            enLine = enScanner.nextLine(); // English treebank terminals
		    
    			if (!Pattern.matches(tbFilter, chLine.substring(0,chLine.indexOf(" "))))
    				continue;
    			
    			sentence.src.parseSentence(chLine, aligner.srcTB, aligner.srcPB);
                sentence.dst.parseSentence(enLine, aligner.dstTB, aligner.dstPB);
		    }
		    
		    System.out.println(chenLine);
            System.out.println(enchLine);
		    
			try {				
				sentence.parseSrcAlign(chenLine);
				sentence.parseDstAlign(enchLine);
			} catch (SentencePair.BadInstanceException e) {
				System.err.println(e);
				continue;
			}
		    
			//System.out.println(sentence.src.tbFile+":");
			//for (String s:sentence.src.tokens)
			//	System.out.print(" "+s);
			//System.out.print("\n");
			
			stream.printf("<h3>%d</h3>\n", linenum);
			for (String s:sentence.src.tokens)
				stream.print(" "+s);
			stream.println("<br>");

			//System.out.print(sentence.dst.tbFile+":");
			//for (String s:sentence.dst.tokens)
			//	System.out.print(" "+s);
			//System.out.print("\n");
			
			for (String s:sentence.dst.tokens)
				stream.print(" "+s);
			stream.println("<br>");
			if (!sentenceAligned)
			{
			    stream.printf("<!-- %s -->\n", chLine);
			    stream.printf("<!-- %s -->\n", enLine);
			}
			stream.printf("<!-- %s -->\n", sentence.getSrcAlignmentIndex());
			stream.printf("<!-- %s -->\n", sentence.getDstAlignmentIndex());
			
			//System.out.println(sentence.getSrcAlignmentString());
			//System.out.println(sentence.getDstAlignmentString());
			
			srcTokenCnt += sentence.srcAlignment.size();
			dstTokenCnt += sentence.dstAlignment.size();
						
			Alignment[] alignment = aligner.align(sentence);
			//Alignment[] alignment = aligner.alignArg(sentence);
			
			TObjectIntHashMap<String> tgtMap;
			
			
			stream.println("<br><font size=\"-1\">Chinese:\n<ol>");
			for (int i=0; i<sentence.src.pbInstances.length; ++i)
			{
				stream.println("<li> "+aligner.toHTMLPB(sentence.src.pbInstances[i], sentence.src));
				stream.printf("<!-- %s: %d,%d -->\n", sentence.src.pbInstances[i].getTree().getFilename(), sentence.src.pbInstances[i].getTree().getIndex(), sentence.src.pbInstances[i].getPredicate().getTerminalIndex());
			}
			stream.println("</ol>");
			
			stream.println("English:\n<ol>");
			for (int i=0; i<sentence.dst.pbInstances.length; ++i)
			{
				stream.println("<li> "+aligner.toHTMLPB(sentence.dst.pbInstances[i], sentence.dst));
				stream.printf("<!-- %s: %d,%d -->\n", sentence.dst.pbInstances[i].getTree().getFilename(), sentence.dst.pbInstances[i].getTree().getIndex(), sentence.dst.pbInstances[i].getPredicate().getTerminalIndex());
			}
			stream.println("</ol></font>");
			
			stream.println("<input type=\"button\" value=\"system\" onclick=\"toggleDiv("+(lines+1)+")\">\n"+
					"<div id="+(lines+1)+" style=\"display:none;\">\n"+
					"<HR>");
			
			//int srcIdx=1;
			//int dstIdx=1;
			for (int i=0; i<alignment.length; ++i)
			{
				//System.out.println("-----------------------------");
				//System.out.printf("# %s => %s, %.4f\n", alignment[i].src.rolesetId, alignment[i].dst.rolesetId, alignment[i].score);
				
				String srcRole = sentence.src.pbInstances[alignment[i].srcPBIdx].getRoleset();
				String dstRole = sentence.dst.pbInstances[alignment[i].dstPBIdx].getRoleset();

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
				
				stream.printf("<p> %d,%d,%f </p>\n", alignment[i].srcPBIdx+1, alignment[i].dstPBIdx+1, alignment[i].getCompositeScore());

			}
			stream.println("<HR></div>\n");
			//System.out.println("#****************************");
			
			++lines;

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
		stream.println("<h3>end<h3>\n</font>\n</body></html>");

		System.out.printf("lines: %d, src tokens: %d, dst tokens: %d\n",lines, srcTokenCnt, dstTokenCnt);
		
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

