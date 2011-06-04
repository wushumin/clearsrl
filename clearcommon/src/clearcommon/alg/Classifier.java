package clearcommon.alg;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectIntHashMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;

public abstract class Classifier implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public enum InstanceFormat {
        DEFAULT,
        SVM
    };
    
    transient Properties prop;
    
    TObjectIntHashMap<String> labelMap;
    String[] labels;
    TIntIntHashMap labelIdxMap;

    int[] labelIdx;
    
    Classifier(TObjectIntHashMap<String> labelMap, Properties prop)
    {
        this.labelMap = labelMap;
        this.prop = prop;
        labels = labelMap.keys(new String[labelMap.size()]);
        Arrays.sort(labels);
        labelIdxMap = new TIntIntHashMap();
        
        labelIdx = new int[labels.length];
        for (int i=0; i<labels.length; ++i)
        {
            labelIdx[i] = labelMap.get(labels[i]);
            labelIdxMap.put(labelIdx[i], i);
        }
    }
    
    public int getClassCnt()
    {
        return labels.length;
    }
    
    public void train(int [][] X, int[] Y)
    {
        train(X, Y, null);
    }
    
    public abstract void train(int [][] X, int[] Y, double[] weightY);
    
    public abstract int predict(int[] x);
    
    public abstract Classifier getNewInstance();
    
    public int predictProb(int[] x, double[] prob)
    {
        int label = predict(x);
        prob[labelIdxMap.get(label)]=1;
        return label;
    }
    
    public int predictValues(int[] x, double[] val)
    {
        int label = predict(x);
        val[labelIdxMap.get(label)]=1;
        return label;
    }

    public int[] getLabelIdx() {
        return labelIdx;
    }
    
    public TIntIntHashMap getLabelIdxMap() {
        return labelIdxMap;
    }
}
