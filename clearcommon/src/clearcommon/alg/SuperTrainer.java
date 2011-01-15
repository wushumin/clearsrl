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

import gnu.trove.TIntArrayList;
import gnu.trove.TShortArrayList;
import gnu.trove.TShortHashSet;
import clearcommon.alg.Classification.InstanceFormat;
import clearcommon.util.JIO;
import clearcommon.util.JArrays;
import clearcommon.util.tuple.JShortDoubleArrayTuple;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Super class for trainer classes.
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/01/2010
 */
public class SuperTrainer
{
	
	/** Delimiter between index and value (e.g. 3:0.12) */
	public static final String FTR_DELIM = ":";
	/** Delimiter between columns (e.g. 0:0.12 3:0.45) */
	public static final String COL_DELIM = " ";
	
	/** Total number of training instances */
	protected int   N; 
	/** Features dimension */
	protected int   D;
	/** Set of labels */
	protected TShortHashSet    s_labels;
	/** Training labels */
	protected TShortArrayList  a_labels;
	/** Training feature-vectors */
	protected ArrayList<int[]> a_features;
		
	/**
	 * Initializes all member fields with training instances in <code>instanceFile</code>.
	 * @see SuperTrainer#init(String, String, int)
	 * @param instanceFile name of the file containing training instances
	 */
	public SuperTrainer(String instanceFile)
	{
		readInstanceFile(instanceFile, InstanceFormat.DEFAULT);
		init();
	}
	
	public SuperTrainer(String instanceFile, InstanceFormat format)
	{
		readInstanceFile(instanceFile, format);
		init();
	}
	
	/**
	 * Initializes all member fields with training instances in <code>instanceFile</code>.
	 * @see SuperTrainer#init(String, String, int)
	 * @param instanceFile name of the file containing training instances
	 */
	public SuperTrainer(ArrayList<int[]> samples, TShortArrayList labels)
	{
		a_features = samples;
		a_labels = labels;
		init();
	}
	
	/**
	 * Reads training instances from file
	 * Reads training instances from <code>instanceFile</code> and stores to 
	 * {@link SuperTrainer#a_features} and {@link SuperTrainer#a_labels}. 
	 * @param instanceFile name of the file containing training instances
	 */
	private void readInstanceFile(String instanceFile, InstanceFormat format)
	{
		final int NUM = 1000000;
		Scanner scan  = JIO.createScanner(instanceFile);
		
		a_labels      = new TShortArrayList (NUM);
		a_features    = new ArrayList<int[]>(NUM);
		
		for (int i=0; scan.hasNextLine(); ++i)
		{
			StringTokenizer tok = new StringTokenizer(scan.nextLine()," \t\n\r:");
			if (format==InstanceFormat.DEFAULT)
			{
				short label         = Short.parseShort(tok.nextToken());
				int[] feature       = JArrays.toIntArray(tok);
			
				// add label and feature
				a_labels  .add(label  );
				a_features.add(feature);
			}
			else if (format==InstanceFormat.SVM)
			{
				a_labels.add((short)(Short.parseShort(tok.nextToken())-1));
				int[] feature = new int[tok.countTokens()/2];
				for (int c=0; c<feature.length; c++)
				{
					feature[c] = Integer.parseInt(tok.nextToken())-1;
					tok.nextToken();
				}
				a_features.add(feature);
			}
			if (i%10000 == 0)	System.out.print("\r* Initializing  : "+i+"+");
		}
		scan.close();
		
		a_labels  .trimToSize();
		a_features.trimToSize();
	}

	/**
	 * Initializes all remaining member fields.
	 */
	private void init()
	{
		s_labels = new TShortHashSet();
		N        = a_features.size();
		
		for (int i=0; i<N; ++i)
		{
			if (a_labels.get(i) >= 0)	s_labels.add(a_labels.get(i));
			D = Math.max(D, a_features.get(i)[a_features.get(i).length-1]);
		}
		
		D++;	// feature dimension = last feature-index + 1

		System.out.println();
		System.out.println("- # of instances: " + N);
		System.out.println("- # of labels   : " + s_labels.size());
		System.out.println("- # of features : " + D);
	}
	
	/**
	 * Returns the score of a training instance <code>x</code> using the weight vector.
	 * @param weight weight vector
	 * @param x training instance (indices start from 1)
	 */
	static public double getScore(double[] weight, int[] x)
	{
		double score = weight[0];
		
		for (int idx : x)	score += weight[idx];
		return score;
	}
	
	/**
	 * Returns f-score of the weight-vector using the training instances.
	 * @param weight weight vector
	 */
	public double getF1Score(short label, double[] weight)
	{
		int correct = 0, pTotal = 0, rTotal = 0;
		
		for (int i=0; i<N; i++)
		{
			int[]  x_i   = a_features.get(i);
			int    y_i   = (label == a_labels.get(i)) ? 1 : -1;
			double score = getScore(weight, x_i);
		
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
		
		return 2 * (precision * recall) / (precision + recall);
	}
	
	/**
	 * Returns a tuple of ((short)label, (double[])weight-vector) representing the model.
	 * @param line line to read the model
	 * @param dimSize dimension size of the weight vector
	 */
	static public JShortDoubleArrayTuple getModel(String line, int dimSize)
	{
		double[] weight = new double[dimSize];
		String[] vector = line.split(COL_DELIM);
		short    label  = Short.parseShort(vector[0]);
		
		for (int j=1; j<vector.length; j++)
		{
			String[] str = vector[j].split(FTR_DELIM);
			int    index = Integer.parseInt   (str[0]);
			double value = Double .parseDouble(str[1]);
			
			weight[index] = value; 
		}
		
		return new JShortDoubleArrayTuple(label, weight);
	}
	
	/**
	 * Prints the weight vector.
	 * @param filename name of the file to print
	 * @param label label for the weight vector
	 * @param weight weight vector
	 */
	static public void printWeight(String filename, short label, double[] weight)
	{
		PrintStream fout = JIO.createPrintFileOutputStream(filename);
		
		printWeight(fout, label, weight);
		fout.close();
	}
	
	static public void printWeight(String filename, short[] labels, double[][] weights)
	{
		PrintStream fout = JIO.createPrintFileOutputStream(filename);
		
		for (short label : labels)
			printWeight(fout, label, weights[label]);
		
		fout.close();
	}
	
	static public void printWeight(PrintStream fout, short label, double[] weight)
	{
		fout.print(label);
		
		for (int i=0; i<weight.length; i++)
			if (weight[i] != 0)	fout.print(COL_DELIM + i + FTR_DELIM + weight[i]);
	
		fout.println();
	}
	
	static public void normalize(double[] weight)
	{
		double norm = 0;
		
		for (int i=0; i<weight.length; i++)
			norm += (weight[i] * weight[i]);
		
		norm = Math.sqrt(norm);
		
		for (int i=0; i<weight.length; i++)
			weight[i] /= norm;
	}
}
