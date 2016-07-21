package edu.colorado.clear.srl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import edu.colorado.clear.common.propbank.PBArg;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBTree;
import gnu.trove.map.TObjectIntMap;

public class SRInstance implements Comparable<SRInstance>, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -1033211240285878934L;

    enum OutputFormat {
        TEXT,
        PROPBANK,
        PROPBANK_PROB,
        CONLL,
        CONLL_DEP,
        BINARY
    };
    
    TBNode predicateNode;
    TBTree tree;
    ArrayList<SRArg> args;
    ArrayList<SRArg> allArgs;
    
    TObjectIntMap<String> argLabelStringMap;
    
    String rolesetId;
    
    public SRInstance(TBNode predicateNode, TBTree tree, TObjectIntMap<String> argLabelStringMap) {
        this.predicateNode = tree.getNodeByTokenIndex(predicateNode.getTokenIndex());
        this.tree = tree;
        this.argLabelStringMap = argLabelStringMap;
        args = new ArrayList<SRArg>();
    }
    
    public SRInstance(TBNode predicateNode, TBTree tree, TObjectIntMap<String> argLabelStringMap, String rolesetId, double score) {
        this(predicateNode, tree, argLabelStringMap);
        this.rolesetId = rolesetId;
        args.add(new SRArg("rel",this.predicateNode, score));
    }

    public SRInstance(PBInstance instance) {
        this(instance.getPredicate(), instance.getTree(), null, instance.getRoleset(), 1.0);
        for (PBArg pbArg: instance.getArgs()) {
            if (pbArg.getLabel().equals("rel")) continue;
            if (!pbArg.getTokenSet().isEmpty())
                addArg(new SRArg(pbArg.getLabel(), pbArg.getNode(), pbArg.getScore()));

            for (PBArg nestedArg:pbArg.getNestedArgs())
                if (!nestedArg.getTokenSet().isEmpty())
                    addArg(new SRArg(nestedArg.getLabel(), nestedArg.getNode(), nestedArg.getScore()));
        }
        args.trimToSize();
    }

    public void addArg(SRArg arg) {
        if (!arg.getTokenSet().isEmpty() && tree.getTokenCount() >= arg.getTokenSet().length()) {
        	if (!arg.isLabelArg()) {
        		if (allArgs==null)
        			allArgs = new ArrayList<SRArg>(args);
        	} else
        		args.add(arg);
        	
            if (allArgs!=null)
            	allArgs.add(arg);
        }
    }
    
    public TBNode getPredicateNode() {
        return predicateNode;
    }
    
    public TBTree getTree() {
        return tree;
    }
    
    public List<SRArg> getArgs() {
        return Collections.unmodifiableList(args);
    }
    
    public List<SRArg> getAllArgs() {
        return Collections.unmodifiableList(allArgs==null?args:allArgs);
    }
    
    public String getRolesetId() {
        return rolesetId;
    }

    public void setRolesetId(String rolesetId) {
        this.rolesetId = rolesetId;
    }
    
    static void removeOverlap(List<SRArg> args) {       
        LinkedList<SRArg> argQueue = new LinkedList<SRArg>(args);
        args.clear();
        
        while (!argQueue.isEmpty()) {
            LinkedList<SRArg> overlappedArgs = new LinkedList<SRArg>();
            
            overlappedArgs.add(argQueue.pop());
            BitSet tokenSet = (BitSet)overlappedArgs.element().tokenSet.clone();
            
            boolean overlapFound;
            do {
                overlapFound = false;
                for (ListIterator<SRArg> iter=argQueue.listIterator(); iter.hasNext();) {
                    SRArg arg = iter.next();
                    if (tokenSet.intersects(arg.tokenSet)) {
                        overlapFound = true;
                        tokenSet.or(arg.tokenSet);
                        overlappedArgs.add(arg);
                        iter.remove();
                        break;
                    }
                }
            } while (overlapFound);
          
            if (overlappedArgs.size()>1) {
                SRArg topArg = overlappedArgs.get(0);
                for (SRArg arg:overlappedArgs)
                    if (arg.score>topArg.score) topArg = arg;

                for (ListIterator<SRArg> iter=overlappedArgs.listIterator(); iter.hasNext();) {
                    SRArg arg = iter.next();
                    if (arg==topArg) continue;
                    if (arg.tokenSet.intersects(topArg.tokenSet))
                        iter.remove();
                }
                removeOverlap(overlappedArgs);
            }
     
            args.addAll(overlappedArgs);
        }
    }   

    public void cleanUpArgs() {
        removeOverlap(args);

        Collections.sort(args);
        
        Map<String, SRArg> argMap = new TreeMap<String, SRArg>();
        for (SRArg arg: args) {
            if (arg.label.startsWith("C-") && !argMap.containsKey(arg.label.substring(2)))
                arg.label = arg.label.substring(2);
            argMap.put(arg.label, arg);
        }
    }
    
    public List<SRArg> getScoringArgs() {
        Map<String, List<SRArg>> argMap = new TreeMap<String, List<SRArg>>();
        cleanUpArgs();

        for (SRArg arg:args) {
            String label = arg.label.startsWith("C-")?arg.label.substring(2):arg.label;
            List<SRArg> argList;
            if ((argList = argMap.get(label))==null) {
                argList = new LinkedList<SRArg>();
                argMap.put(label, argList);
            }
            argList.add(arg);
        }
        
        LinkedList<SRArg> retArgs = new LinkedList<SRArg>();
        
        for (Map.Entry<String, List<SRArg>> entry:argMap.entrySet()) {
            boolean isFirst = true;
            for (SRArg arg: entry.getValue()) {
                if (isFirst || !arg.label.startsWith("C-")) {
                	SRArg retArg = new SRArg(entry.getKey(), arg.tokenSet);
                	retArg.node = arg.node;
                    retArgs.add(retArg);
                } else
                    retArgs.getLast().tokenSet.or(arg.tokenSet);
                isFirst = false;
            }
        }
        Collections.sort(retArgs);
        return retArgs;
    }
    
    public String toPropbankString() {
    	return toPropbankString(false);
    }
    
    public String toPropbankString(boolean printProb) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(' ');
        buffer.append(tree.getIndex()); buffer.append(' ');
        buffer.append(predicateNode.getTerminalIndex()); buffer.append(' ');
        buffer.append("system "); 
        buffer.append((rolesetId==null||rolesetId.isEmpty())?predicateNode.getWord()+".XX":rolesetId);
        buffer.append(" ----- ");
        
        Collections.sort(args);

        TreeMap<String, List<StringBuilder>> argMap = new TreeMap<String, List<StringBuilder>>();
        
        for (SRArg arg:args) {
        	
           List<StringBuilder> argOut;
           
           String label = arg.label.startsWith("C-")?arg.label.substring(2):arg.label;
           if ((argOut = argMap.get(label))==null) {
               argOut = new LinkedList<StringBuilder>();
               argMap.put(label, argOut);
           }
           
           int[] id = PBInstance.getNodeId(arg.node);
           if (arg.label.startsWith("C-"))
               argOut.get(argOut.size()-1).append(","+id[0]+":"+id[1]);
           else {
        	   StringBuilder builder = new StringBuilder();
        	   argOut.add(builder);
        	   if (printProb && arg.score!=1.0)
        		   builder.append(Double.toString(arg.score)+'|');
        	   builder.append(""+id[0]+":"+id[1]);
           }
        }
        
        for (Map.Entry<String, List<StringBuilder>> entry:argMap.entrySet())
            for (StringBuilder builder:entry.getValue()) {
            	String str = builder.toString();
            	int scoreIdx = str.indexOf('|');
                buffer.append(scoreIdx<0?str:str.substring(scoreIdx+1));
                buffer.append('-');
                buffer.append(entry.getKey()); 
                if (scoreIdx>=0)
                	buffer.append("|"+str.substring(0, scoreIdx)); 
                buffer.append(' ');
            }

        return buffer.toString();
    }
    
    public String toCONLLDepString() {
    	StringBuilder buffer = new StringBuilder();
        
        List<TBNode> nodes = tree.getRootNode().getTokenNodes();
        String[] tokens = new String[nodes.size()];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes.get(i).getWord();
        
        String[] labels = new String[tree.getTokenCount()];
        
        for (SRArg arg:args) {
            labels[arg.node.getHead().getTokenIndex()] = arg.label+(arg.auxLabel==null?"":"="+arg.auxLabel.toUpperCase());
        }
        
        for (int i=0; i<labels.length; ++i) {
            if (labels[i]==null) continue;
            if (labels[i].equals("rel"))
                labels[i] = "V";
            else if (labels[i].startsWith("ARG"))
                labels[i] = "A"+labels[i].substring(3);
            else if (labels[i].startsWith("C-ARG"))
                labels[i] = "C-A"+labels[i].substring(5);
            else if (labels[i].startsWith("R-ARG"))
                labels[i] = "R-A"+labels[i].substring(5);
        }

        for (int i=0; i<labels.length; ++i)
            if (labels[i]==null)
                buffer.append("_ ");
            else
            	buffer.append(labels[i]+' ');   
        
        return buffer.toString();
    }
    
    public String toCONLLString() {
        StringBuilder buffer = new StringBuilder();
        
        List<TBNode> nodes = tree.getRootNode().getTokenNodes();
        String[] tokens = new String[nodes.size()];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes.get(i).getWord();
        
        String[] labels = new String[tree.getTokenCount()];
        
        for (SRArg arg:args) {
            BitSet bits = arg.getTokenSet();
            
            for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i+1))
                labels[i] = arg.label;
        }
        
        String previousLabel = null;
        for (int i=0; i<labels.length; ++i) {
            if (labels[i]!=null && labels[i].startsWith("C-") && labels[i].substring(2).equals(previousLabel))
                labels[i] = labels[i].substring(2);
            previousLabel = labels[i];
        }
        
        for (int i=0; i<labels.length; ++i) {
            if (labels[i]==null) continue;
            if (labels[i].equals("rel"))
                labels[i] = "V";
            else if (labels[i].startsWith("ARG"))
                labels[i] = "A"+labels[i].substring(3);
            else if (labels[i].startsWith("C-ARG"))
                labels[i] = "C-A"+labels[i].substring(5);
            else if (labels[i].startsWith("R-ARG"))
                labels[i] = "R-A"+labels[i].substring(5);
        }
        
        for (int i=0; i<labels.length; ++i) {   
            if (labels[i]!=null && (i==0 || !labels[i].equals(labels[i-1])))
                buffer.append('('+labels[i]);
            buffer.append('*');
            if (labels[i]!=null && (i==labels.length-1 || !labels[i].equals(labels[i+1])))
                buffer.append(')');
                    
            buffer.append(' ');      
        }
        
        return buffer.toString();
    }

    
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(" ");
        buffer.append(tree.getIndex()); buffer.append(" ");
        
        TBNode[] nodes = tree.getTokenNodes();
        String[] tokens = new String[nodes.length];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes[i].getWord();
        
        for (SRArg arg:getScoringArgs()) {

            BitSet bits = arg.getTokenSet();
            
            int start = bits.nextSetBit(0);
            int end = -1;
            do {
            	tokens[start] = "["+(end<0?"":"C-")+arg.label+" "+tokens[start];
	            end = bits.nextClearBit(start)-1;
	            tokens[end] += "]";
            } while ((start = bits.nextSetBit(end+1))>=0);
        }
        for (String token:tokens)
        	buffer.append(token+' ');
        
        return buffer.toString();
    }
    
    public String toString(OutputFormat outputFormat) {
        switch (outputFormat)
        {
        case TEXT:
            return toString();
        case PROPBANK:
            return toPropbankString();
        case PROPBANK_PROB:
        	return toPropbankString(true);
        case CONLL:
            return toCONLLString();
		default:
			return toString();
        }
    }

	@Override
    public int compareTo(SRInstance rhs) {
	    return predicateNode.getTokenIndex()-rhs.predicateNode.getTokenIndex();
    }
    
}
