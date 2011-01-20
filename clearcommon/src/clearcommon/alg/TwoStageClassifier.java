package clearcommon.alg;

import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;

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
    
    TwoStageClassifier(TObjectIntHashMap<String> labelMap, double cutoff, Properties prop) {
        super(labelMap, prop);
        this.cutoff = cutoff;
    }

    @Override
    public void train(int[][] X, int[] Y, double[] weightY) {
        
        int[] Y1 = new int[Y.length];
        
        for (int i=0; i<Y.length; ++i)
        {
            Y1[i] = Y[i]==labelIdx[0]?labelIdx[0]:labelIdx[1];
        }
        
        TObjectIntHashMap<String> lMap = new TObjectIntHashMap<String>();
        lMap.put(labels[0], labelIdx[0]);
        lMap.put(labels[1], labelIdx[1]);
        
        Properties propMod = (Properties)prop.clone();
        propMod.setProperty("liblinear.solverType", "L2R_LR");
        
        stageOneClassifier = new LinearClassifier(lMap, propMod);
        
        stageOneClassifier.train(X, Y1);
        
        double p=0, pd=0, r=0, rd=0, f=0;
        
        ArrayList<int[]> X2 = new ArrayList<int[]>();
        TIntArrayList    Y2 = new TIntArrayList();
        double [] prob = new double[2];
        for (int i=0; i<Y.length; ++i)
        {
            int pred = stageOneClassifier.predictProb(X[i], prob);
            
            if (pred==labelIdx[1])
            {
                if (prob[0]<cutoff)
                    p++;
                pd++;
            }
            
            if (Y1[i]==labelIdx[1])
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
        
        TObjectIntHashMap<String> labelMap = new TObjectIntHashMap<String>();
        for (int i=0; i<labels.length;++i)
            labelMap.put(labels[i], labelIdx[i]);
        
        stageTwoClassifier = new PairWiseClassifier(labelMap, prop);
        
        stageTwoClassifier.train(X2.toArray(new int[X2.size()][]), Y2.toNativeArray(), weightY);
    }
    
    @Override
    public int predict(int[] x) {
        double [] prob = new double[2];
        int pred = stageOneClassifier.predictProb(x, prob);
        if (prob[0]>=cutoff)
            return pred;
        
        return stageTwoClassifier.predict(x);
    }

}
