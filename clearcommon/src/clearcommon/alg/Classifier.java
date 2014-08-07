package clearcommon.alg;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;

import java.io.Serializable;
import java.util.BitSet;
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
    
    Properties prop;
    String[] labels;
    TObjectIntMap<String> labelMap;
    int dimension = -1;
    
    Classifier(){
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
        this.prop = (Properties)prop.clone();
    }
    
    public int getClassCnt() {
        return labelMap.size();
    }
    
    
    public BitSet getFeatureMask() {
    	return null;
    }
    
    public Object getNativeFormat(int[] x) {
    	return x;
    }
    
    
    public void train(int [][] X, int[] Y) {
        train(X, Y, null);
    }
    
    public  void train(int[][] X, int[] Y, double[] weightY) {
    	makeDimension(X);
    	Object[] xNative = new Object[X.length];
    	for (int i=0; i<X.length; ++i)
    		xNative[i] = getNativeFormat(X[i]);
    	trainNative(xNative, Y, weightY);
    }
    
    public void trainNative(Object[] X, int[] Y) {
    	trainNative(X, Y, null);
    }

    public abstract void trainNative(Object[] X, int[] Y, double[] weightY);
    

    
    public int predict(int[] x) {
    	return predictNative(getNativeFormat(x));
    }

    public abstract int predictNative(Object x);
    
    public Classifier getNewInstance() {
        Classifier classifier = null;
        try {
            classifier = this.getClass().newInstance();
            classifier.dimension = this.dimension;
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
    
    public void makeDimension(int[][] X) {
    	if (dimension<=0) {
    		for (int[] x:X)
    			for (int xi:x)
    				if (xi>dimension)
    					dimension = xi;
    		dimension++;
    	}
    }
    
    public void setDimension(int dimension) {
    	this.dimension = dimension;
    }
    
    public int getDimension() {
    	return dimension;
    }
    
    public boolean canPredictProb() {
        return false;
    }
    
    public int predictProb(int[] x, double[] prob) {
        return predictProbNative(getNativeFormat(x), prob);
    }
    
    public int predictProbNative(Object x, double[] prob) {
        int label = predictNative(x);
        prob[label-1]=1;
        return label;
    }
    
    public int predictValues(int[] x, double[] val) {
        return predictValuesNative(getNativeFormat(x), val);
    }
    
    public int predictValuesNative(Object x, double[] val) {
        int label = predictNative(x);
        val[label-1]=1;
        return label;
    }
    
    public static int getTopLabel(double[] val) {
    	 int highIdx = 0;
         for (int i=0; i<val.length; ++i) {
             if (val[i]>val[highIdx])
                 highIdx = i;
         }
         return highIdx+1;
    }
}
