package clearcommon.alg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

public class CrossValidator {
    
    class TrainJob implements Runnable{
        int f;
        Classifier cf;
        int[][] Xtrain;
        int[] ytrain;
        double[][] prob;
        int[][] X;
        int[] ytest;
        TIntHashSet permSet;
        int[] seed;
        
        public TrainJob(int f, Classifier cf, int[][]Xtrain, int[]ytrain, double[][] prob, int[][] X, int[]ytest, TIntHashSet permSet, int[] seed)
        {
            this.f = f;
            this.cf = cf;
            this.Xtrain = Xtrain;
            this.ytrain = ytrain;
            this.prob = prob;
            this.X = X;
            this.ytest = ytest;
            this.permSet = permSet;
            this.seed = seed;
        }
        
        @Override
        public void run() {
        	System.out.printf("*********** Training fold %d ***************\n",f+1);
        	
            cf.train(Xtrain, ytrain);
            
            if (f<0) return;
            
            for (int i=0; i<ytest.length; ++i)
                if (permSet.contains(seed[i])) {
                	if (prob==null)
                		ytest[i] = cf.predict(X[i]);
                	else
                		ytest[i] = cf.predictProb(X[i], prob[i]);
                }
        }
    }
    
    Classifier classifier;
    int threads;
    
    public CrossValidator(Classifier classifier) {
        this(classifier, 1);
    }
    
    public CrossValidator(Classifier classifier, int threads) {
        this.classifier = classifier;
        this.threads = threads;
    }

    public static void randomPermute(int[] perm, Random rand) {
        for (int i = 0; i < perm.length; i++) {
            int r = (int) (rand.nextDouble() * (i+1));     // int between 0 and i
            int swap = perm[r];
            perm[r] = perm[i];
            perm[i] = swap;
         }
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y) {
        return validate(foldNum, X, y, null, null, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] prob) {
        return validate(foldNum, X, y, prob, null, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, int[] seed) {
        return validate(foldNum, X, y, null, seed, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] prob, int[] seed) {
        return validate(foldNum, X, y, prob, seed, false);
    }
 
    /**
     * @param foldNum number of validation folds
     * @param X feature vector
     * @param y label
     * @param prob probability vector of the predicted labels if desired
     * @param seed seed vector used to select samples for each fold (same seed value will be in the same fold)
     * @param trainAll train an classifier with all the samples
     * @return predicted labels through cross validation
     */
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] prob, int[] seed, boolean trainAll) {
        ExecutorService executor = null;
        if (threads>1) executor = Executors.newFixedThreadPool(threads);
          
        if (trainAll) { 
            TrainJob job = new TrainJob(-1, classifier, X, y, null, null, null, null, null);
            if (executor!=null) executor.submit(job);
            else job.run();
        }
         
        int[] ytest = new int[y.length];
        
        int[] perm = null;
        
        if (seed!=null) {
            TIntHashSet seedSet = new TIntHashSet(seed);
            perm = seedSet.toArray();
        } else {
            seed = new int[y.length];
            for (int i=0; i<seed.length; ++i)
                seed[i] = i;
            perm = seed.clone();
        }
        
        randomPermute(perm, new Random(y.length));

        for (int f=0; f<foldNum; ++f)  {   
            TIntHashSet permSet = new TIntHashSet();
            for (int i=f; i<perm.length; i+=foldNum)
                permSet.add(perm[i]);
            
            List<int[]> Xtrain = new ArrayList<int[]>();
            TIntArrayList ytrain = new TIntArrayList();
            List<double[]> probTrain=null;
            
            if (prob!=null)
            	probTrain = new ArrayList<double[]>();
            
            for (int i=0; i<y.length; ++i)
                if (!permSet.contains(seed[i])) {
                    Xtrain.add(X[i]);
                    ytrain.add(y[i]);
                    probTrain.add(prob[i]);
                }
            
            Classifier cf = classifier.getNewInstance();
            TrainJob job = new TrainJob(f, cf, Xtrain.toArray(new int[Xtrain.size()][]), 
            		ytrain.toNativeArray(), prob==null?null:probTrain.toArray(new double[probTrain.size()][]), X, ytest, permSet, seed);
            
            if (executor!=null) executor.submit(job);
            else job.run();
        }

        if (executor!=null) {
            executor.shutdown();
            while (true) {
                try {
                    if (executor.awaitTermination(5, TimeUnit.MINUTES)) break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return ytest;
    }
}
