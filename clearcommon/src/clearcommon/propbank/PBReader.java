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
import clearcommon.util.JIO;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
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
	 */
	public PBReader(String annotationFile, String treebankPath)
	{
	    this(annotationFile,treebankPath, null, null);
	}
	
	public PBReader(String annotationFile, String treebankPath, Map<String, TBTree[]> trees)
	{
		this(annotationFile,treebankPath, trees, null);
	}

	public PBReader(String annotationFile, String treebankPath, Map<String, TBTree[]> trees, TreeFileResolver resolver)
    {
	    this.annotationFile = annotationFile;
        treePath            = treebankPath;
        scanner             = JIO.createScanner(annotationFile);
        treeMap             = (trees==null?new HashMap<String, TBTree[]>():trees);
        filenameResolver    = resolver;
    }
		
	public Map<String, TBTree[]> getTrees()
	{
	    return treeMap;
	}
	
	public PBInstance nextProp()
	{
		if (!scanner.hasNextLine())	
		{
			scanner.close();
			return null;
		}
		StringTokenizer tok = new StringTokenizer(scanner.nextLine());
		PBInstance instance = new PBInstance();
		
		String treeFile     = (filenameResolver==null?tok.nextToken():filenameResolver.resolve(annotationFile, tok.nextToken()));
		int treeIndex       = Integer.parseInt(tok.nextToken());
		int predicateIndex  = Integer.parseInt(tok.nextToken());
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
		instance.tree = trees[treeIndex];
		instance.predicateNode = instance.tree.getRootNode().getNodeByTerminalIndex(predicateIndex);
		
		instance.rolesetId = tok.nextToken();
		
		if (!instance.rolesetId.matches("[~\\.-]++(\\.\\w{2}|-[nv])"))
			instance.rolesetId = tok.nextToken(); // skip "gold" or annotator initial before roleset id
		
		if (instance.rolesetId.endsWith("-v"))
		    instance.rolesetId = tok.nextToken();// skip roleset-v for ontonotes
		else if (instance.rolesetId.endsWith("-n"))
		    return nextProp(); // skip nominalization for now
		
		List<PBArg> argList = new LinkedList<PBArg>();
		
		tok.nextToken();	            // skip "-----"
		//System.out.print(instance.rolesetId+" ");
		while (tok.hasMoreTokens())
		{
			String argStr = tok.nextToken();
			int    idx    = argStr.indexOf(PBLib.ARG_DELIM);
			
			String label = argStr.substring(idx+1);
			if (label.startsWith("ARGM") && !label.toUpperCase().equals(label)) continue;
            if (label.equals("LINK-PCR")) continue;
			
            List<TBNode> nodeList = new ArrayList<TBNode>();
            
			String locs = argStr.substring(0, idx);
			
			StringTokenizer tokLocs = new StringTokenizer(locs, PBLib.ARG_OP);
			
			while (tokLocs.hasMoreTokens())
			{
				String[] loc      = tokLocs.nextToken().split(":");
				int terminalIndex = Integer.parseInt(loc[0]);
				int height        = Integer.parseInt(loc[1]);
				
				nodeList.add(instance.tree.getRootNode().getNodeByTerminalIndex(terminalIndex).getAncestor(height));
			}
			PBArg arg = new PBArg(label);
			arg.tokenNodes = nodeList.toArray(new TBNode[nodeList.size()]);	
			argList.add(arg);
		}
		
		PBArg linkArg;
		for (int i=0; i<argList.size(); ++i)
		{
		    if ((linkArg=argList.get(i)).isLabel("LINK-SLC"))
            {
		        if (linkArg.tokenNodes.length!=2) System.err.println("LINK-SLC size incorrect "+linkArg.tokenNodes);

		        for (PBArg arg:argList)
                {
		            if (linkArg==arg) continue;
		            for (TBNode node: arg.tokenNodes)
		            {
		                for (TBNode linkNode:linkArg.tokenNodes)
		                {
		                    if (node == linkNode)
		                    {
		                        linkArg.label = "R-"+arg.label;
		                        linkArg.linkingArg = arg;
		                        linkArg.node = linkArg.tokenNodes[0]==node?linkArg.tokenNodes[1]:linkArg.tokenNodes[0];
		                        break;
		                    }
		                }
		                if (linkArg.linkingArg!=null) break;
		            }
		            if (linkArg.linkingArg!=null) break;
                }
		        if (linkArg.linkingArg==null) System.err.println("LINK-SLC not resolved "+linkArg.tokenNodes);
            }
		    else
		        ++i;
		}
		
		return instance;
	}
	
	public void close()
	{
	    scanner.close();
	}
}
