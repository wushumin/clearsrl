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
package clearcommon.alg;

import clearcommon.alg.Classifier.InstanceFormat;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Trains one-vs-all RRM classifiers.
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/07/2010
 */
public class RRMTrainer extends SuperTrainer
{
	private String model_file;
	/** Number of iteration */
	private int K;
	/** Prior */
	private double mu;
	/** Learning rate */
	private double eta;
	/** Regularization parameter */
	private double c;
	
	/**
	 * Trains weight vectors for all labels using one-vs-all method.
	 * @param instanceFile name of the file containing training instances
	 * @param modelFile prefix-name of the model file
	 * @param numThreads max-number of threads to use 
	 * @param K number of iteration
	 * @param mu prior
	 * @param eta learning rate
	 * @param c regularization parameter
	 */
	public RRMTrainer(String instanceFile, String modelFile, int numThreads, int K, double mu, double eta, double c)
	{
		super(instanceFile);
		
		init(modelFile, K, mu, eta, c);
		train(numThreads);
	}
	
	/**
	 * Trains weight vectors for all labels using one-vs-all method.
	 * @param instanceFile name of the file containing training instances
	 * @param format format of the instance file
	 * @param modelFile prefix-name of the model file
	 * @param numThreads max-number of threads to use 
	 * @param K number of iteration
	 * @param mu prior
	 * @param eta learning rate
	 * @param c regularization parameter
	 */
	public RRMTrainer(String instanceFile, InstanceFormat format, String modelFile, int numThreads, int K, double mu, double eta, double c)
	{
		super(instanceFile, format);
		
		init(modelFile, K, mu, eta, c);
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
	private void init(String modelFile, int K, double mu, double eta, double c)
	{
		model_file = modelFile;
		this.K     = K;
		this.mu    = mu;
		this.eta   = eta;
		this.c     = c;
	}
	
	/**
	 * Trains weight vectors for all labels using multi-threads.
	 * @param numThreads max-number of threads to use
	 * @throws InterruptedException 
	 */
	private void train(int numThreads)
	{
		ExecutorService executor = Executors.newFixedThreadPool(numThreads);;
		System.out.println("\n* Training");
		
		short[] aLabels = s_labels.toArray();
		Arrays.sort(aLabels);
		
		if (aLabels.length<2)
			return;

		for (short currLabel : aLabels)
			executor.execute(new TrainTask(currLabel));
		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e)
		{
			
		}
	}
		
	/**
	 * Trains RRM classifier for {@link TrainTask#curr_label}.
	 * This class is called from {@link RRMTrainer#train()}.
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
		
		public void run()
		{
			double[] pWeight = new double[D];	Arrays.fill(pWeight, mu);
			double[] nWeight = new double[D];	Arrays.fill(nWeight, mu);
			double[] alpha   = new double[N];
			double[] bWeight = new double[D];
			double   bestAcc = -1;
			int      bestK   = 0;
			
			for (int k=1; k<=K; k++)
			{
				updateWeight(pWeight, nWeight, alpha);
				
				double currAcc = getF1Score(pWeight, nWeight);
				if (bestAcc < currAcc)
				{
					setWeight(bWeight, pWeight, nWeight);
					bestAcc = currAcc;
					bestK   = k;
				}
				
				System.out.println("l = "+curr_label+", k = "+k+", acc = "+currAcc);
				if (currAcc == 1)	break;
			}
			
			System.out.println("l = "+curr_label+", best-k = "+bestK+", best-acc = "+bestAcc);
			normalize(bWeight);
			
			String filename = model_file + ".l"+curr_label + ".m"+mu + ".e"+eta + ".c"+c;
			printWeight(filename, curr_label, bWeight);
		}
		
		/**
		 * Updates the weights and alpha using RRM.
		 * @param pWeight positive weights
		 * @param nWeight negative weights
		 * @param alpha alpha
		 */
		public void updateWeight(double[] pWeight, double[] nWeight, double[] alpha)
		{
			for (int i=0; i<N; i++)
			{
				// retreive x_i, y_i
				int[] x_i = a_features.get(i);
				int   y_i = (curr_label == a_labels.get(i)) ? 1 : -1;
				
				// calculate p
				double p = getScore(pWeight, nWeight, x_i) * y_i;
				
				// calculate delta
			/*	double min1      = 2*c - alpha[i];
				double min2      = eta * ((c - alpha[i])/c - p);*/
				double min1      = c - alpha[i];
				double min2      = eta * (1 - p);
				double min       = Math.min(min1, min2);
				double delta     = Math.max(min, -alpha[i]);
				double delta_y_i = delta * y_i;
				
				// update weights
				pWeight[0] *= Math.exp( delta_y_i);
				nWeight[0] *= Math.exp(-delta_y_i);
				
				for (int idx : x_i)
				{
					pWeight[idx] *= Math.exp( delta_y_i);
					nWeight[idx] *= Math.exp(-delta_y_i);
				}

				// update alpha (boosting factor)
				alpha[i] += delta;
			}
		}
		
		/**
		 * Returns the score of a training instance <code>x</code> using the balanced weight vectors.
		 * @param pWeight positive weight vector
		 * @param nWeight negative weight vector
		 * @param x training instance (indices start from 1)
		 */
		private double getScore(double[] pWeight, double[] nWeight, int[] x)
		{
			double score = pWeight[0] - nWeight[0];
			
			for (int idx : x)		
				score += (pWeight[idx] - nWeight[idx]);
			
			return score;
		}
		
		/** bWeight[i] = pWeight[i] - nWeight[i] */
		private void setWeight(double[] bWeight, double[] pWeight, double[] nWeight)
		{
			for (int i=0; i<D; i++)	bWeight[i] = pWeight[i] - nWeight[i];
		}
		
		
		/**
		 * Returns F1 score of the balanced weight vectors.
		 * @param pWeight positive weight vector
		 * @param nWeight negative weight vector
		 * @return
		 */
		private double getF1Score(double[] pWeight, double[] nWeight)
		{
			int correct = 0, pTotal = 0, rTotal = 0;
			
			for (int i=0; i<N; i++)
			{
				int[]  x_i   = a_features.get(i);
				int    y_i   = (curr_label == a_labels.get(i)) ? 1 : -1;
				double score = getScore(pWeight, nWeight, x_i);
			
				if (score >= 0)
				{
					if (y_i == 1)	correct++;
					pTotal++;
				}
				
				if (y_i == 1)	rTotal++;
			}
			
			if (pTotal + rTotal == 0)	return 0;

			double precision = (double)correct / pTotal;
			double recall    = (double)correct / rTotal;
			System.out.printf("precision %f , recall %f\n", precision, recall);
			return 2 * (precision * recall) / (precision + recall);
		}
	}
}