package clearcommon.util;

import gnu.trove.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

public class JIO
{
	static public Scanner createScanner(Reader reader)
	{		
		return new Scanner(new BufferedReader(reader));
	}

	static public Scanner createScanner(String filename)
	{
		Scanner scan = null;
		
		try
		{
			scan = new Scanner(new BufferedReader(new FileReader(filename)));
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		
		return scan;
	}
	
	static public PrintStream createPrintFileOutputStream(String filename)
	{
		PrintStream fout = null;
		
		try
		{
			fout = new PrintStream(new FileOutputStream(filename));
		}
		catch (FileNotFoundException e) {e.printStackTrace();}
		
		return fout;
	}
	
	/**
	 * Returns a hashmap containing keys in <code>filename</code>.
	 * Values start with <code>beginId</code>.
	 * @param filename name of the file containing keys (one key per line)
	 * @return hashmap containing keys in <code>filename</code>
	 */
	static public TObjectIntHashMap<String> getHashMap(String filename, int beginId)
	{
		TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
		
		try
		{
			BufferedReader fin = new BufferedReader(new FileReader(filename));
			String line;
			
			for (int i=beginId; (line = fin.readLine()) != null; i++)
				map.put(line.trim(), i);
			
			map.trimToSize();
		}
		catch (IOException e) {e.printStackTrace();}
		
		return map;
	}
	
	/** Returns getHashMap(filename, 0). */
	static public TObjectIntHashMap<String> getHashMap(String filename)
	{
		return getHashMap(filename, 0);
	}
	
	static public HashMap<String, String[]> getSSaHashMap(String filename)
	{
		HashMap<String, String[]> map = new HashMap<String, String[]>();
		Scanner scan = JIO.createScanner(filename);
		
		while (scan.hasNextLine())
		{
			StringTokenizer tok = new StringTokenizer(scan.nextLine());
			if (!tok.hasMoreTokens())	continue;
			
			String   key   = tok.nextToken();
			String[] value = new String[tok.countTokens()];
			for (int i=0; tok.hasMoreTokens(); i++)
				value[i] = tok.nextToken();
			
			if (value.length > 0)	map.put(key, value);
		}
		
		return map;
	}
	
	static public HashSet<String> getStringHashSet(String filename)
	{
		HashSet<String> set = new HashSet<String>();
		Scanner scan = JIO.createScanner(filename);
		
		while (scan.hasNext())	set.add(scan.next());
		return set;
	}
	
	/**
	 * Returns a list containing values in <code>filename</code>.
	 * @param filename name of the file containing values (one value per line)
	 * @return list containing values in <code>filename</code>.
	 */
	static public ArrayList<String> getArrayList(String filename)
	{
		ArrayList<String> arr = new ArrayList<String>();
		
		try
		{
			BufferedReader fin = new BufferedReader(new FileReader(filename));
			String line;
			
			while ((line = fin.readLine()) != null)
				arr.add(line.trim());
			
			arr.trimToSize();
		}
		catch (IOException e) {e.printStackTrace();}
		
		return arr;
	}
	
	/**
	 * Prints values in <code>set</code> to <code>outputFile</code>.
	 * @param set set containing values
	 * @param outputFile name of the file to print values
	 */
	static public void printFile(HashSet<String> set, String outputFile)
	{
		PrintStream      fout = JIO.createPrintFileOutputStream(outputFile);
		ArrayList<String> arr = new ArrayList<String>(set);
		
		Collections.sort(arr);
		for (String item : arr)	fout.println(item);
	}
	
	static public void printFile(TObjectIntHashMap<String> map, String outputFile, int cutoff)
	{
		PrintStream      fout = JIO.createPrintFileOutputStream(outputFile);
		ArrayList<String> arr = new ArrayList<String>();
		for (String s:map.keys(new String[map.size()]))
			arr.add(s);
		
		Collections.sort(arr);
		for (String key : arr)
			if (map.get(key) > cutoff)	fout.println(key);
	}
	
	static public ArrayList<String> getFiles(File dir, String regex)
	{
		ArrayList<String> fileNames = new ArrayList<String>();
		if (!dir.isDirectory())
			return fileNames;
		
		File[] files = dir.listFiles();
		Arrays.sort(files);
		
		for (File file:files)
		{
			if (file.isDirectory())
			{
				if (file.getName().startsWith("."))
					continue;
				ArrayList<String> moreFileNames = getFiles(file, regex);
				for (String fileName:moreFileNames)
					fileNames.add(file.getName()+File.separatorChar+fileName);
			}
			else if (Pattern.matches(regex, file.getName()))
				fileNames.add(file.getName());
		}
		return fileNames;
	}
}
