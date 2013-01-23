package clearcommon.treebank;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import clearcommon.propbank.PBFileReader;

public class SerialTBFileReader extends TBFileReader
{
	private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());

	private static String WHITESPACE = " \t\n\r\f";
	
	
	Scanner       scanner;
	int           treeCount;
	TBTree        lastTree;
	StringBuilder inputStr;
	
	/**
	 * Initializes the Treebank reader.
	 * @param filename name of the Treebank file
	 * @throws IOException 
	 */
	public SerialTBFileReader(String fileName) throws IOException
    {
	    this(fileName.endsWith(".gz")?new InputStreamReader(new GZIPInputStream(new FileInputStream(fileName))):new FileReader(fileName), fileName);
    }
	
	public SerialTBFileReader(String dirName, String fileName) throws IOException
    {
	    this(fileName.endsWith(".gz")?new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(dirName, fileName)))):new FileReader(new File(dirName, fileName)), fileName);
    }

	public SerialTBFileReader(Reader reader)
	{
		this(reader, null);
	}
	
	public SerialTBFileReader(Reader reader, String fileName)
	{
		super(fileName);
        scanner      = new Scanner(reader);
        scanner.useDelimiter(String.format("((?<=[\\(\\)%s])|(?=[\\(\\)%s]))",WHITESPACE, WHITESPACE));
        
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
		} while (!str.equals(TBLib.LRB));
		
		Stack<List<TBNode>> childNodeStack = new Stack<List<TBNode>>();
		childNodeStack.push(new ArrayList<TBNode>());
		
		int terminalIndex = 0;
		int tokenIndex    = 0;
		TBNode head       = new TBNode(null, "FRAG");		// dummy-head
		TBNode curr       = head;						// pointer to the current node
		
		do {
			if ((str = nextToken()) == null)
				throw new ParseException(fileName+", "+treeCount+": more tokens needed");
			//System.out.println(str);
			
			if (str.equals(TBLib.LRB)) {
				if ((str = nextToken()) == null)		// str = pos-tag
					throw new ParseException(fileName+", "+treeCount+": POS-tag is missing, Read \""+inputStr.toString()+"\"");
				if (!TBNode.POS_PATTERN.matcher(str).matches())
				    throw new ParseException(fileName+", "+treeCount+": Malformed POS tag: "+str);
				
				TBNode childNode = new TBNode(curr, str, (short)(childNodeStack.peek().size()));
				childNodeStack.peek().add(childNode);
				
				curr = childNode;                           // move to child
				childNodeStack.push(new ArrayList<TBNode>());
			} else if (str.equals(TBLib.RRB)) {
				if (curr.terminalIndex<0) {
				    curr.terminalIndex = -(terminalIndex+1);
	                curr.tokenIndex = -(tokenIndex+1);
				}
				
				curr.children= childNodeStack.pop().toArray(TBNode.NO_CHILDREN);
				curr = curr.getParent();				// move to parent
				
			} else if (head==curr) {
				head.pos = str;
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
					logger.warning(fileName+", "+treeCount+": multi-word token: "+pNode.word+" "+str+"("+pNode.pos+")");
				}
				curr.word = str;						// str = word
				curr.terminalIndex = terminalIndex++;
				if (!curr.isEC())	curr.tokenIndex = tokenIndex++;
			}
		}
		while (!childNodeStack.isEmpty());
		
		// omit the dummy head
		TBNode tmp = head.children.length==1?head.children[0]:head;
		
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
			scanner.close();
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
		while (scanner.hasNext())
		{
			String str = scanner.next();
			
			inputStr.append(str);
			
			if (WHITESPACE.indexOf(str) == -1)
				return str;
		}
		close();
		return null;
	}

}

