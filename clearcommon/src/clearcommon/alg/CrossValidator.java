package clearcommon.alg;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CrossValidator {
    
    class TrainJob implements Runnable{
        int fold;
        Classifier cf;
        int[][] X;
        int[] y;
        int[] yValidate;
        double[][] labelValues;
        BitSet testIndices;
        
        public TrainJob(int fold, Classifier cf, int[][] X, int[] y, int[] yValidate, double[][] labelValues, BitSet validateIndices)
        {
            this.fold = fold;
            this.cf = cf;
            this.X = X;
            this.y = y;
            this.yValidate = yValidate;
            this.labelValues = labelValues;
            this.testIndices = validateIndices;
        }
        
        @Override
        public void run() {
            System.out.printf("*********** Training fold %d ***************\n",fold+1);
            if (testIndices==null || testIndices.isEmpty()) {
            	cf.train(X, y);
            	return;
            }

            List<int[]> Xtrain = new ArrayList<int[]>();
            TIntArrayList ytrain = new TIntArrayList();
            for (int i=testIndices.nextClearBit(0) ; i<y.length; i=testIndices.nextClearBit(i+1)) {
                Xtrain.add(X[i]);
                ytrain.add(y[i]);
            }
            cf.train(Xtrain.toArray(new int[Xtrain.size()][]), ytrain.toArray());
            
            for (int i=testIndices.nextSetBit(0) ; i>=0; i=testIndices.nextSetBit(i+1))
            	yValidate[i] = labelValues==null?cf.predict(X[i]):cf.predictValues(X[i], labelValues[i]);
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
    
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] labelValues) {
        return validate(foldNum, X, y, labelValues, null, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, int[] seed) {
        return validate(foldNum, X, y, null, seed, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] labelValues, int[] seed) {
        return validate(foldNum, X, y, labelValues, seed, false);
    }
    
    public int[] validate(int foldNum, int[][] X, int[] y, int[] seed, boolean trainAll) {
        return validate(foldNum, X, y, null, seed, trainAll);
    }
 
    /**
     * @param foldNum number of validation folds
     * @param X feature vector
     * @param y label
     * @param yValues raw class values from classifier if desired
     * @param seed seed vector used to select samples for each fold (same seed value will be in the same fold)
     * @param trainAll train an classifier with all the samples
     * @return predicted labels through cross validation
     */
    public int[] validate(int foldNum, int[][] X, int[] y, double[][] yValues, int[] seed, boolean trainAll) {
        ExecutorService executor = null;
        if (threads>1) executor = Executors.newFixedThreadPool(threads);
          
        if (trainAll) { 
            TrainJob job = new TrainJob(foldNum, classifier, X, y, null, null, null);
            if (executor!=null) executor.execute(job);
            else job.run();
        }
         
        int[] yValidate = new int[y.length];
        
        int[] perm = null;
        
        if (seed!=null) {
            TIntSet seedSet = new TIntHashSet(seed);
            perm = seedSet.toArray();
        } else {
            seed = new int[y.length];
            for (int i=0; i<seed.length; ++i)
                seed[i] = i;
            perm = seed.clone();
        }
        
        randomPermute(perm, new Random(y.length));

        for (int f=0; f<foldNum; ++f)  {   
            TIntSet permSet = new TIntHashSet();
            for (int i=f; i<perm.length; i+=foldNum)
                permSet.add(perm[i]);
            
            BitSet validateIndices = new BitSet(y.length);
            
            for (int i=0; i<y.length; ++i)
                if (permSet.contains(seed[i]))
                	validateIndices.set(i);
            
            Classifier cf = classifier.getNewInstance();
            TrainJob job = new TrainJob(f, cf, X, y, yValidate, yValues, validateIndices);
            
            if (executor!=null) executor.execute(job);
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
        
        return yValidate;
    }
}
