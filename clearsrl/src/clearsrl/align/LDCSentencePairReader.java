package clearsrl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.SortedMap;

import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

public class LDCSentencePairReader extends SentencePairReader {

    Map<String, TBTree[]> srcTreeBank;
    Map<String, TBTree[]> dstTreeBank;
    Map<String, SortedMap<Integer, List<PBInstance>>>  srcPropBank;
    Map<String, SortedMap<Integer, List<PBInstance>>>  dstPropBank;
	
    Scanner sentenceInfoScanner;
    Scanner srcTokenScanner;
    Scanner dstTokenScanner;
    Scanner alignmentScanner;
    
	public LDCSentencePairReader(Properties props) {
		this(props, true);
	}

	public LDCSentencePairReader(Properties props, boolean reWriteObjStream) {
		super(props, reWriteObjStream);
	}
	
	@Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
	
    @Override
    public void initialize() throws FileNotFoundException
    {
        close();
        super.initialize();
        if (objStreamAvailable) return;

        srcTreeBank = TBUtil.readTBDir(props.getProperty("src.tbdir"), props.getProperty("tb.regex"));
        dstTreeBank = TBUtil.readTBDir(props.getProperty("dst.tbdir"), props.getProperty("tb.regex"));
        
        srcPropBank = PBUtil.readPBDir(new TBReader(srcTreeBank), props.getProperty("src.pbdir"), props.getProperty("pb.regex"), new OntoNoteTreeFileResolver());
        dstPropBank = PBUtil.readPBDir(new TBReader(dstTreeBank), props.getProperty("dst.pbdir"), props.getProperty("pb.regex"), new OntoNoteTreeFileResolver());
        
        sentenceInfoScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("info"))));
        srcTokenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.tokenfile")))).useDelimiter("[\n\r]");
        dstTokenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.tokenfile")))).useDelimiter("[\n\r]");
        alignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("alignment"))));
    }
	
	@Override
	public SentencePair nextPair() {
    	if (objStreamAvailable) return readSentencePair();
    	
		// TODO the rest
    	if (!sentenceInfoScanner.hasNext()) return null;
    	
    	String[] infoTokens = sentenceInfoScanner.nextLine().trim().split("[ \t]+");
    	String[] srcTerminals = srcTokenScanner.nextLine().trim().split("[ \t]+");
    	String[] dstTerminals = dstTokenScanner.nextLine().trim().split("[ \t]+");
    	String[] alignments = alignmentScanner.nextLine().trim().split("[ \t]+");
    	
    	int[] srcTerminaltoToken = new int[srcTerminals.length];
    	int[] dstTerminaltoToken = new int[dstTerminals.length];
    	
    	List<String> srcTokens = new ArrayList<String>();
    	for (int i=0; i<srcTerminals.length; ++i)
    	    if (!srcTerminals[i].startsWith("*"))  {
    	        srcTerminaltoToken[i] = srcTokens.size();
    	        srcTokens.add(srcTerminals[i]);
    	    } else {
    	        srcTerminaltoToken[i] = -1;
    	    }

    	List<String> dstTokens = new ArrayList<String>();
    	for (int i=0; i<dstTerminals.length; ++i)
            if (!dstTerminals[i].startsWith("*"))  {
                dstTerminaltoToken[i] = dstTokens.size();
                dstTokens.add(dstTerminals[i]);
            } else {
                dstTerminaltoToken[i] = -1;
            }
    	
        int id=Integer.parseInt(infoTokens[0]);
        SentencePair sentencePair = new SentencePair(id);

        String filename = "nw/xinhua/"+infoTokens[1].substring(5,7)+"/"+infoTokens[1];
        
        TBTree srcTree = srcTreeBank.get(filename+".parse")[Integer.parseInt(infoTokens[3])];
        TBTree[] dstTrees = null;
        {
            TBTree[] trees = dstTreeBank.get(filename+".parse");
            String[] dstTreeIds = infoTokens[4].split(",");
            dstTrees = new TBTree[dstTreeIds.length];
            for (int i=0; i<dstTreeIds.length; ++i)
                dstTrees[i] = trees[Integer.parseInt(dstTreeIds[i])];
        }
        List<TBNode> srcTreeNodes = srcTree.getRootNode().getTokenNodes();
        List<TBNode> dstTreeNodes = new ArrayList<TBNode>();
        for (TBTree tree:dstTrees)
            dstTreeNodes.addAll(tree.getRootNode().getTokenNodes());
        
        if (srcTreeNodes.size()!=srcTokens.size() || dstTreeNodes.size()!=dstTokens.size())
        {
            System.err.println("Mismatch: "+id);
            sentencePair.id = -id;
            return sentencePair;
        }
        
        
        
    	writeSentencePair(sentencePair);
        
        return sentencePair;
	}
	
	@Override
    void close()
    {
	    if (sentenceInfoScanner!=null) {
	        sentenceInfoScanner.close();
	        sentenceInfoScanner = null;
	    }
	    if (srcTokenScanner!=null) {
	        srcTokenScanner.close();
	        srcTokenScanner = null;
        }
        if (dstTokenScanner!=null) {
            dstTokenScanner.close();
            dstTokenScanner = null;
        }
        if (alignmentScanner!=null) {
            alignmentScanner.close();
            alignmentScanner = null;
        }
	    
        super.close();
    }

}
