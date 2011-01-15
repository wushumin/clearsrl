/**
* Copyright (c) 2007, Regents of the University of Colorado
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
* Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/
package clearcommon.treebank;

import gnu.trove.TIntArrayList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree as in Penn Treebank.
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/05/2010
 */
public class TBTree implements Serializable
{   
    String filename;
    int    index;
	TBNode rootNode;
	int    terminalCount;
	int    tokenCount;
	
    public String getFilename() {
        return filename;
    }

    public int getIndex() {
        return index;
    }
	
	public int getTerminalCount() {
		return terminalCount;
	}

	public int getTokenCount() {
		return tokenCount;
	}

	/**
	 * Initializes the tree with the root node.
	 * @param root root node
	 * @throws ParseException 
	 */
	//public TBTree(TBNode root)
	//{
		
	//}
	
	public TBTree(String treeFile, int treeIndex, TBNode root, int terminalCount, int tokenCount) throws ParseException
	{
	    this.filename      = treeFile;
	    this.index     = treeIndex;
		this.rootNode      = root;
		this.terminalCount = terminalCount;
		this.tokenCount    = tokenCount;
		linkIndices(root);
	}
	
	public TBNode getRootNode()
	{
		return rootNode;
	}
	
/*
	public TBNode getNodeByTerminalIndex(int terminalIndex)
	{
	    return rootNode.getTerminalNodes().get(terminalIndex);
	}
	
	
	public TBNode getNodeByTokenIndex(int tokenIndex)
    {
        return rootNode.getTokenNodes().get(tokenIndex);
    }
*/	
	
	protected void linkIndices(TBNode node) throws ParseException
	{
	    Matcher matcher = TBNode.POS_PATTERN.matcher(node.pos);
        matcher.matches();
        String idxStr = matcher.group(4);
        if (idxStr != null && idxStr.charAt(0)=='=')
        {
            node.indexingNode = rootNode.findIndexedNode(Integer.parseInt(idxStr.substring(1)));
            if (node.indexingNode==null)
                throw new ParseException("Missing antecedent: "+idxStr);
        }
        else if (node.isEC())
        {    
            matcher = TBNode.WORD_PATTERN.matcher(node.word);
    	    matcher.matches();
    
    	    idxStr = matcher.group(2);
    	    if (idxStr != null)
    	    {
    	        node.indexingNode = rootNode.findIndexedNode(Integer.parseInt(idxStr.substring(1)));
    	        if (node.indexingNode==null)
    	            throw new ParseException("Missing antecedent: "+idxStr);
    	    }
        }
	    
	    if (node.children==null) return;
		for (TBNode aNode:node.children)
			linkIndices(aNode); 
	}
	
	public String toString()
	{
		return rootNode.toParse();
	}
	
}
