package edu.colorado.clear.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class ExtractFromJson {
	
	static void processProp(File inDir, String srcFilename, File outDir)  {
		List<String> treeList = new ArrayList<String>();
		SortedMap<Long, String> propMap = new TreeMap<Long, String>();
		
		String treeOutFilename;
		String propOutFilename;
		
		if (srcFilename.endsWith("txt")) {
			treeOutFilename = srcFilename.substring(0, srcFilename.length()-3)+"parse";
			propOutFilename = srcFilename.substring(0, srcFilename.length()-3)+"prop";
		} else {
			treeOutFilename = srcFilename+".parse";
			propOutFilename = srcFilename+".prop";
		}
		
		try (JsonReader reader = new JsonReader(new FileReader(new File(inDir, srcFilename)))) {
			reader.beginObject();
			while (reader.hasNext()) {
				if (!reader.nextName().equals("annotations")) {
					reader.skipValue();
					continue;
				}

				reader.beginArray();
				
				while (reader.hasNext()) {
				
					reader.beginObject();
					String tree = null;
					String prop = null;
	
					while (reader.hasNext()) {
						String name = reader.nextName();
						
						if (name.equals("tree"))
							tree = reader.nextString();
						else if (name.equals("pbInstance"))
							prop = reader.nextString();
						else
							reader.skipValue();
						
					}
					if (tree == null || prop == null)
						continue;
	
					if (treeList.isEmpty() || !treeList.get(treeList.size() - 1).equals(tree))
						treeList.add(tree);
	
					int separator = prop.indexOf('#');
	
					String pred = prop.substring(0, separator);
					String argStr = prop.substring(separator + 1).trim();	
					
					int predId = -1;
					
					for (String arg:argStr.split("\\s+"))
						if (arg.endsWith("rel")) {
							predId = Integer.parseInt(arg.substring(0, arg.indexOf(':')));
							break;
						}
					
					if (predId<0)
						continue;
					
					long id = treeList.size()-1;
					id<<=32;
					id|=predId;
					
					propMap.put(id, String.format("%s %d %d %s ----- %s", treeOutFilename, treeList.size()-1, predId, pred, argStr));
					
					reader.endObject();
				}
				reader.endArray();
			}
			reader.endObject();

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try (PrintWriter treeWriter = new PrintWriter(new File(outDir, treeOutFilename)); 
				PrintWriter propWriter = new PrintWriter(new File(outDir, propOutFilename))) {
			for (String line:treeList)
				treeWriter.println(line.replaceFirst("null", ""));
			
			for (Map.Entry<Long, String> entry:propMap.entrySet())
				propWriter.println(entry.getValue());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
	
	public static void main(String[] args) throws Exception {

		String treeName = args[0];

		processProp(new File("."), treeName, new File("."));
	}
}
