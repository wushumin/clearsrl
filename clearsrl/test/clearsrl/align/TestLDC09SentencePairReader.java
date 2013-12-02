package clearsrl.align;

import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBTree;
import clearcommon.util.FileUtil;


public class TestLDC09SentencePairReader {
	
	
	static class Sentence
	{		
		public Sentence(int[] ch, int[] en)
		{
			this.ch = ch;
			this.en = en;
		}
		
		int[] ch;
		int[] en;
	}
	
	static class Mapping
	{
		boolean gold=true;
		String chParse;
		Sentence[] sentences;
	}
	
	static Sentence[] readAlignment(String dir, String fName) throws IOException
	{
		List<Sentence> sentences = new ArrayList<Sentence>();
		
		File pFile = new File(dir, fName);
		
		if (!pFile.exists()) return null;
		
		BufferedReader reader = new BufferedReader(new FileReader(pFile));
		
		String line;
		while ((line = reader.readLine())!=null)
		{
			String[] tokens = line.trim().split("\\s+");
			
			if (tokens[0].equals("omitted")||tokens[2].equals("omitted")) continue;

			String[] enTokens = tokens[0].split(",");
			String[] chTokens = tokens[2].split(",");
			
			int[] en = new int[enTokens.length];
			int[] ch = new int[chTokens.length];
			
			for (int i=0; i<enTokens.length; ++i)
				en[i] = Integer.parseInt(enTokens[i])-1;
			for (int i=0; i<chTokens.length; ++i)
				ch[i] = Integer.parseInt(chTokens[i])-1;

			sentences.add(new Sentence(ch, en));
		}
		reader.close();
		
		return sentences.toArray(new Sentence[sentences.size()]);
		
	}
	
	static Mapping findMapping(String enDir, String chDir, String fName) throws IOException
	{	

		Mapping mapping = new Mapping();
		List<Sentence> sentences = new ArrayList<Sentence>();

		File pFile = new File(enDir, fName.replace(".parse", ".parallel"));
		
		if (!pFile.exists()) return null;

		TLongList indices = new TLongArrayList();
		
		BufferedReader reader = new BufferedReader(new FileReader(pFile));
		
		reader.readLine();
		String[] tokens = reader.readLine().trim().split("\\s+");
		boolean original = !tokens[0].equals("translation");

		if (!tokens[1].equals("ch"))
		{
			reader.close();
			return null;
		}
		
		mapping.chParse = tokens[2]+".parse";	
		
		if (!original)
		{
			reader.close();
			reader = new BufferedReader(new FileReader(new File(chDir, tokens[2]+".parallel")));
			reader.readLine(); reader.readLine();
		}
		
		TIntList enList = new TIntArrayList();
		TIntList chList = new TIntArrayList();
	
		
		
		String line;
		while ((line = reader.readLine())!=null)
		{
			if (fName.contains("cnn_0004")) System.out.println(line);
			
			tokens = line.trim().split("\\s+");
		
			int ch = Integer.parseInt(tokens[original?1:2]);
			int en = Integer.parseInt(tokens[original?2:1]);
			
			if (!chList.isEmpty()&&ch<=chList.get(chList.size()-1))
			{
				if (enList.isEmpty() || enList.get(enList.size()-1)<en)
					enList.add(en);
			}
			else if (!enList.isEmpty()&&en<=enList.get(enList.size()-1))
			{
				if (chList.isEmpty() || chList.get(chList.size()-1)<ch)
					chList.add(ch);
			}
			else
			{
				if (!chList.isEmpty())
				{
					sentences.add(new Sentence(chList.toArray(), enList.toArray()));
					chList.clear(); enList.clear();
				}
				chList.add(ch);
				enList.add(en);
			}
		}
		reader.close();
		
		if (!chList.isEmpty())
			sentences.add(new Sentence(chList.toArray(), enList.toArray()));
		
		mapping.sentences = sentences.toArray(new Sentence[sentences.size()]);

		
		if (fName.contains("cnn_0004"))
		{
			for (Sentence s:mapping.sentences)
				System.out.println(Arrays.toString(s.ch)+" <> "+Arrays.toString(s.en));
		}
		
		return mapping;
	}
	
	static String[] readParse(String dir, String file)
	{
		List<String> textList = new ArrayList<String>();
		
		TBFileReader reader=null;
		try {
			reader = new SerialTBFileReader(dir, file);
			
			TBTree tree;
			while ((tree=reader.nextTree())!=null)
				textList.add(tree.getRootNode().toText());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			textList.clear();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			textList.clear();
		}
		
		if (reader!=null) reader.close();
		
		return textList.toArray(new String[textList.size()]);
		
	}
	
	
	public static void main(String[] args) throws IOException{
		
		boolean outputMapping = false;
		String line;
		/*
		BufferedReader breader = new BufferedReader(new FileReader("/home/shumin/stages/shumin/newaligner/TripleGold09/output"));
		
		Map<String,String> map = new HashMap<String, String>();

		while ((line=breader.readLine())!=null)
			map.put(line.substring(0,line.indexOf(' ')),line);
		breader.close();
*/
		
		String enDir = "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations";
		String chDir = "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/chinese/annotations";

		String outDir = "/home/verbs/student/shumin/stages/shumin/newaligner/ontonotes-parallel";
		
		String mappingFile = "/home/verbs/student/shumin/stages/shumin/newaligner/ontonotes-parallel.mapping";
		
		Map<String, String> fileMapping=null;
		
		BufferedReader mappingIn = null;
		PrintStream mappingOut = null;
		
		PrintStream chIdxOut=null;
		PrintStream enIdxOut=null;
		PrintStream chTokOut=null;
		PrintStream enTokOut=null;
		
		PrintStream parallelOut = null;
		
		if (outputMapping)
			mappingOut = new PrintStream(mappingFile);
		else
		{
			mappingIn = new BufferedReader(new FileReader(mappingFile));
			fileMapping = new HashMap<String, String>();
			
			while ((line = mappingIn.readLine())!=null)
			{
				String[] tokens = line.split("\\s+");
				fileMapping.put(tokens[2], tokens[0].substring(0, 4)+".align");
			}
			
			chIdxOut = new PrintStream(new File(outDir, "ontonotes-token.ch"));
			enIdxOut = new PrintStream(new File(outDir, "ontonotes-token.en"));
			chTokOut = new PrintStream(new File(outDir, "ontonotes-text.ch"));
			enTokOut = new PrintStream(new File(outDir, "ontonotes-text.en"));
			parallelOut = new PrintStream(new File(outDir, "ontonotes-parallel"));
		}
		
		List<String> files = FileUtil.getFiles(new File(enDir), ".*\\.parse");
		
		int c1=0;
		int c2=0;
		
		int counter = 0;
		
		for (String file:files)
		{
			Mapping mapping = findMapping(enDir, chDir, file);
			
			if (mapping != null)
			{
				String[] chinese = readParse(chDir, mapping.chParse);
				c1+=chinese.length;
				
				String[] english = readParse(enDir, file);

				
				if (outputMapping)
				{
					if (chinese.length!=0 && english.length!=0)
					{
						
						String chFName = String.format("%04d_ch.snt", counter);
						String enFName = String.format("%04d_en.snt", counter++);
						
						PrintStream enOut = new PrintStream(new File(outDir, enFName));
						PrintStream chOut = new PrintStream(new File(outDir, chFName));
						
						
						for (int i=0; i<chinese.length; ++i)
						{
							//if (chinese[i].trim().isEmpty()) continue;
							chOut.println(chinese[i]);
						}
						
						for (int i=0; i<english.length; ++i)
						{
							//if (english[i].trim().isEmpty()) continue;
							enOut.println(english[i]);
						}
	
						enOut.close();
						chOut.close();
						mappingOut.println(enFName+" "+chFName+" "+file+" "+mapping.chParse+" "+(mapping.sentences.length>0));
					}
				}
				else if (mapping.sentences.length==0)
				{
					String mapFile = fileMapping.get(file);
					if (mapFile!=null)
					{
						mapping.sentences = readAlignment(outDir+"/files", mapFile);
						mapping.gold = false;
					}
				}
				
				if (mapping.sentences.length>0 && chinese.length>0 && english.length>0)
				{
					for (Sentence s:mapping.sentences)
					{
						if (!mapping.gold && (s.ch.length>1 || s.en.length>1)) continue;
						
						chIdxOut.print(mapping.chParse);
						for (int sIdx:s.ch)
						{
							String[] tokens = chinese[sIdx].trim().split("\\s+");
							for (int i=0; i<tokens.length; ++i)
								chIdxOut.printf(" %d-%d", sIdx, i);
							chTokOut.print(chinese[sIdx]);
						}
						chIdxOut.print("\n");
						chTokOut.print("\n");
						
						enIdxOut.print(file);
						for (int sIdx:s.en)
						{
							String[] tokens = english[sIdx].trim().split("\\s+");
							for (int i=0; i<tokens.length; ++i)
								enIdxOut.printf(" %d-%d", sIdx, i);
							enTokOut.print(english[sIdx]);
						}
						enIdxOut.print("\n");
						enTokOut.print("\n");
					}
				}
				
				int sCnt = 0;
				for (int i=0; i<english.length; ++i)
				{
					if (sCnt>=mapping.sentences.length || mapping.sentences[sCnt].en[0]>i)
					{
						parallelOut.println(file.substring(0,file.length()-5)+i+" NONE");
						continue;
					}
					if (mapping.sentences[sCnt].en.length>1)
						parallelOut.println(file.substring(0,file.length()-5)+i+" ON TOO SHORT");
					else
					{
						parallelOut.print(file.substring(0,file.length()-5)+i+" ");
						for (int c:mapping.sentences[sCnt].ch)
							parallelOut.print(chinese[c]);
						parallelOut.print("\n");
					}
					++sCnt;
				}
						
				
				System.out.println(file+" "+chinese.length+"/"+mapping.sentences.length);
				if (mapping.sentences.length>0) c2+= chinese.length;;
			}
			else
			{
				String[] english = readParse(enDir, file);
				for (int i=0; i<english.length; ++i)
					parallelOut.println(file.substring(0,file.length()-5)+i+" NONE");
			}
		}
		
		System.out.println(c1+" "+c2);
		if (mappingOut!=null) mappingOut.close();
		
		if (chIdxOut!=null) chIdxOut.close();
		if (enIdxOut!=null) enIdxOut.close();
		if (chTokOut!=null) chTokOut.close();
		if (enTokOut!=null) enTokOut.close();
		if (parallelOut!=null) parallelOut.close();
	}
}
