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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node as in Penn Treebank.
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/05/2010
 */
public class TBNode
{
    public static final Pattern WORD_PATTERN = Pattern.compile("\\A([^-]+)(-\\d+)?\\z");    
    public static final Pattern POS_PATTERN = Pattern.compile("\\A([^\\-=\\)]+|\\-NONE\\-)((\\-[a-zA-Z]+)*)([\\-=]\\d+)?\\z");
  
    static final TBNode[] NO_CHILDREN = new TBNode[0];
    
    //public static final Pattern POS_PATTERN = Pattern.compile("([a-zA-Z]+|\\-NONE\\-)((\\-[a-zA-Z]+)*)(\\-\\d+)?");    
    
    TBNode            parent;
	String            pos;
	Set<String>       functionTags;
	String            word;
	short             childIndex;
	int               terminalIndex;
	int               tokenIndex;
	TBNode            indexingNode;
	TBNode            head;
	TBNode[]          children;
	
	/**
	 * Initializes the node and sets its parent and pos-tag.
	 * @param parent parent node.
	 * @param pos pos-tag.
	 */
	public TBNode(TBNode parent, String pos)
	{
	    this(parent, pos, (short)-1);
	}
	
	public TBNode(TBNode parent, String pos, short childIndex)
    {
	    this.parent     = parent;
        this.pos        = pos;
        this.childIndex = childIndex;
        terminalIndex   = -1;
        tokenIndex      = -1;
        children        = NO_CHILDREN;
    }
	
	/** Returns true if the pos-tag of the node is <code>pos</code>. */
	public boolean isPos(String pos)
	{
		return this.pos.equals(pos);
	}
	
	/** Returns true if the node is an empty category. */
	public boolean isEC()
	{
		return isPos(TBLib.POS_EC);
	}
	
	public boolean isTrace()
	{
	    return isPos(TBLib.POS_EC)&&indexingNode!=null;
	}
	
	public boolean isTerminal()
	{
		return terminalIndex>=0;
	}
	
	public boolean isToken()
	{
		return tokenIndex>=0;
	}
	
    public boolean isDecendentOf(TBNode ancestor)
    {
        if (parent==null) return false;
        if (parent==ancestor) return true;
        return parent.isDecendentOf(ancestor);
    }

	public String getPOS() {
        return pos;
    }

    public Set<String> getFunctionTags() {
        return functionTags;
    }
    
    public boolean hasFunctionTag(String tag) {
        return functionTags==null?false:functionTags.contains(tag);
    }

    public String getWord() {
        return word;
    }

    public int getTerminalIndex() {
        return terminalIndex;
    }

    public int getTokenIndex() {
        return tokenIndex;
    }

    public short getChildIndex() {
        return childIndex;
    }

    public TBNode getHead() {
        return head;
    }
    
    public String getHeadword() {
        return head==null?null:head.word;
    }

    public TBNode getTrace()
	{
        if (children.length==1)
            return children[0].getTrace();
		return TBLib.POS_EC.equals(pos)?indexingNode:null;
	}
	
	public TBNode getGap() {
        return TBLib.POS_EC.equals(pos)?null:indexingNode;
    }

    /**
	 * Returns the parent node.
	 * If there is none, return null.
	 */
	public TBNode getParent()
	{
		return parent;
	}
	
    public TBNode getRoot()
    {
        if (parent==null) return this;
        return parent.getRoot();
    }
    
    public List<TBNode> getPathToRoot()
    {
        TBNode node = this;
        ArrayList<TBNode> nodeList = new ArrayList<TBNode>();
        do {
            nodeList.add(node);
        } while ((node = node.getParent())!=null);

        return nodeList;
    }
    
    public List<TBNode> getPathToAncestor(TBNode ancestor)
    {
        TBNode node = this;
        ArrayList<TBNode> nodeList = new ArrayList<TBNode>();
        do {
            nodeList.add(node);
        } while (node!=ancestor && (node = node.getParent())!=null);

        if (node!=ancestor) return null;
        
        return nodeList;
    }
    
	public TBNode getAncestor(int level)
	{
	    if (level==0) return this;
	    if (parent==null) return null;
	    return parent.getAncestor(level-1);
	}
	

	/** Returns the list of children nodes. */
	public TBNode[] getChildren()
	{
		return children;
	}
	
	public List<TBNode> getTerminalNodes()
	{
		List<TBNode> tnodes = new ArrayList<TBNode>();
		getNestedNodes(true, tnodes);
		return tnodes;
	}
	
    public List<TBNode> getTokenNodes()
    {
        List<TBNode> tnodes = new ArrayList<TBNode>();
        getNestedNodes(false, tnodes);
        return tnodes;
    }

    void getNestedNodes(boolean onTerminal, List<TBNode> nodes)
    {
        if (onTerminal) {
            if (terminalIndex>=0)
                nodes.add(this);
        } else {
            if (tokenIndex>=0)
                nodes.add(this);
        }
        for (TBNode child: children)
            child.getNestedNodes(onTerminal, nodes);
    }
    
    public TBNode getNodeByTerminalIndex(int terminalIndex)
    {
        if (terminalIndex<0) return null;
        if (this.terminalIndex == terminalIndex)
            return this;
        if (isTerminal()) return null;
        if (-this.terminalIndex<=terminalIndex)
            return null;
        TBNode retNode;
        for (TBNode node:children)
            if ((retNode=node.getNodeByTerminalIndex(terminalIndex))!=null)
                return retNode;

        return null;
    }
    
    public TBNode getNodeByTokenIndex(int tokenIndex)
    {
        if (tokenIndex<0) return null;
        if (this.tokenIndex == tokenIndex)
            return this;
        if (isTerminal()) return null;
        if (-this.tokenIndex<=tokenIndex)
            return null;
        TBNode retNode;
        for (TBNode node:children)
            if ((retNode=node.getNodeByTokenIndex(tokenIndex))!=null)
                return retNode;

        return null;
    }
	
	public BitSet getTerminalSet()
	{
	    BitSet terminalSet = new BitSet(Math.abs(terminalIndex));
	    getIndexSet(true, terminalSet);
	    return terminalSet;
	}

	public BitSet getTokenSet()
    {
        BitSet tokenSet = new BitSet(Math.abs(tokenIndex));
        getIndexSet(false, tokenSet);
        return tokenSet;
    }

	void getIndexSet(boolean onTerminal, BitSet indexSet)
	{
	    if (onTerminal) {
	        if (terminalIndex>=0)
	            indexSet.set(terminalIndex);
	    } else {
	        if (tokenIndex>=0)
                indexSet.set(tokenIndex);
	    }
        
        for (TBNode child: children)
            child.getIndexSet(onTerminal, indexSet);
	}
    
	// this should only be called once when tree is read in
	TBNode findIndexedNode(int idx)
	{
	    Matcher matcher = POS_PATTERN.matcher(pos);
	    matcher.matches();
	    String idxStr = matcher.group(4);
		if (idxStr!=null && idxStr.charAt(0)=='-' && Integer.parseInt(idxStr.substring(1))==idx)
			return this;

		if (children==null) return null;
		
		for (TBNode aNode:children)
			if ((aNode = aNode.findIndexedNode(idx))!=null)
				return aNode;

		return null;
	}
	
	// this should only be called once when tree is read in
	void cleanUpPOS()
	{
	    Matcher matcher = POS_PATTERN.matcher(pos);
	    matcher.matches();
	    pos = matcher.group(1);
	    String[] fTags = matcher.group(2).split("\\-");
	    if (fTags.length>1)
	    {
	        functionTags = new TreeSet<String>();
	        for (int i=1; i<fTags.length;++i)
	            functionTags.add(fTags[i]);
	    }
	    if (children==null) return;
	    for (TBNode child:children)
	        child.cleanUpPOS();
	}
	
	public String toParse()
	{
		StringBuilder str = new StringBuilder();
		str.append('(');
		str.append(pos);
		if (functionTags!=null)
		    for(String tag:functionTags)
		        str.append('-'+tag);
		if (word!=null) str.append(' '+word);
		
        for (TBNode node : children)
            str.append(node.toParse());

        str.append(')');
		return str.toString();
	}
	
	public String toString()
	{
		StringBuilder str = new StringBuilder();
		if (isTrace())
		    str.append("["+getTrace()+"] ");
		else if (word!=null)
			str.append(word+" ");
		
		for (TBNode node : children)
			str.append(node);
		
		return str.toString();
	}
}
