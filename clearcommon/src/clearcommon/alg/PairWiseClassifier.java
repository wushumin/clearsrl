package clearcommon.alg;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PairWiseClassifier extends Classifier implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    Classifier [][] classifiers;
    
    int topN;
    
    int threads;
    
    class TrainJob implements Runnable{
        Classifier cf;
        Object[] X;
        int[] y;
        double[] weights;
        
        public TrainJob(Classifier cf, Object[] X, int[]y, double[] weights)
        {
            this.cf = cf;
            this.X = X;
            this.y = y;
            this.weights = weights;
        }
        
        @Override
        public void run() {
            cf.trainNative(X, y, weights);
        }
    }
    
    class PredictJob implements Runnable {
        Classifier cf;
        int[] x;
        public PredictJob(Classifier cf, int[] x) {
            this.cf = cf;
            this.x = x;
        }
        
        @Override
        public void run() {
            cf.predict(x);
        }
    }
    
    public PairWiseClassifier(){
    }
    
    public void initialize(TObjectIntMap<String> labelMap, Properties prop) {
        super.initialize(labelMap, prop);

        
        classifiers = new Classifier[labelMap.size()][labelMap.size()];
        
        topN = ((int)Math.round(labelMap.size()*0.1))+1;
        threads = Integer.parseInt(prop.getProperty("pairwise.threads","1"));
    }
    
    @Override
    public int predictNative(Object x) {
        return predictValuesNative(x, new double[labelMap.size()]);
    }
    
    @Override
    public int predictValuesNative(Object x, double[] values) {
        //double[] prob = new double[probClassifier.getClassCnt()];
        //probClassifier.predictProb(x, prob);
    	
    	BitSet[] valueMatrix = new BitSet[labelMap.size()];
        for (int i=0; i<valueMatrix.length;++i)
            valueMatrix[i] = new BitSet(labelMap.size());
        
        for (int i=0; i<labelMap.size()-1; ++i)
            for (int j=i+1; j<labelMap.size(); ++j) {
                double[] probs = new double[classifiers[i][j].getClassCnt()];
                int label = classifiers[i][j].predictValuesNative(x,probs);
                //double a = 1 / (1 + Math.exp(-probs[0]));
                //double b = 1 / (1 + Math.exp(-probs[1]));
                //double s = a+b;
                
                //values[i]+= a/s;
                //values[j]+= b/s;
                
                if (label==1) {
                    values[i]++;
                    valueMatrix[i].set(j);
                }
                else {
                    values[j]++;
                    valueMatrix[j].set(i);
                }
                /*
                if (classifiers[i][j].predict(x)==labelIdx[i])
                    values[i]++;
                else
                    values[j]++;
                    */
            }
                /*
                if (classifiers[i][j].predict(x)==labelIdx[i])
                    values[i]+= prob[i]+prob[j];
                else
                    values[j]+= prob[i]+prob[j];
        */
        /*
        for (int i=0; i<labels.length-1; ++i)
            for (int j=i+1; j<labels.length; ++j)
            {
                double prob = classifiers[j][i].predictProb(x)[];
                if (classifiers[i][j].predict(x)==labelIdx[i])
                    values[i]+=prob;
                else
                    values[j]+=prob;
            }
        */
        
        if (topN>=1) {
            Arrays.sort(values);
            double cutoffValue = values[values.length-2];
            BitSet mask = new BitSet(labelMap.size());
           
            
            
            for (int i=0; i<valueMatrix.length; ++i)
                if (valueMatrix[i].cardinality()>=cutoffValue)
                    mask.set(i);
            
            for (int i=0; i<valueMatrix.length; ++i) {
                //values[i] = valueMatrix[i].cardinality()/(values.length*100-100.0);
                valueMatrix[i].and(mask);
                values[i] = valueMatrix[i].cardinality()/(mask.cardinality()-1.0);
                //values[i] /= 1.01;
            }
        }
        else
        {
            for (int i=0; i<values.length; ++i)
                values[i] /= values.length-1;
        }
    
        int highIdx = 0;
        for (int i=0; i<values.length; ++i)
        {
            if (values[i]>values[highIdx])
                highIdx = i;
        }
        //int cnt = 0;
        //for (int i=0; i<values.length; ++i)
        //  if (values[i]==values[highIdx])
        //      cnt++;
        //if (cnt>1) System.out.print("TIE ");
            
        return highIdx+1;

    }
    
    @Override
    public void trainNative(Object[] X, int[] Y, double[] weightY) {
        
        TIntArrayList[] classLabels = new TIntArrayList[labelMap.size()];
        for (int i=0; i<labelMap.size(); ++i)
            classLabels[i] = new TIntArrayList();
        for (int i=0; i<Y.length; ++i)
            classLabels[Y[i]-1].add(i);
        /*
        // the probabilistic classifier can only use L2R_LR
        {
            Properties propMod = (Properties)prop.clone();
            propMod.setProperty("liblinear.solverType", "L2R_LR");
            TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
            for (int i=0; i<labels.length; ++i)
                map.put(labels[i], labelIdx[i]);
            probClassifier = new LinearClassifier(map, propMod);
            System.out.println("Training probabilistic classifier");
            probClassifier.train(X, Y);
        }
        */

        ExecutorService executor = null;
        if (threads>1) executor = Executors.newFixedThreadPool(threads);
        
        for (int i=0; i<labelMap.size()-1; ++i) {
            for (int j=i+1; j<labelMap.size(); ++j) {
                System.out.printf("Training %s(%d) -- %s(%d) (%d)\n", labels[i], classLabels[i].size(), labels[j], classLabels[j].size(), Y.length);
                System.out.println("Pairwise:");
                {
                    TObjectIntMap<String> map = new TObjectIntHashMap<String>();
                    map.put(labels[i], 1);
                    map.put(labels[j], 2);
                    
                    classifiers[i][j] = new LinearClassifier();
                    classifiers[i][j].dimension = dimension;
                    classifiers[i][j].initialize(map, prop);

                    Object[] XPair = new int[classLabels[i].size()+classLabels[j].size()][];
                    int[] YPair = new int[classLabels[i].size()+classLabels[j].size()];
                    for (int c=0; c<classLabels[i].size(); ++c) {
                        XPair[c] = X[classLabels[i].get(c)];
                        YPair[c] = 1;
                    }
                    for (int c=0; c<classLabels[j].size(); ++c) {
                        XPair[c+classLabels[i].size()] = X[classLabels[j].get(c)];
                        YPair[c+classLabels[i].size()] = 2;
                    }
                    double [] weights =null;
                    if (weightY!=null) {
                        weights = new double[2];
                        weights[0] = weightY[i];
                        weights[1] = weightY[j];
                        System.out.println(weights[0]+" "+weights[1]);
                    }
                    
                    TrainJob job = new TrainJob(classifiers[i][j], XPair, YPair, weights);
                    
                    if (executor !=null) executor.execute(job);
                    else job.run();
                }
                /*
                System.out.println("2 vs rest");
                {
                    TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
                    map.put(labels[i]+"_"+labels[j], 1);
                    map.put("_other_", 2);
                    
                    classifiers[j][i] = new LinearClassifier(map, propMod);
                    
                    int[] YProb = new int[Y.length];
                    for (int c=0; c<YProb.length; ++c)
                        YProb[c] = (Y[c]==labelIdx[i]||Y[c]==labelIdx[j])?1:2;
                    classifiers[j][i].train(X, YProb);
                }
                System.out.println("\n");
                */
            }
        }
        
        if (executor!=null) {
            executor.shutdown();
            while (true)
            {
                try {
                    if (executor.awaitTermination(5, TimeUnit.MINUTES)) break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public BitSet getFeatureMask() {
    	BitSet mask = new BitSet();
    	for (int i=0; i<labelMap.size()-1; ++i)
            for (int j=i+1; j<labelMap.size(); ++j)
            	mask.or(classifiers[i][j].getFeatureMask());
    	return mask;
    }
    
/*
    @Override
    public Classifier getNewInstance() {
        Classifier classifier = new PairWiseClassifier();
        classifier.initialize(labelMap, prop);
        return classifier;
    }
*/
}
