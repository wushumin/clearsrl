package clearcommon.alg;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

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
    
    String[] labels;
    
    TObjectIntMap<String> labelMap;
    
    Classifier()
    {
    }
    
    /**
     * initializes the classifier 
     * @param labelMap The string class labels are for debugging purposes, the label values must be 1-size of classes
     * @param prop
     */
    public void initialize(TObjectIntMap<String> labelMap, Properties prop) {
        labels = new String[labelMap.size()];
        for (TObjectIntIterator<String> iter=labelMap.iterator(); iter.hasNext();) {
        	iter.advance();
        	labels[iter.value()-1] = iter.key();
        }
    	
        this.labelMap = labelMap;
        this.prop = prop;
    }
    
    public int getClassCnt() {
        return labelMap.size();
    }
    
    public void train(int [][] X, int[] Y) {
        train(X, Y, null);
    }
    
    public abstract void train(int [][] X, int[] Y, double[] weightY);
    
    public abstract int predict(int[] x);
    
    public Classifier getNewInstance() {
        Classifier classifier = null;
        try {
            classifier = this.getClass().newInstance();
            classifier.initialize(labelMap, prop);
            return classifier;
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public boolean canPredictProb() {
        return false;
    }
    
    public int predictProb(int[] x, double[] prob) {
        int label = predict(x);
        prob[label-1]=1;
        return label;
    }
    
    public int predictValues(int[] x, double[] val) {
        int label = predict(x);
        val[label-1]=1;
        return label;
    }
}
