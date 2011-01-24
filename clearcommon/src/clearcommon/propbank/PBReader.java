/**
* Copyright (c) 2007-2009, Regents of the University of Colorado
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
package clearcommon.propbank;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TreeFileResolver;
import clearcommon.treebank.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;


/**
 * 'PBReader' reads a Propbank annotation file and stores all information from both treebank 
 * and annotation into vectors.
 * It also provides operations to access and manage the information.
 * The following show the annotation format.
 * <pre>
 * TERMS:
 * s# = sentence number of the tree to be extracted
 * t# = terminal number (start from 0)
 * a# = ancestor number (start from 0)
 * i.e. t#:a# = a#'th ancestor from the t#'th terminal
 *      3:0 = 0th ancestor from the 4th terminal = 4th terminal
 *      0:1 = 1st ancestor from the 1st terminal
 * tr = * (trace)
 * jo = , (join)
 * sc = ; (?)
 * ARG = argument tag
 * 
 * FORMAT:
 * treebank-path s# t#_of_predicate annotator predicate_lemma -----  [t#:a#[tr|jo|sct#:a#]*-ARG]+
 * i.e.
 * ebn/ebn_0001.mrg 0 10 gold keep.XX -----  5:1*9:0-ARG0 10:0-rel 11:1,13:2-ARG1
 * </pre>
 * @since 09/13/07
 */
public class PBReader
{
    String                annotationFile;
	String                treePath;
	Scanner               scanner;
	Map<String, TBTree[]> treeMap;
	TreeFileResolver      filenameResolver;
	
	/**
	 * Opens 'annotationFile', finds trees from 'treebankPath', and collects information.
	 * @param annotationFile the path of the annotation file.
	 * @param treebankPath the path of the treebank.
	 * @throws FileNotFoundException 
	 */
	public PBReader(String annotationFile, String treebankPath) throws FileNotFoundException
	{
	    this(annotationFile,treebankPath, null, null);
	}
	
	public PBReader(String annotationFile, String treebankPath, Map<String, TBTree[]> trees) throws FileNotFoundException
	{
		this(annotationFile,treebankPath, trees, null);
	}

	public PBReader(String annotationFile, String treebankPath, Map<String, TBTree[]> trees, TreeFileResolver resolver) throws FileNotFoundException
    {
	    this.annotationFile = annotationFile;
        treePath            = treebankPath;
        scanner             = new Scanner(new BufferedReader(new FileReader(annotationFile)));
        treeMap             = (trees==null?new HashMap<String, TBTree[]>():trees);
        filenameResolver    = resolver;
    }
		
	public Map<String, TBTree[]> getTrees()
	{
	    return treeMap;
	}
	
	public PBInstance nextProp() throws PBFormatException
	{
		if (!scanner.hasNextLine())	
		{
			scanner.close();
			return null;
		}
		String line = scanner.nextLine().trim();
		String[] tokens = line.split("[ \t]+");
		System.out.println(Arrays.toString(tokens));
		int t=0;
		
		PBInstance instance = new PBInstance();
		
		String treeFile     = (filenameResolver==null?tokens[0]:filenameResolver.resolve(annotationFile, tokens[0]));
	    TBTree[] trees      = null;
        if ((trees = treeMap.get(treeFile))==null)
        {
            try {
                System.out.println("Reading "+treePath+File.separatorChar+treeFile);
                TBReader tbreader    = new TBReader(treePath, treeFile);
                ArrayList<TBTree> a_tree = new ArrayList<TBTree>();
                TBTree tree;
                while ((tree = tbreader.nextTree()) != null)
                    a_tree.add(tree);
                trees = a_tree.toArray(new TBTree[a_tree.size()]);
                
                treeMap.put(treeFile, trees);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
		
		int treeIndex       = Integer.parseInt(tokens[1]);
		int predicateIndex  = Integer.parseInt(tokens[2]);
		
		instance.tree = trees[treeIndex];
		instance.predicateNode = instance.tree.getRootNode().getNodeByTerminalIndex(predicateIndex);
		
		t = 3; // skip "gold" or annotator initial before roleset id
		while (!tokens[t].matches("[^\\.-]+(\\.\\w{2}|-[nv])")) ++t;
		
		if (tokens[t].endsWith("-v"))
		    instance.rolesetId = tokens[++t];// skip roleset-v for ontonotes
		else if (tokens[t].endsWith("-n"))
		    return nextProp(); // skip nominalization for now
		else
		    instance.rolesetId = tokens[t];
		
		++t;              // skip "-----"
		
		List<PBArg> argList = new LinkedList<PBArg>();

		for (++t;t<tokens.length; ++t)
		{
		    if (!tokens[t].matches(PBArg.ARG_PATTERN))
		        throw new PBFormatException("malformed argument: "+tokens[t]);
		    
            int idx    = tokens[t].indexOf(PBLib.ARG_DELIM);
            
            String label = tokens[t].substring(idx+1);
            if (!label.matches(PBArg.LABEL_PATTERN))
                throw new PBFormatException("unrecognized argument label: "+label);

            List<TBNode> nodeList = new ArrayList<TBNode>();
            List<TBNode> nestedNodeList = new ArrayList<TBNode>();
            String[] locs = tokens[t].substring(0, idx).split("(?=[\\*,;&])");
            
            String[] loc      = locs[0].split(":");
            nodeList.add(instance.tree.getRootNode().getNodeByTerminalIndex(Integer.parseInt(loc[0])).getAncestor(Integer.parseInt(loc[1])));
            
            for (int i=1; i<locs.length; ++i)
            {
                loc = locs[i].substring(1).split(":");
                TBNode node = instance.tree.getRootNode().getNodeByTerminalIndex(Integer.parseInt(loc[0])).getAncestor(Integer.parseInt(loc[1]));
                if (locs[i].charAt(0)!='*')
                    nestedNodeList.add(node);
                else
                    nodeList.add(node);
            }
            
            PBArg arg = new PBArg(label);
            arg.tokenNodes = nodeList.toArray(new TBNode[nodeList.size()]);
            
            if (!nestedNodeList.isEmpty())
            {
                arg.nestedArgs = new PBArg[nestedNodeList.size()];
                for (int i=0; i<nestedNodeList.size(); ++i)
                {
                    arg.nestedArgs[i] = new PBArg("C-"+label);
                    arg.nestedArgs[i].tokenNodes = new TBNode[1];
                    arg.nestedArgs[i].tokenNodes[0] = nestedNodeList.get(i);
                }
            }
            argList.add(arg);
		}
		
		PBArg linkArg;
		for (Iterator<PBArg> iter=argList.iterator(); iter.hasNext();)
		{
		    linkArg = iter.next();
		    if (!linkArg.getLabel().startsWith("LINK")) continue;

	        boolean found = false;
	        boolean isSLC = linkArg.isLabel("LINK-SLC");
	        if (isSLC && linkArg.tokenNodes.length!=2) throw new PBFormatException("LINK-SLC size incorrect "+linkArg.tokenNodes);

	        for (PBArg arg:argList)
            {
	            if (linkArg==arg) continue;
	            for (TBNode node: arg.tokenNodes)
	            {
	                for (TBNode linkNode:linkArg.tokenNodes)
	                {
	                    if (node == linkNode)
	                    {
	                        found = true;
	                        if (isSLC)
	                        {
	                            linkArg.label = "R-"+arg.label;
	                            linkArg.linkingArg = arg;
	                            linkArg.tokenNodes = Arrays.asList(linkArg.tokenNodes[0]==node?linkArg.tokenNodes[1]:linkArg.tokenNodes[0]).toArray(new TBNode[1]);
	                        } else {
	                            List<TBNode> nodeList = new ArrayList<TBNode>(Arrays.asList(arg.tokenNodes));
	                            for (TBNode aNode:linkArg.tokenNodes)
	                                if (aNode!=linkNode) nodeList.add(aNode);
	                            arg.tokenNodes = nodeList.toArray(new TBNode[nodeList.size()]);
	                        }
	                        break;
	                    }
	                }
	                if (found) break;
	            }
	            if (found) break;
            }
	        if (!found) throw new PBFormatException(linkArg.label+" not resolved "+linkArg.tokenNodes);
	        if (!isSLC) iter.remove();

		}
		
		for (PBArg arg:argList)
		    arg.processNodes();
		
		instance.allArgs = argList.toArray(new PBArg[argList.size()]); 
		
		argList.clear();
		for (PBArg arg:instance.allArgs)
		    if (!arg.isEmpty()) argList.add(arg);
		
		instance.args = argList.toArray(new PBArg[argList.size()]);
		
		return instance;
	}
	
	public void close()
	{
	    scanner.close();
	}
}
