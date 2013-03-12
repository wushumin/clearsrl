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
import java.util.logging.Logger;
import java.util.regex.Matcher;

import clearcommon.propbank.PBFileReader;

/**
 * Penn Treebank tree
 * 
 * @author Shumin Wu
 */
public class TBTree implements Serializable {
	/**
     * 
     */
	private static final long serialVersionUID = 8010593996914604986L;

	private static Logger logger = Logger.getLogger(PBFileReader.class
			.getPackage().getName());

	String filename;
	int index;
	TBNode rootNode;
	int terminalCount;
	int tokenCount;

	public TBTree(String treeFile, int treeIndex, TBNode root,
			int terminalCount, int tokenCount) throws ParseException {
		this.filename = treeFile;
		this.index = treeIndex;
		this.rootNode = root;
		this.terminalCount = terminalCount;
		this.tokenCount = tokenCount;
		if (rootNode != null) {
			linkIndices(root);
			rootNode.cleanUpPOS();
		}
	}

	public String getFilename() {
		return filename;
	}

	public int getIndex() {
		return index;
	}

	public TBNode getRootNode() {
		return rootNode;
	}

	public int getTerminalCount() {
		return terminalCount;
	}

	public int getTokenCount() {
		return tokenCount;
	}

	void linkIndices(TBNode node) throws ParseException {
		Matcher matcher = TBNode.POS_PATTERN.matcher(node.pos);
		if (!matcher.matches()) return;
		String idxStr = matcher.group(6);
		if (idxStr != null) {
			node.indexingNode = rootNode.findIndexedNode(Integer
					.parseInt(idxStr.substring(1)));
			if (node.indexingNode == null) {
				node.pos = node.pos + "-" + idxStr.substring(1);
				logger.warning(filename + ", " + index
						+ ": Missing antecedent: " + idxStr);
			}
		} else if (node.isEC()) {
			matcher = TBNode.WORD_PATTERN.matcher(node.word);
			matcher.matches();

			idxStr = matcher.group(2);
			if (idxStr != null) {
				node.indexingNode = rootNode.findIndexedNode(Integer
						.parseInt(idxStr.substring(1)));
				if (node.indexingNode == null) {
					node.pos = node.pos + "-" + idxStr.substring(1);
					logger.warning(filename + ", " + index
							+ ": Missing antecedent: " + idxStr);
				}
			}
		}

		if (node.children == null)
			return;
		for (TBNode aNode : node.children)
			linkIndices(aNode);
	}

	@Override
	public String toString() {
		return "( " + rootNode.toParse() + " )";
	}

	public String toText(boolean wTerminal) {
		return rootNode.toText(wTerminal);
	}

	public String toDependence(boolean wTerminal) {
		return rootNode.toDependence(wTerminal);
	}

	
}
