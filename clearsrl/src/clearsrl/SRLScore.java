package clearsrl;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class SRLScore {
    
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
            if (SRLModel.NOT_ARG.equals(argType))
                continue;
            labelMap.put(argType, ++count);
        }
        microCount = new int[count+1][count+1];
        macroCount = new int[count+1][count+1];
    }
    
    String[] getLabels(List<SRArg> args, int tokenCount) {
        String[] strs = new String[tokenCount];
        Arrays.fill(strs, SRLModel.NOT_ARG);
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
        
        SRInstance instance = new SRInstance(lhs.predicateNode, lhs.tree);
        
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
                
        SRInstance instance = new SRInstance(lhs.predicateNode, lhs.tree);
        
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
    
    public void addResult(String systemLabel, String goldLabel) {
        macroCount[labelMap.get(systemLabel)][labelMap.get(goldLabel)]++;
    }
    
    public void addResult(SRInstance systemSRL, SRInstance goldSRL) {
        List<SRArg> sysArgs = systemSRL.getScoringArgs();
        List<SRArg> goldArgs = goldSRL.getScoringArgs();
        
        String[] sysStr = getLabels(sysArgs, systemSRL.tree.getTokenCount());       
        String[] goldStr = getLabels(goldArgs, goldSRL.tree.getTokenCount());
        
        for (int i=0; i<sysStr.length; ++i) {
            if (sysStr[i]==SRLModel.NOT_ARG && goldStr[i]==SRLModel.NOT_ARG)
                continue;
            microCount[labelMap.get(sysStr[i])][labelMap.get(goldStr[i])]++;
        }

        for (int i=0, j=0; i<sysArgs.size() || j<goldArgs.size();) {
            if (i>=sysArgs.size()) {
                macroCount[labelMap.get(SRLModel.NOT_ARG)][labelMap.get(goldArgs.get(j).label)]++;
                ++j;
                continue;
            }
            if (j>=goldArgs.size()) {
                macroCount[labelMap.get(sysArgs.get(i).label)][labelMap.get(SRLModel.NOT_ARG)]++;
                ++i;
                continue;
            }
            
            int compare = sysArgs.get(i).compareTo(goldArgs.get(j));
            if (compare<0) {
                macroCount[labelMap.get(sysArgs.get(i).label)][labelMap.get(SRLModel.NOT_ARG)]++;
                ++i;
            } else if (compare>0) {
                macroCount[labelMap.get(SRLModel.NOT_ARG)][labelMap.get(goldArgs.get(j).label)]++;
                ++j;
            } else {
                if (sysArgs.get(i).tokenSet.equals(goldArgs.get(j).tokenSet))
                    macroCount[labelMap.get(sysArgs.get(i).label)][labelMap.get(goldArgs.get(j).label)]++;
                else {
                    macroCount[labelMap.get(sysArgs.get(i).label)][labelMap.get(SRLModel.NOT_ARG)]++;
                    macroCount[labelMap.get(SRLModel.NOT_ARG)][labelMap.get(goldArgs.get(j).label)]++;
                }
                ++i; ++j;
            }   
        }
    }
    
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
        builder.append("************************\n\n");
        
        return builder.toString();
    }
        
    
    List<Score> getScores(int[][] count) {
        List<Score> scores = new ArrayList<Score>(labelSet.size());
        int pTotal=0, rTotal=0, fTotal=0;
        double p, r, f;
        for (String label: labelSet) {
            if (label.equals(SRLModel.NOT_ARG)) continue;
            
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
