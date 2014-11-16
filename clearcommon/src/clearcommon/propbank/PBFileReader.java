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
import clearcommon.treebank.ParseException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Matcher;


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
public class PBFileReader
{
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    String                annotationFile;
    Scanner               scanner;
    PBTokenizer           tokenizer;
    TBReader              tbReader;
    PBInstance            lastInstance;
    boolean               closed;
    
    /**
     * Opens 'annotationFile', finds trees from 'treebankPath', and collects information.
     * @param annotationFile the path of the annotation file.
     * @param treebankPath the path of the treebank.
     * @throws FileNotFoundException 
     */
    public PBFileReader(TBReader tbReader, String annotationFile) throws IOException
    {
        this(tbReader, annotationFile, new DefaultPBTokenizer());
    }

    public PBFileReader(TBReader tbReader, String annotationFile, PBTokenizer tokenizer) throws IOException
    {
        this.tbReader       = tbReader;
        this.annotationFile = annotationFile;
        scanner             = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(annotationFile),"UTF-8")));
        this.tokenizer      = tokenizer;
        lastInstance        = null;
        closed              = false;
    }
    
    public PBInstance nextProp() throws PBFormatException, ParseException
    {
        if (closed) return null;
        if (!scanner.hasNextLine()) 
        {
            close();
            return null;
        }
        String line = scanner.nextLine().trim();
        String[] tokens = tokenizer.tokenize(line);
        if (tokens==null) 
            return nextProp();
        
        //System.out.println(Arrays.toString(tokens));
        int t=0;
        
        PBInstance instance = new PBInstance();
        
        String treeFile     = tokens[0];
        
        instance.tree = tbReader.getTree(treeFile, Integer.parseInt(tokens[1]));
        if (instance.tree==null)
            throw new PBFormatException("parse tree invalid: "+treeFile+" "+tokens[1]+"\n"+Arrays.toString(tokens));

        instance.predicateNode = instance.tree.getNodeByTerminalIndex(Integer.parseInt(tokens[2]));
        
        if (instance.predicateNode == null)
            throw new PBFormatException("predicate node not found "+"\n"+Arrays.toString(tokens));
        
        t = 3; // skip "gold" or annotator initial before roleset id
        while (!tokens[t].matches(".+(\\.1?\\w{2}|-[nvj])")) {
            ++t;
            if (t>=tokens.length)
                throw new PBFormatException("Can't find roleset: "+"\n"+Arrays.toString(tokens));
        }
        
        if (tokens[t].matches(".+-[nvj]"))
            instance.rolesetId = tokens[++t];// skip roleset-[nv] for ontonotes
        else
            instance.rolesetId = tokens[t];
        
        ++t;  // skip inflection, should be 5 characters, "-----" if not annotated
        
        List<PBArg> argList = new LinkedList<PBArg>();

        for (++t;t<tokens.length; ++t)
        {
        	Matcher matcher = PBArg.ARG_PATTERN.matcher(tokens[t]);
        	
            if (!matcher.matches())
                throw new PBFormatException("malformed argument: "+tokens[t]+"\n"+Arrays.toString(tokens));
            
            String label = matcher.group("label"); 
            
            String probStr = matcher.group("prob");
            double prob = probStr==null?1.0:Double.parseDouble(probStr);
            
            /*
            int idx    = tokens[t].indexOf(PBLib.ARG_DELIM);
            
            String label = tokens[t].substring(idx+1);
            if (!label.matches(PBArg.LABEL_PATTERN))
                throw new PBFormatException("unrecognized argument label: "+label+"\n"+Arrays.toString(tokens));

            if (label.matches("(A[A-Z]*\\d)-.*"))
            	label = label.substring(0, label.indexOf('-'));*/
            
            List<TBNode> nodeList = new ArrayList<TBNode>();
            List<TBNode> nestedNodeList = new ArrayList<TBNode>();
            String[] locs = matcher.group("locs").split("(?=[\\*,;&])");
            
            String[] loc  = locs[0].split(":");
            
            TBNode node = instance.tree.getRootNode().getNode(Integer.parseInt(loc[0]),Integer.parseInt(loc[1]));
            if (node==null)
                throw new PBFormatException("invalid node:"+locs[0]+"\n"+Arrays.toString(tokens));
            nodeList.add(node);
            
            for (int i=1; i<locs.length; ++i)
            {
                loc = locs[i].substring(1).split(":");
                node = instance.tree.getRootNode().getNode(Integer.parseInt(loc[0]),Integer.parseInt(loc[1]));
                if (node==null)
                    throw new PBFormatException("invalid node:"+locs[i]+"\n"+Arrays.toString(tokens));
                if (locs[i].charAt(0)!='*')
                    nestedNodeList.add(node);
                else
                    nodeList.add(node);
            }
            
            if (label.endsWith("PRR")) {
                instance.prrNode = nodeList.get(0);
                continue;
            }
            
            PBArg arg = new PBArg(label, prob);
            arg.allNodes = nodeList.toArray(new TBNode[nodeList.size()]);
            
            if (!nestedNodeList.isEmpty()) {
                arg.nestedArgs = new PBArg[nestedNodeList.size()];
                for (int i=0; i<nestedNodeList.size(); ++i)  {
                    arg.nestedArgs[i] = new PBArg("C-"+label, prob);
                    arg.nestedArgs[i].allNodes = new TBNode[1];
                    arg.nestedArgs[i].allNodes[0] = nestedNodeList.get(i);
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
            if (isSLC && linkArg.allNodes.length!=2) throw new PBFormatException("LINK-SLC size incorrect "+linkArg.allNodes);

            for (PBArg arg:argList)
            {
                if (linkArg==arg) continue;
                for (TBNode node: arg.allNodes)
                {
                    for (TBNode linkNode:linkArg.allNodes)
                    {
                        if (node == linkNode)
                        {
                            found = true;
                            if (isSLC)
                            {
                                linkArg.label = arg.label;
                                
                                if (arg.allNodes.length==1)
                                    linkArg.allNodes = Arrays.asList(linkArg.allNodes[0]==node?linkArg.allNodes[1]:linkArg.allNodes[0]).toArray(new TBNode[1]);
                                else
                                    linkArg.allNodes = Arrays.asList(linkArg.allNodes[0].getPOS().startsWith("WH")?linkArg.allNodes[1]:linkArg.allNodes[0]).toArray(new TBNode[1]);
                                arg.label = "R-"+linkArg.label;
                                arg.linkingArg = linkArg;
                            } else {
                                List<TBNode> nodeList = new ArrayList<TBNode>(Arrays.asList(arg.allNodes));
                                for (TBNode aNode:linkArg.allNodes)
                                    if (aNode!=linkNode) nodeList.add(aNode);
                                arg.allNodes = nodeList.toArray(new TBNode[nodeList.size()]);
                            }
                            break;
                        }
                    }
                    if (found) break;
                }
                if (found) break;
            }
            if (!found || !isSLC) {
            if (isSLC) logger.warning(linkArg.label+" not resolved "+linkArg.allNodes+"\n"+Arrays.toString(tokens));
            iter.remove();

        }
        }
        try {
        	List<PBArg> extraArgs = new LinkedList<PBArg>();
        	PBArg extraArg = null;
            // process all the main args before the reference args
            for (PBArg arg:argList)
            	if (arg.linkingArg==null && (extraArg = arg.processNodes())!=null)
            		extraArgs.add(extraArg);
            argList.addAll(extraArgs);

            for (PBArg arg:argList)
                if (arg.linkingArg!=null) arg.processNodes();
        } catch (PBFormatException e) {
            throw new PBFormatException(e.getMessage()+"\n"+Arrays.toString(tokens));
        }
        
        instance.allArgs = argList.toArray(new PBArg[argList.size()]);
        
        Arrays.sort(instance.allArgs);
        
        List<PBArg> emptyArgList = new LinkedList<PBArg>();
        argList.clear();
        
        for (PBArg arg:instance.allArgs)
            if (!arg.isEmpty()) argList.add(arg);
            else emptyArgList.add(arg);

        instance.args = argList.toArray(new PBArg[argList.size()]);
        Arrays.sort(instance.args);
        
        instance.emptyArgs = emptyArgList.toArray(new PBArg[emptyArgList.size()]);
        Arrays.sort(instance.emptyArgs);
        //System.out.println(instance);

        BitSet terminalSet = new BitSet(instance.tree.getTerminalCount());
        for (PBArg arg:instance.allArgs) {
            if (terminalSet.intersects(arg.terminalSet))
                throw new PBFormatException("instance has terminal overlap\n"+Arrays.toString(tokens)+"\n"+instance);
            terminalSet.or(arg.terminalSet);
        }
        return instance;
    }
    
    public List<PBInstance> nextPropSet()
    {
        LinkedList<PBInstance> retList = new LinkedList<PBInstance>();
        if (lastInstance!=null)
        {
            retList.add(lastInstance);
            lastInstance = null;
        }

        for (;;)
        {
            try {
                lastInstance = nextProp();
                
                if (lastInstance==null) return retList.isEmpty()?null:retList;
                if (retList.isEmpty())
                {
                    retList.add(lastInstance);
                    lastInstance = null;
                }
                else
                {
                    if (!retList.getLast().tree.getFilename().equals(lastInstance.tree.getFilename())||
                        retList.getLast().tree.getIndex()!=lastInstance.tree.getIndex())
                        return retList;
                    else
                    {
                        retList.add(lastInstance);
                        lastInstance = null;
                    }
                }
            } catch (PBFormatException e) {
                logger.severe(e.getMessage());
                continue;
            } catch (ParseException e) {
                logger.severe(e.getMessage());
                close();
                return retList.isEmpty()?null:retList;
            } catch (Exception e) {
                logger.severe(annotationFile+": "+e.getMessage());
                return retList.isEmpty()?null:retList;
            }
        }
    }
    
    public boolean isOpen() {
        return !closed;
    }
    
    public void close()
    {
        closed = true;
        scanner.close();
    }
}
