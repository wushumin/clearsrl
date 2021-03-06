package edu.colorado.clear.common.alg;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Properties;

public class TwoStageClassifier extends Classifier implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -3806477906565319097L;
    
    Classifier stageOneClassifier;
    Classifier stageTwoClassifier;
    double cutoff;
    
    TwoStageClassifier() {
    }
    
    @Override
	public void initialize(TObjectIntMap<String> labelMap, Properties prop) {
        super.initialize(labelMap, prop);
        this.cutoff = Double.parseDouble(prop.getProperty("TwoStageClassifier.cutoff"));
    }

    @Override
    public void trainNative(Object[] X, int[] Y, double[] weightY) {
        
        int[] Y1 = new int[Y.length];
        
        for (int i=0; i<Y.length; ++i) {
            Y1[i] = Y[i]==1?1:2;
        }
        
        TObjectIntMap<String> lMap = new TObjectIntHashMap<String>();
        lMap.put(labels[0], 1);
        lMap.put(labels[1], 2);
        
        Properties propMod = (Properties)prop.clone();
        propMod.setProperty("liblinear.solverType", "L2R_LR");
        
        stageOneClassifier = new LinearClassifier();
        stageOneClassifier.initialize(lMap, propMod);
        
        stageOneClassifier.trainNative(X, Y1);
        
        double p=0, pd=0, r=0, rd=0, f=0;
        
        ArrayList<Object> X2 = new ArrayList<Object>();
        TIntArrayList    Y2 = new TIntArrayList();
        double [] prob = new double[2];
        for (int i=0; i<Y.length; ++i)
        {
            int pred = stageOneClassifier.predictProbNative(X[i], prob);
            
            if (pred==2)
            {
                if (prob[0]<cutoff)
                    p++;
                pd++;
            }
            
            if (Y1[i]==2)
            {
                if (prob[0]<cutoff)
                    r++;
                rd++;
            }
            
            if (prob[0]<cutoff)
            {
                X2.add(X[i]);
                Y2.add(Y[i]);
            }
        }
        p = p/pd;
        r = r/rd;
        f = 2*p*r/(p+r);
        System.out.printf("Stage 1 (%f): precision=%f, recall=%f, f-score=%f\n", cutoff, p, r, f);
        
        TObjectIntMap<String> labelMap = new TObjectIntHashMap<String>();
        for (int i=0; i<labels.length;++i)
            labelMap.put(labels[i], i+1);
        
        stageTwoClassifier = new PairWiseClassifier();
        stageTwoClassifier.initialize(labelMap, prop);
        
        stageTwoClassifier.trainNative(X2.toArray(), Y2.toArray(), weightY);
    }
    
    @Override
    public int predictNative(Object x) {
        double [] prob = new double[2];
        int pred = stageOneClassifier.predictProbNative(x, prob);
        if (prob[0]>=cutoff)
            return pred;
        
        return stageTwoClassifier.predictNative(x);
    }
}
