package clearcommon.propbank;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TreeFileResolver;
import clearcommon.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

public final class PBUtil {
	
	private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
	
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, String tbDir)
    {
        return readPBDir(dirName, regex, tbDir, null);
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, String tbDir, TreeFileResolver resolver)
    {   
        return readPBDir(dirName, regex, new TBReader(tbDir, true), resolver);
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, TBReader tbReader)
    {
    	return readPBDir(dirName, regex, tbReader, null);
    }
    
	public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, TBReader tbReader, TreeFileResolver resolver)
	{ 
		return readPBDir(FileUtil.getFiles(new File(dirName), regex, true), tbReader, resolver);		
	}
	
	public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(List<String> files, TBReader tbReader, TreeFileResolver resolver)
	{   
		int correctCnt=0;
		int exceptionCnt=0;
		
		Map<String, SortedMap<Integer, List<PBInstance>>> pbMap = new TreeMap<String, SortedMap<Integer, List<PBInstance>>>();
		SortedMap<Integer, List<PBInstance>> instances;
		
		Set<String> predicates = new HashSet<String>();
		int pCnt = 0;
		
		for (String annotationFile: files)
		{
			PBFileReader pbreader=null;
            try {
                pbreader = new PBFileReader(tbReader, annotationFile, resolver);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                continue;
            }
            logger.info("Reading "+annotationFile);
			PBInstance instance=null;
			//System.out.println(annotationFile);
			for (;;)
			{
				try {
					instance = pbreader.nextProp();
				} catch (PBFormatException e) {
					++exceptionCnt;
				    e.printStackTrace();
				    continue;
				} catch (ParseException e) {
				    e.printStackTrace();
				    pbreader.close();
				    break;
				} catch (Exception e) {
					logger.severe(annotationFile+": "+e.getMessage());
					continue;
				}
				
				if (instance==null) break;
				++correctCnt;
				
				predicates.add(instance.rolesetId);
				++pCnt;
				
				//System.out.println(instance.treeFile+" "+instance.treeIndex+" "+instance.predicateIndex);
				instances = pbMap.get(instance.tree.getFilename());
				if (instances == null)
				{
					instances = new TreeMap<Integer, List<PBInstance>>();
					pbMap.put(instance.tree.getFilename(), instances);
				}
				
				List<PBInstance> instanceList = instances.get(instance.tree.getIndex());
				if (instanceList == null)
				{
				    instanceList = new ArrayList<PBInstance>();
				    instances.put(instance.tree.getIndex(), instanceList);
				}
				instanceList.add(instance);
				
				//instance.tree.moveToRoot();
				//String[] sent = instance.tree.getSubTokens();				
				//for (String word:sent)
				//	System.out.print(word+" ");
				//System.out.print("\n");	
			}
		}
		logger.info(String.format("%d props read, %d format exceptions encountered\n", correctCnt, exceptionCnt));
		
		return pbMap;		
	}
}
