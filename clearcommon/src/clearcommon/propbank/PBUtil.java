package clearcommon.propbank;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PBUtil {
    
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, String tbDir)
    {
        return readPBDir(dirName, regex, tbDir, new DefaultPBTokenizer());
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, String tbDir, PBTokenizer tokenizer)
    {   
        return readPBDir(dirName, regex, new TBReader(tbDir, true), tokenizer);
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, TBReader tbReader)
    {
        return readPBDir(dirName, regex, tbReader, new DefaultPBTokenizer());
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(String dirName, String regex, TBReader tbReader, PBTokenizer tokenizer)
    { 
        return readPBDir(FileUtil.getFiles(new File(dirName), regex, true), tbReader, tokenizer);       
    }
    
    public static Map<String, SortedMap<Integer, List<PBInstance>>> readPBDir(List<String> files, TBReader tbReader, PBTokenizer tokenizer)
    {   
        int exceptionCnt=0;
        
        Map<String, SortedMap<Integer, List<PBInstance>>> pbMap = new TreeMap<String, SortedMap<Integer, List<PBInstance>>>();
        SortedMap<Integer, List<PBInstance>> instances;
        
        Set<String> predicates = new HashSet<String>();
        
        for (String annotationFile: files)
        {
            PBFileReader pbreader=null;
            try {
                pbreader = new PBFileReader(tbReader, annotationFile, tokenizer);
            } catch (IOException e1) {
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
                    if (!e.getMessage().startsWith("parse tree invalid")) {
                        ++exceptionCnt;
                        e.printStackTrace();
                    }
                    continue;
                } catch (ParseException e) {
                    e.printStackTrace();
                    pbreader.close();
                    break;
                } catch (Exception e) {
                    logger.severe(annotationFile+": "+e.getMessage());
                    e.printStackTrace();
                    continue;
                }
                
                if (instance==null) break;
                
                predicates.add(instance.rolesetId);
                
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
                //  System.out.print(word+" ");
                //System.out.print("\n");   
            }
        }
        
        int count = 0;
        int dupCnt = 0;
        for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:pbMap.entrySet())
        	for (Map.Entry<Integer, List<PBInstance>>e2:entry.getValue().entrySet()) {
		    	Collections.sort(e2.getValue());
		    	BitSet predMask = new BitSet();
		    	for (Iterator<PBInstance> iter=e2.getValue().iterator();iter.hasNext();) {
		    		PBInstance instance = iter.next();
		    		if (predMask.get(instance.getPredicate().getTokenIndex())) {
		    			logger.warning("deleting duplicate props: "+e2.getValue());
		    			iter.remove();
		    			dupCnt++;
		    			continue;
		    		}
		    		predMask.set(instance.getPredicate().getTokenIndex());
		    	}
		    	count+=e2.getValue().size();
        	}
        
        logger.info(String.format("%d props read, %d props skipped due to format exceptions, %d duplicated props removed\n", count, exceptionCnt, dupCnt));
        
        return pbMap;       
    }
    
    static final Pattern ARG_PATTERN = Pattern.compile("(([RC]-)?(A[A-Z]*\\d))(\\-[A-Za-z]+)?");
    static String removeArgModifier(String argType) {
        Matcher matcher = ARG_PATTERN.matcher(argType);
        if (matcher.matches())
            return matcher.group(1);
        return argType;
    }
    
    static String[] makeCoNLL(PBInstance instance) {
    	String[] sList = new String[instance.getTree().getTokenCount()];
    	Arrays.fill(sList, "*");
    	
    	List<PBArg> args = new ArrayList<PBArg>();
    	for (PBArg arg: instance.getArgs()) {
    		args.add(arg);
    		for (PBArg nestedArg:arg.getNestedArgs())
    			args.add(nestedArg);
    	}
    	Collections.sort(args);
    	
    	for (PBArg arg:args) {
    		String label = removeArgModifier(arg.getLabel());
    		if (label.endsWith("rel"))
                label = label.substring(0, label.length()-3)+"V";
    		else if (label.startsWith("ARG"))
                label = "A"+label.substring(3);
    		else if (label.matches("[CR]-ARG.*"))
                label = label.substring(0,3)+label.substring(5);
    		BitSet tokenIdxSet = arg.getNode().getTokenSet();
    		
    		int start = tokenIdxSet.nextSetBit(0);
    		if (start<0) {
    			System.err.println("Empty arg encountered: "+arg);
    			continue;
    		}
    			
    		int end = tokenIdxSet.nextClearBit(start)-1;
    		
    		sList[start] = "("+label+sList[start];
    		sList[end] += ')';
    	}
    	   	
    	return sList;
    }
    
    static public String toCoNLLformat(TBTree tree, List<PBInstance> instances) {
    	StringBuilder buffer = new StringBuilder();
    	String[][] outStr = new String[instances==null?2:instances.size()+2][tree.getTokenCount()];
    	
    	if (instances!=null)
    		Collections.sort(instances);
    	
    	TBNode[] nodes = tree.getTokenNodes();
    	for (int i=0; i<outStr[0].length; ++i)
    		outStr[0][i] = nodes[i].getWord(); 
    	
    	Arrays.fill(outStr[1], "-");
    	
    	for (int i=2; i<outStr.length; ++i) {
    		outStr[i] = makeCoNLL(instances.get(i-2));
    		outStr[1][instances.get(i-2).getPredicate().getTokenIndex()] = instances.get(i-2).getRoleset();
    	}
    		
    	int[] maxlength = new int[outStr.length];
        for (int i=0; i<outStr.length; ++i)
            for (int j=0; j<outStr[i].length; ++j)
                if (maxlength[i]<outStr[i][j].length())
                    maxlength[i] = outStr[i][j].length();

        for (int j=0; j<outStr[0].length; ++j) {
            for (int i=0; i<outStr.length; ++i) {
                buffer.append(outStr[i][j]);
                for (int k=outStr[i][j].length(); k<=maxlength[i]; ++k)
                    buffer.append(' ');
            }
            buffer.append("\n");
        }
    	
    	return buffer.toString();
    }
}
