package edu.colorado.clear.common.treebank;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
//import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
//import java.util.Scanner;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import edu.colorado.clear.common.propbank.PBFileReader;

public class SerialTBFileReader extends TBFileReader
{
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());

    //private static String WHITESPACE = " \t\n\r\f";
    
    BufferedReader reader;
    Deque<String>  tokenQueue;
    //Scanner       scanner;
    int           treeCount;
    TBTree        lastTree;
    StringBuilder inputStr;
    
    /**
     * Initializes the Treebank reader.
     * @param filename name of the Treebank file
     * @throws IOException 
     */
    public SerialTBFileReader(String fileName) throws IOException {
        this(new InputStreamReader(fileName.endsWith(".gz")?new GZIPInputStream(new FileInputStream(fileName)):new FileInputStream(fileName), "UTF-8"), fileName);
    }
    
    public SerialTBFileReader(String dirName, String fileName) throws IOException {
    	this(new InputStreamReader(fileName.endsWith(".gz")?new GZIPInputStream(new FileInputStream(new File(dirName, fileName))):new FileInputStream(new File(dirName, fileName)), "UTF-8"), fileName);
    }

    public SerialTBFileReader(Reader reader) {
        this(reader, null);
    }
    
    public SerialTBFileReader(Reader reader, String fileName) {
        super(fileName);
        
        this.reader = new BufferedReader(reader);
        tokenQueue = new ArrayDeque<String>();
        //scanner      = new Scanner(reader);
        //scanner.useDelimiter(String.format("((?<=[\\(\\)%s])|(?=[\\(\\)%s]))",WHITESPACE, WHITESPACE));
        
        treeCount     = 0;
        lastTree      = null;
        closed        = false;
        inputStr      = new StringBuilder();
    }
    
    /**
     * Returns the next tree in the Treebank.
     * If there is none, returns null.
     * @throws TreeException 
     */
    @Override
    public TBTree nextTree() throws ParseException
    {    
        String str;
        
        do {
            str = nextToken();
            if (str == null)
            {
                inputStr = new StringBuilder();
                logger.fine("Read "+treeCount+" trees, done.");
                return lastTree=null;
            }
            logger.fine("Reading tree "+treeCount);
        } while (!str.equals(TBLib.LRB));
        
        Stack<List<TBNode>> childNodeStack = new Stack<List<TBNode>>();
        childNodeStack.push(new ArrayList<TBNode>());
        
        int terminalIndex = 0;
        int tokenIndex    = 0;
        TBNode head       = new TBNode(null, "");       // dummy-head
        TBNode curr       = head;                       // pointer to the current node
        
        do {
            if ((str = nextToken()) == null)
                throw new ParseException(fileName+", "+treeCount+": more tokens needed");
            //System.out.println(str);
            
            if (str.equals(TBLib.LRB)) {
                if ((str = nextToken()) == null)        // str = pos-tag
                    throw new ParseException(fileName+", "+treeCount+": POS-tag is missing, Read \""+inputStr.toString()+"\"");
                if (!TBNode.POS_PATTERN.matcher(str).matches())
                    logger.warning(fileName+", "+treeCount+": Malformed POS tag: "+str);
                
                TBNode childNode = new TBNode(curr, str, (short)(childNodeStack.peek().size()));
                childNodeStack.peek().add(childNode);
                
                curr = childNode;                           // move to child
                childNodeStack.push(new ArrayList<TBNode>());
            } else if (str.equals(TBLib.RRB)) {
            	curr.children= childNodeStack.pop().toArray(TBNode.NO_CHILDREN);
            	
               	if (curr.children.length==0 && curr.getWord()==null) {
            		curr.getParent().word = "("+curr.getPOS()+")";
            		childNodeStack.peek().remove(childNodeStack.peek().size()-1);
            		curr = curr.getParent();  
            		curr.terminalIndex = terminalIndex++;
                    if (curr.isEC())
                    	curr.tokenIndex = -tokenIndex-1;
                    else
                    	curr.tokenIndex = tokenIndex++;
                    logger.fine(fileName+" "+treeCount+": fixed node: "+curr.toParse());
            	} else { 
            		if (curr.children.length!=0 && curr.getWord()!=null)
            			logger.severe(fileName+" "+treeCount+": encountered bad node: "+curr);
            	
	                if (curr.terminalIndex<0) {
	                    curr.terminalIndex = -(terminalIndex+1);
	                    curr.tokenIndex = -(tokenIndex+1);
	                }
	                curr = curr.getParent();                // move to parent
            	}
            } else if (curr.pos.isEmpty()) {
                curr.pos = str;
            } else {
                if (curr.terminalIndex >= 0)
                {
                    // code to fix Berkeley parser anomaly 
                    TBNode pNode = curr;
                    curr.children= childNodeStack.pop().toArray(TBNode.NO_CHILDREN);
                    curr = curr.getParent();
                    
                    TBNode childNode = new TBNode(curr, str, (short)(childNodeStack.peek().size()));
                    childNodeStack.peek().add(childNode);
                    
                    curr = childNode;                           // move to child
                    childNodeStack.push(new ArrayList<TBNode>());
                    curr.pos = pNode.pos;
                    logger.fine(fileName+", "+treeCount+": multi-word token: "+pNode.word+" "+str+"("+pNode.pos+")");
                }
                curr.word = str;                        // str = word
                curr.terminalIndex = terminalIndex++;
                if (curr.isEC())
                	curr.tokenIndex = -tokenIndex-1;
                else
                	curr.tokenIndex = tokenIndex++;
            }
        }
        while (!childNodeStack.isEmpty());
        
        // omit the dummy head
        TBNode tmp = head.children.length==1?head.children[0]:head;
        
        if (tmp.pos.isEmpty())
        	tmp.pos = "FRAG";
        
        tmp.parent=null;
        inputStr = new StringBuilder();
        return lastTree=new TBTree(fileName, treeCount++, tmp, terminalIndex, tokenIndex);
    }
    
    @Override
    public TBTree getTree(int index) throws ParseException
    {
        while (true)
        {
            if (lastTree!=null)
            {
                if (lastTree.index==index)
                    return lastTree;
                if (lastTree.index>index)
                    return null;
            }
            try {
                lastTree = nextTree();
                if (lastTree==null) return null;
            } catch (ParseException e) {
                logger.severe(e.getMessage());
            }
        }
    }
    
    @Override
    public void close()
    {
        if (!closed)
        {
            try {
	            reader.close();
            } catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            }
            closed = true;
        }
    }
    
    @Override
    public boolean isOpen()
    {
        return !closed;
    }
    
    private String nextToken()
    {
    	if (tokenQueue.isEmpty() && isOpen()) {
    		String line = null;
    		try {
    			line = reader.readLine();
            } catch (IOException e) {
            	e.printStackTrace();
            }

            if (line == null) {
            	close();
                return null;
            }
            
            line = line.trim();
            if (line.isEmpty())
            	return nextToken();
            
            StringTokenizer tok = new StringTokenizer(line, "() \t\n\r\f", true);
            String str;
            
            while (tok.hasMoreTokens()) {
            	str = tok.nextToken().trim();
            	if (!str.isEmpty()) 
            		tokenQueue.add(str);
            }
    	}
    	
        return tokenQueue.pop();
    }

}

