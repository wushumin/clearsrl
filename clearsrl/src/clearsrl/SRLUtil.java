package clearsrl;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.LanguageUtil;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SRLUtil {
    static final Pattern ARG_PATTERN = Pattern.compile("(([RC]-)?(A[A-Z]*\\d))(\\-[A-Za-z]+)?");
    
    public static String removeArgModifier(String argType) {
        Matcher matcher = ARG_PATTERN.matcher(argType);
        if (matcher.matches())
            return matcher.group(1);
        return argType;
    }

    public static float getFScore(float lhs, float rhs) {
        float denom = lhs + rhs;
        return denom==0?0:2*lhs*rhs/denom;
    }

    public enum SupportType {
        NOMINAL,
        VERB,
        ALL
    }
    
    public static int[] findSupportPredicates(List<SRInstance> instances, LanguageUtil langUtil, SupportType type, boolean training) {
        int[] supports = new int[instances.size()];
        if (supports.length==0) return supports;
        Arrays.fill(supports, -1);
        
        TIntIntMap instanceMap = new TIntIntHashMap();
        for (int i=0; i<instances.size();++i)
            instanceMap.put(instances.get(i).getPredicateNode().getTokenIndex(), i+1);

        for (int i=0; i<instances.size();++i) {
            SRInstance instance = instances.get(i);         
            boolean isVerb = langUtil.isVerb(instance.getPredicateNode().getPOS());
            if (isVerb && type.equals(SupportType.NOMINAL) || !isVerb && type.equals(SupportType.VERB))
                continue;
            
            TBNode head = instance.getPredicateNode();
            TBNode constituent = head.getConstituentByHead();

            boolean foundSupport=false;
            if (!isVerb && constituent.getPOS().equals("NP")) {
                for (TBNode node:head.getDependentNodes()) {
                    if (langUtil.isVerb(node.getPOS()) && node.getTokenIndex()>head.getTokenIndex() && langUtil.getPassive(node)>0) {
                        supports[i] = instanceMap.get(node.getTokenIndex())-1;
                        if (supports[i]>0)
                            for (SRArg sarg:instances.get(supports[i]).getArgs())
                                if (sarg.getLabel().matches("ARG1|ARGM-PRX")&&sarg.tokenSet.get(head.getTokenIndex())) {
                                    foundSupport = true;
                                    break;
                                }   
                        
                        //System.out.println(instance);
                        //if (supports[i]>=0)
                        //  System.out.println(Boolean.toString(foundSupport)+" "+instances.get(supports[i])+"\n");
                        break;
                    }
                }
            }
            if (!foundSupport) {
                //while (constituent.getParent()!=null && constituent.getPOS().equals("VP") && constituent.getParent().getPOS().equals("VP")) {
                //  constituent = constituent.getParent();
                //  head = constituent.getHead();
                //}
    
                while (constituent.getParent()!=null && !langUtil.isClause(constituent.getParent().getPOS())) {
                    if (constituent.getParent().getHead()!=head && 
                            langUtil.isVerb(constituent.getParent().getHead().getPOS()) && 
                            head.getLevelToRoot()>=constituent.getParent().getHead().getLevelToRoot()) {
                        supports[i] = instanceMap.get(constituent.getParent().getHead().getTokenIndex())-1;
                        break;
                    }
                    constituent = constituent.getParent();
                }
            }
            if (training) {

                TBNode lvNode = null;
                for (SRArg arg:instance.getArgs())
                    if (arg.getLabel().endsWith("LVB")) {
                        lvNode = arg.node;
                        break;
                    }
                if (lvNode!=null) {
                    if (supports[i]<0 || instances.get(supports[i]).getPredicateNode()!=lvNode) {
                        if (supports[i]<0)
                            System.err.println("Light verb not found for "+instance);
                        else
                            System.err.println("Wrong support for "+instance);
                        boolean found = false;
                        for (SRInstance support:instances)
                            if (support.getPredicateNode()==lvNode) {
                                found = true;
                                System.err.println(Boolean.toString(langUtil.getPassive(support.predicateNode)>0)+" "+support);
                                break;
                            }
                        if (!found)
                            System.err.println("Light verb not in list");
                    } 
                }
    
                boolean nonlocalArg = false;
                if (supports[i]>=0) {
                    TBNode node = instances.get(supports[i]).getPredicateNode().getParent();
                    while (node.getParent()!=null && !node.getPOS().equals("VP") && !langUtil.isClause(node.getPOS()))
                        node = node.getParent();
                    for (SRArg sarg:instances.get(supports[i]).getArgs()) {
                        if (sarg.getLabel().equals(SRLModel.NOT_ARG)) continue;
                        BitSet tokenSet = sarg.getTokenSet();
                        tokenSet.or(node.getTokenSet());
                        if (tokenSet.get(instance.getPredicateNode().getTokenIndex())) {
                            for (SRArg arg:instance.getArgs()) {
                                if (arg.getLabel().equals(SRLModel.NOT_ARG)) continue;
                                if (!tokenSet.intersects(arg.getTokenSet())) {
                                    nonlocalArg=true;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
                
                if (lvNode!=null && supports[i]>=0) {
                    //re-populate the arguments of the light verb
                    BitSet tokenSet=null;
                    
                    for (SRArg sarg:instances.get(supports[i]).getArgs())
                        if ((tokenSet = sarg.getTokenSet()).get(instance.getPredicateNode().getTokenIndex()))
                            break;
                        else
                            tokenSet = null;
                    if (tokenSet!=null) {
                        TBNode node = instances.get(supports[i]).getPredicateNode().getParent();
                        while (node.getParent()!=null && !node.getPOS().equals("VP") && !langUtil.isClause(node.getPOS()))
                            node = node.getParent();
                        
                        BitSet vpSet = node.getTokenSet();
                        
                        for (int a=0; a<instance.getArgs().size(); ++a)
                            if (!tokenSet.intersects(instance.getArgs().get(a).getTokenSet())) {
                                boolean found = false;
                                for (SRArg sarg:instances.get(supports[i]).getArgs())
                                    if (instance.getArgs().get(a).node==sarg.node) {
                                        found = true;
                                        if (!instance.getArgs().get(a).getLabel().equals(sarg.getLabel()) && !sarg.getLabel().equals("rel")) {
                                            System.err.println("LVC issue: "+instance.getArgs().get(a).getLabel()+" "+sarg.getLabel());
                                            System.err.println(instance);
                                            System.err.println(instances.get(supports[i])+"\n");
                                        }
                                        break;
                                    }
                                if (!found) {
                                    instances.get(supports[i]).addArg(instance.getArgs().get(a));
                                    //if (!vpSet.intersects(instance.getArgs().get(a).getTokenSet()))
                                    //  instance.getArgs().remove(a);
                                    continue;
                                }
                            }
                        //System.out.println(instance);
                        //System.out.println(instances.get(supports[i])+"\n");
                    }           
                }
                
                //if (lvNode==null && nonlocalArg && !isVerb) {
                //  System.out.println(instance);
                //  System.out.println(instances.get(supports[i])+"\n");
                //}
            }
        }
        return supports;
    }
        
    static List<TBNode> getArgumentCandidates(TBNode node) {
        List<TBNode> nodes = new ArrayList<TBNode>();
        if (node.isTerminal())
            return nodes;
        
        for (TBNode childNode:node.getChildren())
        {
            if (childNode.isToken())
                nodes.add(childNode);
            else
            {
                List<TBNode> childNodes = getArgumentCandidates(childNode);
                if (childNodes.size()>0)// && node.getChildren().length>1)
                    nodes.add(childNode);
                if (childNodes.size()>1)
                {
                    if (childNode.getChildren().length>1)
                        nodes.addAll(childNodes);
                    else
                        nodes.addAll(childNodes.subList(1, childNodes.size()));
                }
            }
        }
        return nodes;
    }
    
    public static List<TBNode> getArgumentCandidates(TBNode predicate, boolean filter, SRInstance support, LanguageUtil langUtil, int levelDown, boolean allHeadPhrases) {
    	if (!filter) {
    		List<TBNode> nodes = getArgumentCandidates(predicate.getRoot());
            filterPredicateNode(nodes, predicate);
            return nodes;
    	}
    	return getArgumentCandidates(predicate, support, langUtil, levelDown, allHeadPhrases);
    }
    
    static List<TBNode> getArgumentCandidates(TBNode predicate, SRInstance support, LanguageUtil langUtil, int levelDown, boolean allHeadPhrases) {
        boolean toRoot = false;
        
        Set<TBNode> candidates = new HashSet<TBNode>();
        if (allHeadPhrases) {
        	for (TBNode token:predicate.getRoot().getTokenNodes()) {
        		TBNode constituent = token.getConstituentByHead();
        		if (constituent.getNodeByTokenIndex(predicate.getTokenIndex())==null)
        			candidates.add(constituent);
        	}
        }
        
        boolean isVerb = langUtil.isVerb(predicate.getPOS());
        if (support != null) {
            int levelUp = 0;            
            SRArg foundArg = null;
            for (SRArg arg:support.getArgs()) {
                if (arg.getLabel().equals(SRLModel.NOT_ARG)) continue;
                if (arg.tokenSet.get(predicate.getTokenIndex())) {
                    foundArg = arg;
                    TBNode node = predicate;
                    while (node != arg.node) {
                        ++levelUp;
                        node=node.getParent();
                    }
                    break;  
                }
            }
            if (foundArg!=null) {
                candidates.addAll(getNodes(predicate, levelUp, levelDown, allHeadPhrases));
                if (!isVerb) {
                    for (SRArg arg:support.getArgs())
                        if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG) && !arg.tokenSet.get(predicate.getTokenIndex()))
                            candidates.add(arg.node);
                } else {
                    TBNode argNode = foundArg.node;
                    
                    if (toRoot)
                        levelUp = argNode.getLevelToRoot();
                    else {
                        levelUp = 0;
                        boolean foundVP=false;
                        while (argNode!=null) {
                            ++levelUp;
                            if (foundVP && (langUtil.isClause(argNode.getPOS()) || argNode.getPOS().equals("FRAG")))
                                break;
                            if (argNode.getPOS().equals("VP"))
                                foundVP = true;
                            argNode = argNode.getParent();
                        }
                    }
                    
                    if (levelUp>0)
                        candidates.addAll(getNodes(foundArg.node, levelUp, levelDown-1, allHeadPhrases));
                    for (SRArg arg:support.getArgs())
                        if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG) && !arg.tokenSet.get(predicate.getTokenIndex())) {
                            boolean foundCandidate = false;
                            for (TBNode node:candidates)
                                if (arg.node==node) {
                                    foundCandidate = true;
                                    break;
                                }
                            if (!foundCandidate)
                                candidates.add(arg.node);
                        }
                }
                return new ArrayList<TBNode>(candidates);
            }
            if (langUtil.isVerb(predicate.getPOS())) {
                TBNode ancestor = predicate.getLowestCommonAncestor(support.predicateNode);
                if (ancestor.getPOS().equals("VP") && (levelUp=predicate.getLevelToNode(ancestor))==support.predicateNode.getLevelToNode(ancestor)) {
                    // support probably is the head of VP conjunction
                    candidates.addAll(getNodes(predicate, levelUp, levelDown, allHeadPhrases));
                    if (toRoot) {
                        levelUp = ancestor.getLevelToRoot();
                        candidates.addAll(getNodes(ancestor, levelUp, levelDown-1, allHeadPhrases));
                    }
                    for (SRArg arg:support.getArgs())
                        if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG) && !arg.getLabel().equals("rel")) {
                            boolean foundCandidate = false;
                            for (TBNode node:candidates)
                                if (arg.node==node) {
                                    foundCandidate = true;
                                    break;
                                }
                            if (!foundCandidate)
                                candidates.add(arg.node);
                        }
                    return new ArrayList<TBNode>(candidates);
                }
            }
        }
        int levelUp = 0;
        TBNode node = predicate.getParent();
        boolean foundClause=false;
        boolean foundRelativeClause=false;
        while (node!=null) {
            ++levelUp;
            if (!isVerb && (node.getPOS().equals("VP")||langUtil.isClause(node.getPOS())) || node.getPOS().equals("FRAG"))
                break;
            
            // work around for relative clauses when the support verb is missing
            if (foundClause && langUtil.isVerb(node.getHead().getPOS()) && node.getHead()!=predicate && 
                    (foundRelativeClause || !langUtil.isRelativeClause(node.getPOS())) ) {
                TBNode headConstituent = node.getHead().getConstituentByHead();
                levelUp+=node.getLevelToNode(headConstituent);
                node = headConstituent;
                break;
            }
            
            if (langUtil.isClause(node.getPOS()))
                foundClause = true;
            
            if (langUtil.isRelativeClause(node.getPOS()))
                foundRelativeClause = true;
            
            node = node.getParent();
        }
        candidates.addAll(getNodes(predicate, levelUp, levelDown, allHeadPhrases));  
        if (toRoot && node!=null) {
            levelUp = node.getLevelToRoot();
            candidates.addAll(getNodes(node, levelUp, levelDown-1, allHeadPhrases));
        }
        return new ArrayList<TBNode>(candidates);
    }
    
    static List<TBNode> getNodes(TBNode node, int levelUp, int levelDown, boolean getHeadPhrases) {
        List<TBNode> nodes = new ArrayList<TBNode>();
        if (levelUp<=0) 
            return nodes;
        getNodesAux(node, levelUp, levelDown, nodes, getHeadPhrases);
        return nodes;
    }
    
    static void getNodesAux(TBNode node, int levelUp, int levelDown, List<TBNode> nodes, boolean getHeadPhrases) {
        if ((levelUp<=0 || node.getParent()==null) && levelDown<=0)
            return;
        
        if (levelUp>0) {
            TBNode parent = node.getParent();
            if (parent==null) return;
            
            for (int i=0; i<parent.getChildren().length; ++i) {
                if (node.getChildIndex()==i) continue;
                if (parent.getChildren()[i].getTokenSet().cardinality()>0) {
                    nodes.add(parent.getChildren()[i]);
                    if (levelDown>0)
                        getNodesAux(parent.getChildren()[i], 0, levelDown, nodes, false);
                    if (getHeadPhrases)
                        nodes.addAll(getHeadPhrases(parent.getChildren()[i], 1));
                }
            }
            if (levelUp>1)
                getNodesAux(parent, levelUp-1, levelDown, nodes, getHeadPhrases);
        } else {
            Set<TBNode> nodeSet = new HashSet<TBNode>();
            for (TBNode child:node.getChildren())
                // make sure this is not an empty element node
                if (child.getTokenSet().cardinality()>0) {
                    nodeSet.add(child);
                    if (levelDown>0)
                        getNodesAux(child, 0, levelDown-1, nodes, getHeadPhrases);
                }
            // if we are going down a level, we need to find more than 1 node, otherwise
            // the single node would have the same token set as the current node
            if (nodeSet.size()>1)
                nodes.addAll(nodeSet);
        }
        return;
    }
    
    static List<TBNode> getHeadPhrases(TBNode node, int levelDown) {
        List<TBNode> nodes = new ArrayList<TBNode>();
        for (TBNode dependent:node.getHead().getDependentNodes(false)) {
            if (!dependent.isDecendentOf(node))
                continue;
            TBNode constituent = dependent.getConstituentByHead();
            if (constituent.isToken()&&constituent.getParent().getPOS().equals("NP"))
                continue;
            nodes.add(constituent);
            nodes.addAll(getHeadLevelDown(constituent, levelDown));
            nodes.addAll(getHeadPhrases(constituent, levelDown));
        }
        return nodes;
    }
    
    static List<TBNode> getHeadLevelDown(TBNode node, int levelDown) {
        List<TBNode> nodes = new ArrayList<TBNode>();
        if (levelDown<=0) return nodes;
        for (TBNode child:node.getChildren()) 
            if (child.getHead()==node.getHead()) {
                if (child.getTokenSet().cardinality()<node.getTokenSet().cardinality()) {
                    if (!child.isToken()||child.getParent().getPOS().equals("NP")) {
                        nodes.add(child);
                        nodes.addAll(getHeadLevelDown(child, levelDown-1));
                    }
                } else {
                    nodes.addAll(getHeadLevelDown(child, levelDown));
                }
                break;
            }
        return nodes;
    }
    
    public static void filterPredicateNode(List<TBNode> argNodes, TBNode predicateNode) {
        for (Iterator<TBNode> iter=argNodes.iterator(); iter.hasNext();)
            if (iter.next().getTokenSet().get(predicateNode.getTokenIndex()))
                iter.remove();
        return;
    }

    public static Map<String, BitSet> convertSRInstanceToTokenMap(SRInstance instance)
    {
        Map<String, BitSet> tokenMap = new TreeMap<String, BitSet>();
        
        BitSet tokenSet=null;
        for (SRArg arg:instance.getArgs())
        {
            if ((tokenSet=tokenMap.get(arg.label))==null)
                tokenMap.put(arg.label, arg.getTokenSet());
            else
                tokenSet.or(arg.getTokenSet());
        }
        return tokenMap;
    }
    
    public static int getMaxIndex(float[] array)
    {
        float val = Float.NEGATIVE_INFINITY;
        int index = -1;
        for (int i=0; i<array.length; ++i)
            if (array[i]>val)
            {
                val = array[i];
                index = i;
            }
        return index;
    }
}
