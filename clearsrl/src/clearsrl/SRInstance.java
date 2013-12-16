package clearsrl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;

public class SRInstance implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -1033211240285878934L;

    enum OutputFormat {
        TEXT,
        PROPBANK,
        CONLL
    };
    
    TBNode predicateNode;
    TBTree tree;
    ArrayList<SRArg> args;
    
    String rolesetId;
    
    public SRInstance(TBNode predicateNode, TBTree tree)
    {
        this.predicateNode = tree.getNodeByTokenIndex(predicateNode.getTokenIndex());
        this.tree = tree;
        args = new ArrayList<SRArg>();
    }
    
    public SRInstance(TBNode predicateNode, TBTree tree, String rolesetId, double score)
    {
        this(predicateNode, tree);
        this.rolesetId = rolesetId;
        args.add(new SRArg("rel",this.predicateNode, score));
    }
    /*
    public SRInstance(PBInstance instance) {
        this(instance.predicateNode, instance.tree);
        for (Entry<String, PBArg> entry : instance.getArgs().entrySet())
        {
            BitSet tokenSet = new BitSet(tree.getTokenCount());
            for (TBNode node:entry.getValue().getTokenNodes())
                if (node.tokenIndex>=0) tokenSet.set(node.tokenIndex);
            if (tokenSet.isEmpty()) continue;
            addArg(new SRArg(SRLUtil.removeArgModifier(entry.getKey()), tokenSet));
        }
    }
    */
    public SRInstance(PBInstance instance) {
        this(instance.getPredicate(), instance.getTree(), instance.getRoleset(), 1.0);
        for (PBArg pbArg: instance.getArgs()) {
            if (pbArg.getLabel().equals("rel")) continue;
            if (!pbArg.getTokenSet().isEmpty())
                addArg(new SRArg(SRLUtil.removeArgModifier(pbArg.getLabel()), pbArg.getNode(), 1.0));

            for (PBArg nestedArg:pbArg.getNestedArgs())
                if (!nestedArg.getTokenSet().isEmpty())
                    addArg(new SRArg(SRLUtil.removeArgModifier(nestedArg.getLabel()), nestedArg.getNode(), 1.0));
        }
        args.trimToSize();
    }

    public void addArg(SRArg arg)
    {
        if (!arg.getTokenSet().isEmpty() && tree.getTokenCount() >= arg.getTokenSet().length())
            args.add(arg);
    }
    
    public TBNode getPredicateNode()
    {
        return predicateNode;
    }
    
    public TBTree getTree()
    {
        return tree;
    }
    
    public List<SRArg> getArgs()
    {
        return args;
    }
    
    public String getRolesetId() {
        return rolesetId;
    }

    public void setRolesetId(String rolesetId) {
        this.rolesetId = rolesetId;
    }
    
    /*
    public void removeOverlap()
    {       
        boolean overlapped = false;
        
        do {
            overlapped = false;
            for (int i=0; i<args.size();++i)
            {
                BitSet argi = args.get(i).tokenSet;
                for (int j=i+1; j<args.size();++j)
                {
                    BitSet argj= args.get(j).tokenSet; 
                    if (argj.intersects(argi))
                    {
                        //if (instance.args.get(i).label.equals(instance.args.get(j).label))
                        {
                            args.remove(argi.cardinality()<argj.cardinality()?i:j);
                            overlapped = true;
                            break;
                        }
                    }   
                }
                if (overlapped) break;
            }
        } while (overlapped);
        
        for (int i=0; i<args.size();++i)
        {
            BitSet argi = args.get(i).tokenSet;
            for (int j=i+1; j<args.size();++j)
            {
                BitSet argj= args.get(j).tokenSet; 
                if (argj.intersects(argi))
                {
                    //System.out.println(instance);
                    return;
                }
            }
        }
    }
*/
    public void removeOverlap()
    {
        //System.out.println(args);
        removeOverlap(args);
    }
    
    static void removeOverlap(List<SRArg> args)
    {       
        LinkedList<SRArg> argQueue = new LinkedList<SRArg>(args);
        args.clear();
        
        while (!argQueue.isEmpty())
        {
            LinkedList<SRArg> overlappedArgs = new LinkedList<SRArg>();
            
            overlappedArgs.add(argQueue.pop());
            BitSet tokenSet = (BitSet)overlappedArgs.element().tokenSet.clone();
            
            boolean overlapFound;
            do
            {
                overlapFound = false;
                for (ListIterator<SRArg> iter=argQueue.listIterator(); iter.hasNext();)
                {
                    SRArg arg = iter.next();
                    if (tokenSet.intersects(arg.tokenSet))
                    {
                        overlapFound = true;
                        tokenSet.or(arg.tokenSet);
                        overlappedArgs.add(arg);
                        iter.remove();
                        break;
                    }
                }
            } while (overlapFound);
          
            if (overlappedArgs.size()>1)
            {
                SRArg topArg = overlappedArgs.get(0);
                for (SRArg arg:overlappedArgs)
                    if (arg.score>topArg.score) topArg = arg;

                for (ListIterator<SRArg> iter=overlappedArgs.listIterator(); iter.hasNext();)
                {
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

    public void cleanUpArgs()
    {
        removeOverlap();

        Collections.sort(args);
        
        Map<String, SRArg> argMap = new TreeMap<String, SRArg>();
        for (SRArg arg: args)
        {
            if (arg.label.startsWith("C-") && !argMap.containsKey(arg.label.substring(2)))
            {
                arg.label = arg.label.substring(2);
            }
            argMap.put(arg.label, arg);
        }
    }
    
    public List<SRArg> getScoringArgs() {
        Map<String, List<SRArg>> argMap = new TreeMap<String, List<SRArg>>();
        cleanUpArgs();

        for (SRArg arg:args)
        {
            if (arg.label.equals(SRLModel.NOT_ARG) || arg.label.equals("rel")) continue;
            String label = arg.label.startsWith("C-")?arg.label.substring(2):arg.label;
            List<SRArg> argList;
            if ((argList = argMap.get(label))==null)
            {
                argList = new LinkedList<SRArg>();
                argMap.put(label, argList);
            }
            argList.add(arg);
        }
        
        LinkedList<SRArg> retArgs = new LinkedList<SRArg>();
        
        for (Map.Entry<String, List<SRArg>> entry:argMap.entrySet())
        {
            boolean isFirst = true;
            for (SRArg arg: entry.getValue())
            {
                if (isFirst || !arg.label.startsWith("C-"))
                    retArgs.add(new SRArg(entry.getKey(), arg.tokenSet));
                else
                    retArgs.getLast().tokenSet.or(arg.tokenSet);
                isFirst = false;
            }
        }
        Collections.sort(retArgs);
        return retArgs;
    }
    
    public String toPropbankString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(' ');
        buffer.append(tree.getIndex()); buffer.append(' ');
        buffer.append(predicateNode.getTerminalIndex()); buffer.append(' ');
        buffer.append("system "); 
        buffer.append((rolesetId==null||rolesetId.isEmpty())?predicateNode.getWord()+".XX":rolesetId);
        buffer.append(" ----- ");
        
        Collections.sort(args);
        
        TreeMap<String, List<StringBuilder>> argMap = new TreeMap<String, List<StringBuilder>>();
        
        for (SRArg arg:args)
        {
           if (arg.label.equals(SRLModel.NOT_ARG)) continue;
           
           List<StringBuilder> argOut;
           
           String label = arg.label.startsWith("C-")?arg.label.substring(2):arg.label;
           if ((argOut = argMap.get(label))==null)
           {
               argOut = new LinkedList<StringBuilder>();
               argMap.put(label, argOut);
           }
           
           int[] id = PBInstance.getNodeId(arg.node);
           if (arg.label.startsWith("C-"))
               argOut.get(argOut.size()-1).append(","+id[0]+":"+id[1]);
           else
               argOut.add(new StringBuilder(id[0]+":"+id[1]));
        }
        
        for (Map.Entry<String, List<StringBuilder>> entry:argMap.entrySet())
        {
            for (StringBuilder builder:entry.getValue())
            {
                buffer.append(builder.toString());
                buffer.append('-');
                buffer.append(entry.getKey()); buffer.append(' ');   
            }
        }
        
        return buffer.toString();
    }
    /*
    public String toPropbankString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(' ');
        buffer.append(tree.getIndex()); buffer.append(' ');
        buffer.append(predicateNode.getTerminalIndex()); buffer.append(' ');
        buffer.append("system "); buffer.append(predicateNode.getWord());
        buffer.append(" ----- ");
        
        TreeMap<String, TreeSet<SRArg>> argMap = new TreeMap<String, TreeSet<SRArg>>();
        
        for (SRArg arg:args)
        {
           if (arg.label.equals(SRLModel.NOT_ARG)) continue;
           TreeSet<SRArg> argSet;
           if ((argSet = argMap.get(arg.label))==null)
           {
               argSet = new TreeSet<SRArg>();
               argMap.put(arg.label, argSet);
           }
           argSet.add(arg);
        }
        
        for (Map.Entry<String, TreeSet<SRArg>> entry:argMap.entrySet())
        {
            String argStr = "";
            for (SRArg arg:entry.getValue())
            {
                int depth=0;
                TBNode node = arg.node;
                while (!node.isTerminal())
                {
                    ++depth;
                    node=node.getChildren()[0];
                }
                argStr+=node.getTerminalIndex()+":"+depth+"*";
            }
            buffer.append(argStr.substring(0,argStr.length()-1));
            buffer.append('-');
            buffer.append(entry.getKey()); buffer.append(' ');   
        }
        
        return buffer.toString();
    }
*/
    
    public String toCONLLString()
    {
        StringBuilder buffer = new StringBuilder();
        
        List<TBNode> nodes = tree.getRootNode().getTokenNodes();
        String[] tokens = new String[nodes.size()];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes.get(i).getWord();
        
        String[] labels = new String[tree.getTokenCount()];
        
        for (SRArg arg:args)
        {
            if (arg.label.equals(SRLModel.NOT_ARG)) continue;
            BitSet bits = arg.getTokenSet();
            
            for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i+1))
                labels[i] = arg.label;
        }
        
        String previousLabel = null;
        for (int i=0; i<labels.length; ++i)
        {
            if (labels[i]!=null && labels[i].startsWith("C-") && labels[i].substring(2).equals(previousLabel))
                labels[i] = labels[i].substring(2);
            previousLabel = labels[i];
        }
        
        for (int i=0; i<labels.length; ++i)
        {
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
        {   
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
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(tree.getFilename()); buffer.append(" ");
        buffer.append(tree.getIndex()); buffer.append(" ");
        
        TBNode[] nodes = tree.getTokenNodes();
        String[] tokens = new String[nodes.length];
        for (int i=0; i<tokens.length; ++i)
            tokens[i] = nodes[i].getWord();
        
        String[] labels = new String[nodes.length];
        
        for (SRArg arg:args)
        {
            if (arg.label.equals(SRLModel.NOT_ARG)) continue;
            BitSet bits = arg.getTokenSet();
            
            for (int i = bits.nextSetBit(0); i >= 0 ; i = bits.nextSetBit(i+1))
                labels[i] = arg.label;
        }
        
        String previousLabel = null;
        for (int i=0; i<tokens.length; ++i)
        {
            if (labels[i]!=null && labels[i].startsWith("C-") && labels[i].substring(2).equals(previousLabel))
                    labels[i] = labels[i].substring(2);
            previousLabel = labels[i];
        }
        
        for (int i=0; i<tokens.length; ++i)
        {
            if (labels[i]!=null && (i==0 || !labels[i].equals(labels[i-1])))
                buffer.append('['+labels[i]+' ');
            buffer.append(tokens[i]);
            if (labels[i]!=null && (i==tokens.length-1 || !labels[i].equals(labels[i+1])))
                buffer.append(']');
                    
            buffer.append(' ');      
        }
        
        return buffer.toString();
    }
    
    
    
    
    public String toString(OutputFormat outputFormat) {
        switch (outputFormat)
        {
        case TEXT:
            return toString();
        case PROPBANK:
            return toPropbankString();
        case CONLL:
            return toCONLLString();
        }
        return toString();
    }
    
}
