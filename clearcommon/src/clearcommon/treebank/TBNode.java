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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Penn Treebank node
 * 
 * @author Shumin Wu
 */
/**
 * @author shumin
 *
 */
public class TBNode implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -1925614518382689283L;

    public static final Pattern WORD_PATTERN = Pattern
            .compile("\\A([^-]+)(-\\d+)?\\z");
    public static final Pattern POS_PATTERN = Pattern
            .compile("\\A([^-\\=\\)]+|-NONE-|-LRB-|-RRB-|-LSB-|-RSB-)((-[a-zA-Z]+)*)((-\\d+)*(\\=\\d+)?(-\\d+)*)\\z");

    static final TBNode[] NO_CHILDREN = new TBNode[0];

    // public static final Pattern POS_PATTERN =
    // Pattern.compile("([a-zA-Z]+|\\-NONE\\-)((\\-[a-zA-Z]+)*)(\\-\\d+)?");

    TBNode parent;
    String pos;
    Set<String> functionTags;
    String word;
    short childIndex;
    int terminalIndex;
    int tokenIndex;
    TBNode indexingNode;
    TBNode[] children;

    TBNode head;            // head of this terminal (self) or constituent    
    String depLabel;        // only for terminals
    TBNode headConstituent; // highest level constituent headed by this terminal
    

    /**
     * Initializes the node and sets its parent and pos-tag.
     * 
     * @param parent
     *            parent node.
     * @param pos
     *            pos-tag.
     */
    public TBNode(TBNode parent, String pos) {
        this(parent, pos, (short) -1);
    }

    public TBNode(TBNode parent, String pos, short childIndex) {
        this.parent = parent;
        this.pos = pos;
        this.childIndex = childIndex;
        terminalIndex = -1;
        tokenIndex = -1;
        children = NO_CHILDREN;
    }

    // this should only be called once when tree is read in
    void cleanUpPOS() {
        Matcher matcher = POS_PATTERN.matcher(pos);
        if (!matcher.matches()) return;
        pos = matcher.group(1);
        String[] fTags = matcher.group(2).split("\\-");
        if (fTags.length > 1) {
            functionTags = new TreeSet<String>();
            for (int i = 1; i < fTags.length; ++i)
                functionTags.add(fTags[i]);
        }
        if (children == null)
            return;
        for (TBNode child : children)
            child.cleanUpPOS();
    }

    TBNode findBoundaryNode(boolean onTerminal, boolean start) {
        if (isTerminal())
            return onTerminal || isToken() ? this : null;

        TBNode node;
        if (start) {
            for (TBNode child : children)
                if ((node = child.findBoundaryNode(onTerminal, start)) != null)
                    return node;
        } else {
            for (int i = children.length - 1; i >= 0; --i)
                if ((node = children[i].findBoundaryNode(onTerminal, start)) != null)
                    return node;
        }
        return null;
    }

    // this should only be called once when tree is read in
    TBNode findIndexedNode(int idx) {
        Matcher matcher = POS_PATTERN.matcher(pos);
        matcher.matches();
        String idxStr = matcher.group(4);
        if (idxStr != null) {
            String[] indices = idxStr.split("(?=[-\\=])");
            for (String index : indices)
                if (!index.isEmpty() && index.charAt(0) == '-'
                        && Integer.parseInt(index.substring(1)) == idx)
                    return this;
        }
        if (children == null)
            return null;

        for (TBNode aNode : children)
            if ((aNode = aNode.findIndexedNode(idx)) != null)
                return aNode;

        return null;
    }
    
    public TBNode getAncestor(int level) {
        if (level == 0)
            return this;
        if (parent == null)
            return null;
        return parent.getAncestor(level - 1);
    }

    public short getChildIndex() {
        return childIndex;
    }

    /** Returns the list of children nodes. */
    public TBNode[] getChildren() {
        return children;
    }

    /**
     * returns the highest level constituent with the same head
     * 
     * @return
     */
    public TBNode getConstituentByHead() {
        return head.headConstituent;
    }

    
    /**
     * Get nodes (currently only tokens) that dependents of the head node of
     * this constituent
     * @return
     */
    public List<TBNode> getDependentNodes() {
        return getDependentNodes(false);
    }
    
    public List<TBNode> getDependentNodes(boolean terminal) {
        ArrayList<TBNode> dependents = new ArrayList<TBNode>();
        headConstituent.findDependentNodes(this, dependents, terminal);
        return dependents;
    }
    
    void findDependentNodes(TBNode head, List<TBNode> dependents, boolean terminal) {
    	for (TBNode child:children) { 
    		if (!terminal && !child.head.isToken())
    			continue;
    		if (child.head.getHeadOfHead()==head)
    			dependents.add(child);
    		else if (child.head==head)
    			child.findDependentNodes(head, dependents, terminal);
    	}
    }
    
    public String getECType() {
        if (!isEC())
            return word;

        Matcher m = WORD_PATTERN.matcher(word);
        m.matches();
        return m.group(1);
    }

    /**
     * get the last terminal in the constituent
     * 
     * @return the last terminal in the constituent
     */
    public TBNode getEndTerminal() {
        return findBoundaryNode(true, false);
    }

    /**
     * get the last token in the constituent
     * 
     * @return the last token in the constituent
     */
    public TBNode getEndToken() {
        return findBoundaryNode(false, false);
    }

    public List<String> getFunctionTaggedPOS() {
        if (functionTags == null)
            return Arrays.asList(pos);

        List<String> retList = new ArrayList<String>(1 + functionTags.size());
        retList.add(pos);
        for (String tag : functionTags)
            retList.add(pos + "-" + tag);
        return retList;
    }

    public Set<String> getFunctionTags() {
        return functionTags;
    }

    public TBNode getGap() {
        return TBLib.POS_EC.equals(pos) ? null : indexingNode;
    }

    public TBNode getHead() {
        return isTerminal()?this:head;
    }

    /**
     * Get the head node of the head of this constituent
     * @return
     */
    public TBNode getHeadOfHead() {
        return headConstituent.parent == null ? null
                : headConstituent.parent.head;
    }

    public String getHeadword() {
        return head == null ? null : head.word;
    }

    void getIndexSet(boolean onTerminal, BitSet indexSet) {
        if (onTerminal) {
            if (terminalIndex >= 0)
                indexSet.set(terminalIndex);
        } else {
            if (tokenIndex >= 0)
                indexSet.set(tokenIndex);
        }

        for (TBNode child : children)
            child.getIndexSet(onTerminal, indexSet);
    }

    public int getLevelToRoot() {
        int level = 0;
        TBNode ancestor = this;
        while ((ancestor=ancestor.parent)!=null)
            ++level;
        return level;
    }
    
    public int getLevelToNode(TBNode node) {
        int level = 0;
        TBNode ancestor = this;
        while (ancestor!=node && (ancestor=ancestor.parent)!=null)
            ++level;
        if (ancestor!=node)
            return -1;
        return level;
    }
    
    public TBNode getLowestCommonAncestor(TBNode node) {
        List<TBNode> lhs = getPathToRoot();
        List<TBNode> rhs = node.getPathToRoot();

        if (lhs.size() > rhs.size()) {
            List<TBNode> temp = lhs;
            lhs = rhs;
            rhs = temp;
        }

        for (int i = 0; i < lhs.size(); ++i)
            if (lhs.get(i) == rhs.get(i + rhs.size() - lhs.size()))
                return lhs.get(i);

        return null;
    }

    void fillNodeArray(boolean onTerminal, TBNode[] nodes) {
        if (onTerminal) {
            if (terminalIndex >= 0)
                nodes[terminalIndex] = this;
        } else {
            if (tokenIndex >= 0)
                nodes[tokenIndex] = this;
        }
        for (TBNode child : children)
            child.fillNodeArray(onTerminal, nodes);
    }
    
    void getNestedNodes(boolean onTerminal, List<TBNode> nodes) {
        if (onTerminal) {
            if (terminalIndex >= 0)
                nodes.add(this);
        } else {
            if (tokenIndex >= 0)
                nodes.add(this);
        }
        for (TBNode child : children)
            child.getNestedNodes(onTerminal, nodes);
    }

    /**
     * Equivalent to
     * getNodeByTerminalIndex(terminalIndex).getAncestor(ancestorLevel) but with
     * null checking
     * 
     * @param terminalIndex
     * @param ancestorLevel
     * @return
     */
    public TBNode getNode(int terminalIndex, int ancestorLevel) {
        TBNode node = getNodeByTerminalIndex(terminalIndex);
        return node == null ? null : node.getAncestor(ancestorLevel);
    }

    public TBNode getNodeByTerminalIndex(int terminalIndex) {
        if (terminalIndex < 0)
            return null;
        if (this.terminalIndex == terminalIndex)
            return this;
        if (isTerminal())
            return null;
        if (-this.terminalIndex <= terminalIndex)
            return null;
        TBNode retNode;
        for (TBNode node : children)
            if ((retNode = node.getNodeByTerminalIndex(terminalIndex)) != null)
                return retNode;

        return null;
    }

    public TBNode getNodeByTokenIndex(int tokenIndex) {
        if (tokenIndex < 0)
            return null;
        if (this.tokenIndex == tokenIndex)
            return this;
        if (isTerminal())
            return null;
        if (-this.tokenIndex <= tokenIndex)
            return null;
        TBNode retNode;
        for (TBNode node : children)
            if ((retNode = node.getNodeByTokenIndex(tokenIndex)) != null)
                return retNode;

        return null;
    }

    /**
     * Returns the parent node. If there is none, return null.
     */
    public TBNode getParent() {
        return parent;
    }
    
    public ArrayList<TBNode> getDepPath(TBNode node) {
    	return getPath(node, true);
    }
    
    public ArrayList<TBNode> getPath(TBNode node) {
        return getPath(node, false);
    }
    
    ArrayList<TBNode> getPath(TBNode node, boolean dependency) {
    	ArrayList<TBNode> lhs = getPathToRoot(dependency);
        ArrayList<TBNode> rhs = node.getPathToRoot(dependency);
        
        TBNode anchor=null;
        
        while (!lhs.isEmpty()&&!rhs.isEmpty()) {
            if (lhs.get(lhs.size()-1)!=rhs.get(rhs.size()-1))
                break;
            anchor = lhs.get(lhs.size()-1);
            lhs.remove(lhs.size()-1);
            rhs.remove(rhs.size()-1);
        }
        if (anchor==null)
            return null;
        
        lhs.add(anchor);
        for (int i=rhs.size()-1; i>=0; --i)
            lhs.add(rhs.get(i));
        
        return lhs;
    }
    
    public ArrayList<TBNode> getPathToAncestor(TBNode ancestor) {
        TBNode node = this;
        ArrayList<TBNode> nodeList = new ArrayList<TBNode>();
        do {
            nodeList.add(node);
        } while (node != ancestor && (node = node.getParent()) != null);

        if (node != ancestor)
            return null;

        return nodeList;
    }

    public ArrayList<TBNode> getPathToRoot() {
    	return getPathToRoot(false);
    }
    
    public ArrayList<TBNode> getDepPathToRoot() {
    	return getPathToRoot(true);
    }
    
    ArrayList<TBNode> getPathToRoot(boolean dependency) {
        TBNode node = this;
        ArrayList<TBNode> nodeList = new ArrayList<TBNode>();
        do {
            nodeList.add(node);
        } while ((node = (dependency?node.getHeadOfHead():node.getParent())) != null);

        return nodeList;
    }

    public String getPOS() {
        return pos;
    }

    public TBNode getRoot() {
        if (parent == null)
            return this;
        return parent.getRoot();
    }

    /**
     * get the first terminal in the constituent
     * 
     * @return the first terminal in the constituent
     */
    public TBNode getStartTerminal() {
        return findBoundaryNode(true, true);
    }

    /**
     * get the first token in the constituent
     * 
     * @return the first token in the constituent
     */
    public TBNode getStartToken() {
        return findBoundaryNode(false, true);
    }

    public int getTerminalIndex() {
        return terminalIndex;
    }

    public List<TBNode> getTerminalNodes() {
        List<TBNode> tnodes = new ArrayList<TBNode>();
        getNestedNodes(true, tnodes);
        return tnodes;
    }

    public BitSet getTerminalSet() {
        BitSet terminalSet = new BitSet(Math.abs(terminalIndex));
        getIndexSet(true, terminalSet);
        return terminalSet;
    }

    public int getTokenIndex() {
        return tokenIndex;
    }

    public List<TBNode> getTokenNodes() {
        List<TBNode> tnodes = new ArrayList<TBNode>();
        getNestedNodes(false, tnodes);
        return tnodes;
    }

    public BitSet getTokenSet() {
        BitSet tokenSet = new BitSet(Math.abs(tokenIndex));
        getIndexSet(false, tokenSet);
        return tokenSet;
    }

    public TBNode getTrace() {
        if (children.length == 1)
            return children[0].getTrace();
        return TBLib.POS_EC.equals(pos) ? indexingNode : null;
    }

    public String getWord() {
        return word;
    }

    public boolean hasFunctionTag(String tag) {
        return functionTags == null ? false : functionTags.contains(tag);
    }

    public boolean isDecendentOf(TBNode ancestor) {
        if (parent == null)
            return false;
        if (parent == ancestor)
            return true;
        return parent.isDecendentOf(ancestor);
    }

    /** Returns true if the node is an empty category. */
    public boolean isEC() {
        return isPos(TBLib.POS_EC);
    }

    /** Returns true if the pos-tag of the node is <code>pos</code>. */
    public boolean isPos(String pos) {
        return this.pos.equals(pos);
    }

    public boolean isTerminal() {
        return children.length == 0;
    }

    public boolean isToken() {
        return tokenIndex >= 0;
    }

    public boolean isTrace() {
        if (children.length == 1)
            return children[0].isTrace();
        return isPos(TBLib.POS_EC) && indexingNode != null;
    }

    void linkDependency() {
        headConstituent = this;
        while (headConstituent.parent != null
                && headConstituent.parent.head == head)
            headConstituent = headConstituent.parent;
    }

    public String toParse() {
        StringBuilder str = new StringBuilder();
        str.append('(');
        str.append(pos);
        if (functionTags != null)
            for (String tag : functionTags)
                str.append('-' + tag);
        if (word != null)
            str.append(' ' + word);

        for (TBNode node : children)
            str.append(" " + node.toParse());

        str.append(')');
        return str.toString();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        if (isTrace())
            str.append("[" + getTrace() + "]");
        else if (word != null)
            str.append(word);

        for (TBNode node : children)
            str.append(" " + node);

        return str.toString();
    }

    public String toText() {
        return toText(false);
    }

    /**
     * Similar to toString() but w/o trace expansion
     * @param wTerminal whether to print terminals
     * @return
     */
    public String toText(boolean wTerminal) {
        StringBuilder str = new StringBuilder();
        for (TBNode node : (wTerminal ? getTerminalNodes() : getTokenNodes()))
            str.append(node.getWord() + ' ');
        return str.toString();
    }

    public String toDependence(boolean wTerminal) {
        StringBuilder str = new StringBuilder();
        for (TBNode node : (wTerminal ? getTerminalNodes() : getTokenNodes()))
            str.append(node.getWord() + "_" + (wTerminal?node.getTerminalIndex():node.getTokenIndex())+"/"+(node.getHeadOfHead()==null?-1:(wTerminal?node.getHeadOfHead().getTerminalIndex():node.getHeadOfHead().getTokenIndex()))+' ');
        return str.toString();
    }

	public String getDepLabel() {
	    return depLabel;
    }

	public String toPrettyParse(int indent) {
	    // TODO Auto-generated method stub
		StringBuilder str = new StringBuilder();
		
		if (childIndex>0)
			for (int i=0; i<indent; ++i)
				str.append(' ');
		
        str.append('(');
        str.append(pos);
        if (functionTags != null)
            for (String tag : functionTags)
                str.append('-' + tag);
        if (word != null)
            str.append(' ' + word);

        int slen = str.length()+1;
        
        for (TBNode node : children) {
            str.append(" " + node.toPrettyParse(childIndex==0?slen+indent:slen));
            if (node.childIndex!=children.length-1)
            	str.append('\n');	
        }
        str.append(')');
        return str.toString();
    }

	public TBNode findHead() {
		if (isTerminal())
			return head = this;

		TBNode headChild = null;
		for (TBNode child:children)
			if (child.hasFunctionTag("HEAD")) {
				headChild = child;
				break;
			}
		// sentence not head annotated
		if (headChild==null) return null;
		for (TBNode child:children)
			if (child==headChild) {
				child.headConstituent = this.headConstituent;
				this.head = child.findHead();
			} else {
				child.headConstituent = child;
				child.findHead();
			}
		return this.head;
    }
}
