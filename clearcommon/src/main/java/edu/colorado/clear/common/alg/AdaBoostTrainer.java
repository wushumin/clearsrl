/**
* Copyright (c) 2009, Regents of the University of Colorado
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
* Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/
package edu.colorado.clear.common.alg;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.colorado.clear.common.util.JArrays;

/**
 * Trains one-vs-all RRM classifiers.
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/01/2010
 */
public class AdaBoostTrainer extends SuperTrainer
{
    private String model_file;
    /** Number of iterations */
    private int T;
    /** Maximum margin */
    private double v;
    
    /**
     * Trains weight vectors for all labels using one-vs-all method.
     * @param instanceFile name of the file containing training instances
     * @param modelFile prefix-name of the model file
     * @param numThreads max-number of threads to use 
     * @param T number of iteration
     * @param mu prior
     * @param eta learning rate
     * @param c regularization parameter
     * @throws FileNotFoundException 
     */
    public AdaBoostTrainer(String instanceFile, String modelFile, int numThreads, double v) throws FileNotFoundException
    {
        super(instanceFile);
        
        init(modelFile, v);
        train(numThreads);
    }
    
    /**
     * Initializes member variables.
     * @param modelFile prefix-name of the model file
     * @param K number of iteration
     * @param mu prior
     * @param eta learning rate
     * @param c regularization parameter
     */
    private void init(String modelFile, double v)
    {
        model_file = modelFile;
        T          = (int)(2 * Math.log(N) / (v*v));
        this.v     = v;
    }
    
    /**
     * Trains weight vectors for all labels using multi-threads.
     * @param numThreads max-number of threads to use
     */
    private void train(int numThreads)
    {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);;
        System.out.println("\n* Training");
        
        short[] aLabels = s_labels.toArray();
        Arrays.sort(aLabels);
        
        for (short currLabel : aLabels)
            executor.execute(new TrainTask(currLabel));
        
        executor.shutdown();
    }
        
    /**
     * Trains RRM classifier for {@link TrainTask#curr_label}.
     * This class is called from {@link AdaBoostTrainer#train()}.
     */
    class TrainTask implements Runnable
    {
        /** Current label to train */
        short curr_label;
        
        /**
         * Trains RRM model using {@link SuperTrainer#a_features} and {@link SuperTrainer#a_labels} with respect to <code>currLabel</code>.
         * @param currLabel current label to train ({@link this#curr_label})
         */
        public TrainTask(short currLabel)
        {
            curr_label = currLabel;
        }
        
        @Override
		public void run()
        {
            double[] gWeight = new double[D];
            double   gAlpha  = 0;
            double[] weight  = new double[D];
            double[] gamma   = new double[T];
            double[] ada     = new double[N];   Arrays.fill(ada, (double)1/N);
            
            for (int t=1; t<=T; t++)
            {
                Arrays.fill (weight, 0);
                updateWeight(weight, ada);
                
                double alpha = updateAda(t, weight, ada, gamma);
                if (Double.isNaN(alpha))    alpha = 1;
                System.out.println("l = "+curr_label+", t = "+t+", alpha = "+alpha);
                
                if (Math.abs(alpha) == 1)
                {
                    JArrays.copy(gWeight, weight);
                    gAlpha = alpha;
                    break;
                }
                
                updateGlobalWeight(gWeight, weight, alpha);
                gAlpha += alpha;
            }
            
            if (Math.abs(gAlpha) != 1)  normalize(gWeight, gAlpha);
            
            String filename = model_file + ".l"+curr_label + ".v"+v;
            try {
                printWeight(filename, curr_label, gWeight);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        
        /**
         * Updates the weights and alpha using RRM.
         * @param pWeight positive weights
         * @param nWeight negative weights
         * @param ada alpha
         */
        public void updateWeight(double[] weight, double[] ada)
        {
            for (int i=0; i<N; i++)
            {
                // retreive x_i, y_i
                int[] x_i = a_features.get(i);
                int   y_i = (curr_label == a_labels.get(i)) ? 1 : -1;
                
                // calculate penalty
                double eta = (getScore(weight, x_i) * y_i > 0) ? 0 : ada[i] * y_i;
                
                // update weights
                weight[0] += eta;
                for (int idx : x_i) weight[idx] += eta;
            }
        }
        
        private double updateAda(int t, double[] weight, double[] ada, double[] gamma)
        {
            for (int i=0; i<N; i++)
            {
                int[] x_i = a_features.get(i);
                int   y_i = (curr_label == a_labels.get(i)) ? 1 : -1;
                
                gamma[t] += ada[i] * y_i * sign(getScore(weight, x_i));
            }
        
            if (Math.abs(gamma[t]) == 1)    return gamma[t];
            
            double gamma_min = getMin(t, gamma);
            double rho       = gamma_min - v;
            double alpha     = 0.5 * Math.log((1+gamma[t])/(1-gamma[t])) - 0.5 * Math.log((1+rho)/(1-rho));
            double z         = 0;
            
            for (int i=0; i<N; i++)
            {
                int[] x_i = a_features.get(i);
                int   y_i = (curr_label == a_labels.get(i)) ? 1 : -1;
                
                double exp = Math.exp(-alpha * y_i * sign(getScore(weight, x_i)));
                z += ada[i] * exp;
                ada[i] *= exp;
            }
            
            for (int i=0; i<N; i++) ada[i] /= z;

            return alpha;
        }
        
        private void updateGlobalWeight(double[] gWeight, double[] weight, double alpha)
        {
            for (int i=0; i<gWeight.length; i++)
                gWeight[i] += alpha * weight[i];
        }
        
        private void normalize(double[] gWeight, double gAlpha)
        {
            for (int i=0; i<gWeight.length; i++)
                gWeight[i] /= gAlpha;
        }
        
        private int sign(double d)
        {
            return (d > 0) ? 1 : -1;
        }
        
        private double getMin(int t, double[] gamma)
        {
            double min = Double.MAX_VALUE;
            
            for (int i=0; i<=t; i++)
                min = Math.min(min, gamma[i]);
            
            return min;
        }
    }
}