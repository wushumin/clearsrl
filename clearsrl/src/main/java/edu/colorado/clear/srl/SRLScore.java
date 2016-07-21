package edu.colorado.clear.srl;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.colorado.clear.common.treebank.TBNode;

public class SRLScore {
    
	enum Type {
		ALL,
		HEAD,
		DEP,
		NODEP,
		DEPHEAD,
		NODEPHEAD
	}
	
    SortedSet<String> labelSet;
    TObjectIntMap<String> labelMap;
    int[][] microCount;
    int[][] macroCount;
    
    class Score {
        public Score(String label, double p, double r, double f, long pCount, long rCount, long fCount) {
            this.label = label;
            this.p = p;
            this.r = r;
            this.f = f;
            this.pCount = pCount;
            this.rCount = rCount;
            this.fCount = fCount;
        }
        
        @Override
		public String toString() {
            return String.format("%s(%d,%d,%d): precision: %f recall: %f f-measure: %f", label, fCount, pCount, rCount, p*100, r*100, f*100);
        }
        
        String label;
        double p;
        double r;
        double f;
        long pCount;
        long rCount;
        long fCount;
    }
    
    public SRLScore(Collection<String> labelSet) {
        this.labelSet = new TreeSet<String>(labelSet);
        labelMap = new TObjectIntHashMap<String>();
        
        int count=0;
        for (String argType:labelSet) {
            if (SRArg.NOT_ARG.equals(argType))
                continue;
            labelMap.put(argType, ++count);
        }
        microCount = new int[count+1][count+1];
        macroCount = new int[count+1][count+1];
    }
    
    String[] getLabels(List<SRArg> args, int tokenCount) {
        String[] strs = new String[tokenCount];
        Arrays.fill(strs, SRArg.NOT_ARG);
        for (SRArg arg:args)
            for (int i=arg.tokenSet.nextSetBit(0); i>=0; i=arg.tokenSet.nextSetBit(i+1))
                strs[i] = arg.label;
        return strs;
    }
    
    public static SRInstance getIntersection(SRInstance lhs, SRInstance rhs) {
        if (!lhs.tree.getFilename().equals(rhs.tree.getFilename()) ||
                lhs.tree.getIndex()!=rhs.tree.getIndex() ||
                lhs.predicateNode.getTerminalIndex() != rhs.predicateNode.getTerminalIndex())
            return null;
        
        SRInstance instance = new SRInstance(lhs.predicateNode, lhs.tree, null);
        
        List<SRArg> lhsargs = lhs.args;
        List<SRArg> rhsargs = rhs.args;
        
        for (int i=0, j=0; i<lhsargs.size() && j<rhsargs.size();) {
            int compare = lhsargs.get(i).compareTo(rhsargs.get(j));
            if (compare<0) 
                ++i;
            else if (compare>0) 
                ++j;
            else {
                if (lhsargs.get(i).tokenSet.equals(rhsargs.get(j).tokenSet))
                    instance.args.add(lhsargs.get(i));
                ++i; ++j;
            }
        }
        
        return instance;
    }
    
    
    public static SRInstance getUnion(SRInstance lhs, SRInstance rhs) {
        if (!lhs.tree.getFilename().equals(rhs.tree.getFilename()) ||
                lhs.tree.getIndex()!=rhs.tree.getIndex() ||
                lhs.predicateNode.getTerminalIndex() != rhs.predicateNode.getTerminalIndex())
                        return null;
                
        SRInstance instance = new SRInstance(lhs.predicateNode, lhs.tree, null);
        
        List<SRArg> lhsargs = lhs.args;
        List<SRArg> rhsargs = rhs.args;
            
        
        instance.args.addAll(lhs.args);
        int i=0;
        int j=0;
        for (; i<lhsargs.size() && j<rhsargs.size();) {
            int compare = lhsargs.get(i).compareTo(rhsargs.get(j));
            if (compare<0) 
                ++i;
            else if (compare>0) {
                instance.args.add(rhsargs.get(j));
                ++j;
            } else {
                if (!lhsargs.get(i).tokenSet.equals(rhsargs.get(j).tokenSet))
                    instance.args.add(rhsargs.get(j));
                ++i; ++j;
            }
        }
        for (;j<rhsargs.size();++j)
            instance.args.add(rhsargs.get(j));
            
        return instance;
    }
    
    public boolean addResult(String systemLabel, String goldLabel) {
        macroCount[labelMap.get(systemLabel)][labelMap.get(goldLabel)]++;
        return labelMap.get(systemLabel)==labelMap.get(goldLabel);
    }
    
    public int[] addResult(SRInstance systemSRL, SRInstance goldSRL, Type type) {
    	int[] counts = new int[3];
    	
    	boolean headOnly = type.equals(Type.HEAD) || type.equals(Type.DEPHEAD) || type.equals(Type.NODEPHEAD);
    	
        List<SRArg> sysArgs = systemSRL.getScoringArgs();
        List<SRArg> goldArgs = goldSRL.getScoringArgs();
        
        filter(systemSRL, sysArgs, type);
        filter(goldSRL, goldArgs, type);
        
        String[] sysStr = getLabels(sysArgs, systemSRL.tree.getTokenCount());       
        String[] goldStr = getLabels(goldArgs, goldSRL.tree.getTokenCount());
        
        for (int i=0; i<sysStr.length; ++i) {
            if (sysStr[i]==SRArg.NOT_ARG && goldStr[i]==SRArg.NOT_ARG)
                continue;
            microCount[labelMap.get(sysStr[i])][labelMap.get(goldStr[i])]++;
        }

        for (int i=0, j=0; i<sysArgs.size() || j<goldArgs.size();) {
            if (i>=sysArgs.size()) {
            	int goldLabel = labelMap.get(goldArgs.get(j).label);
            	if (goldLabel!=0)
            		++counts[2];
                macroCount[labelMap.get(SRArg.NOT_ARG)][goldLabel]++;
                ++j;
                continue;
            }
            if (j>=goldArgs.size()) {
            	int sysLabel = labelMap.get(sysArgs.get(i).label);
            	if (sysLabel!=0)
            		++counts[1];
                macroCount[sysLabel][labelMap.get(SRArg.NOT_ARG)]++;
                ++i;
                continue;
            }
            
            int compare = compare(sysArgs.get(i), goldArgs.get(j), headOnly);
            if (compare<0) {
            	int sysLabel = labelMap.get(sysArgs.get(i).label);
            	if (sysLabel!=0)
            		++counts[1];
                macroCount[sysLabel][labelMap.get(SRArg.NOT_ARG)]++;
                ++i;
            } else if (compare>0) {
            	int goldLabel = labelMap.get(goldArgs.get(j).label);
            	if (goldLabel!=0)
            		++counts[2];
                macroCount[labelMap.get(SRArg.NOT_ARG)][goldLabel]++;
                ++j;
            } else {
            	int sysLabel = labelMap.get(sysArgs.get(i).label);
            	int goldLabel = labelMap.get(goldArgs.get(j).label);
                if (headOnly || sysArgs.get(i).tokenSet.equals(goldArgs.get(j).tokenSet)) {
                	macroCount[sysLabel][goldLabel]++;
                	if (sysLabel!=0 && sysLabel==goldLabel)
                		++counts[0];
                } else {
                    macroCount[sysLabel][labelMap.get(SRArg.NOT_ARG)]++;
                    macroCount[labelMap.get(SRArg.NOT_ARG)][goldLabel]++;
                }
                if (sysLabel!=0)
            		++counts[1];
                if (goldLabel!=0)
            		++counts[2];
                ++i; ++j;
            }   
        }
        return counts;
    }
    
    void filter(SRInstance instance, List<SRArg> args,  Type type) {
    	if (type.equals(Type.ALL)|| type.equals(Type.HEAD))
    		return;
    	boolean filterDep = type.equals(Type.DEP) || type.equals(Type.DEPHEAD);
    	for (Iterator<SRArg> iter= args.iterator();iter.hasNext();) {
    		TBNode head = iter.next().node.getHead().getHeadOfHead();
    		if (head==instance.getPredicateNode() ^ filterDep)
    			iter.remove();
    	}
    }
    
    int compare(SRArg lhs, SRArg rhs, boolean headOnly) {
    	if (headOnly) {
    		//if (lhs.node.getHead().getTokenIndex()!=rhs.node.getHead().getTokenIndex() && lhs.getTokenSet().equals(rhs.getTokenSet())) {
    		//	System.err.println(lhs.node.toParse());
    		//	System.err.println(rhs.node.toParse());
    		//}
    		//if (lhs.getTokenSet().equals(rhs.getTokenSet()))
    		//	return 0;
    		
    		return lhs.node.getHead().getTokenIndex()-rhs.node.getHead().getTokenIndex();
    	}	
    	return lhs.compareTo(rhs);
    }
    
    @Override
	public String toString() {
        StringBuilder builder = new StringBuilder();
        int sum = 0;
        for (int[] row:microCount)
            for (int cell:row)
                sum+=cell;
        if (sum>0) {
            builder.append("\n********** Token Results **********\n");
            builder.append(toString(microCount));
        }
        builder.append("---------- Arg Results ------------\n");
        builder.append(toString(macroCount));
        builder.append("************************\n");
        
        return builder.toString();
    }
    
    public String toMacroString() {
    	return toString(macroCount);
    }
    
    public String toSimpleString() {
    	StringBuilder builder = new StringBuilder();
    	List<Score> scores = getScores(macroCount);
    	builder.append(scores.get(scores.size()-1));
    	builder.append('\n');
    	return builder.toString();
    }
        
    
    List<Score> getScores(int[][] count) {
        List<Score> scores = new ArrayList<Score>(labelSet.size());
        int pTotal=0, rTotal=0, fTotal=0;
        double p, r, f;
        for (String label: labelSet) {
            if (label.equals(SRArg.NOT_ARG)) continue;
            
            int idx = labelMap.get(label);
            
            int pArgT=0, rArgT=0, fArgT=0;
            
            for (int i=0; i<count[idx].length; ++i) pArgT+=count[idx][i];
            for (int i=0; i<count.length; ++i) rArgT+=count[i][idx];
            
            fArgT = count[idx][idx];

            p = pArgT==0?0:((double)fArgT)/pArgT;
            r = rArgT==0?0:((double)fArgT)/rArgT;
            f = p==0?0:(r==0?0:2*p*r/(p+r));

            scores.add(new Score(label, p, r, f, pArgT, rArgT, fArgT));
            
            pTotal += pArgT;
            rTotal += rArgT;
            fTotal += fArgT;
        }
        
        p = pTotal==0?0:((double)fTotal)/pTotal;
        r = rTotal==0?0:((double)fTotal)/rTotal;
        f = p==0?0:(r==0?0:2*p*r/(p+r));
        
        scores.add(new Score("all", p, r, f, pTotal, rTotal, fTotal));
        return scores;
        
    }
    
    public double getFScore() {
        List<Score> scores = getScores(macroCount);
        return scores.get(scores.size()-1).f;
    }
    
    String toString(int[][] count)  {
        StringBuilder builder = new StringBuilder();
        
        for (Score score:getScores(count)) {
            builder.append(score.toString());
            builder.append("\n");
        }
        return builder.toString();
    }
}
