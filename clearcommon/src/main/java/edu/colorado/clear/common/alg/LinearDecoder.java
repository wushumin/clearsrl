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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import edu.colorado.clear.common.util.tuple.JIntDoubleTuple;

/**
 * Decoder for linear classifiers.
 * @author Jinho D. Choi
 * <br><b>Last update:</b> 1/22/2010
 */
public class LinearDecoder
{
    /** Weight vectors for all labels in sparse-vector format. */
    private SparseVector[] a_weight;
    
    /**
     * Initializes weight vectors from <code>modelFile</code>.
     * @param modelFile name of the file containing weight-vectors
     * @param numLabels total number of labels
     */
    public LinearDecoder(String modelFile, int numLabels)
    {
        a_weight = getWeights(modelFile, numLabels);
    }

    /**
     * Returns weight vectors for all labels.
     * <code>modelFile</code> consists of one weight vector per line.
     * @param modelFile name of the file containing weight vectors
     * @param numLabels total number of labels
     */
    public SparseVector[] getWeights(String modelFile, int numLabels)
    {
        System.out.print("Intiializing weights: ");
        SparseVector[] sWeight = new SparseVector[numLabels];

        try (BufferedReader fin = new BufferedReader(new FileReader(modelFile))) {
        //  BufferedReader fin = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(modelFile))));
            String line;   int i;
            
            for (i=0; (line = fin.readLine()) != null; i++)
            {
                String[] vector = line.split(SuperTrainer.COL_DELIM);
                int       label = Integer.parseInt(vector[0]);
                int   [] aIndex = new int   [vector.length-1];
                double[] aValue = new double[vector.length-1];
                
                for (int j=1; j<vector.length; j++)
                {
                    String[] str = vector[j].split(SuperTrainer.FTR_DELIM);
                    aIndex[j-1]  = Integer.parseInt   (str[0]);
                    aValue[j-1]  = Double .parseDouble(str[1]);
                }
                
                sWeight[label] = new SparseVector(aIndex, aValue);
                if (i%10 == 0)  System.out.print("\rInitializing weights: "+i+"+");
            }   System.out.println("\rInitializing weights: "+i+" ");
        }
        catch (IOException e) {e.printStackTrace();}
        
        return sWeight;
    }
    
    /**
     * Returns priors for all labels.
     * <code>priorFile</code> consists of one prior per line.
     * @param priorFile name of the file containing priors
     * @param numLabels total number of labels
     * @throws FileNotFoundException 
     */
    public double[] getPriors(String priorFile, int numLabels) throws FileNotFoundException {
    	Scanner  scan   = new Scanner(new BufferedReader(new FileReader(priorFile)));
        double[] priors = new double[numLabels];
        
        while (scan.hasNextLine()) {
            String[]  ls = scan.nextLine().split(SuperTrainer.COL_DELIM);
            int    label = Integer.parseInt  (ls[0]);
            double prior = Double.parseDouble(ls[1]);
            
            priors[label] = prior;
        }
        scan.close();
        return priors;
    }
    
    /**
     * Returns a predicated label and its score for a feature vector <code>x</code>. 
     * @param x feature vector
     * @param min minimum score (<= 0) to be in the positive zone
     * @return predicated label and its score for a feature vector<code>x</code>
     */
    public JIntDoubleTuple get(ArrayList<Integer> x, double min)
    {
        JIntDoubleTuple max = new JIntDoubleTuple(-1, min);
        
        for (int label=0; label<a_weight.length; label++)
        {
            if (a_weight[label] == null)    continue;
            double score = a_weight[label].getScore(x);
            if (score >= max.value) max.set(label, score);
        }
        
        return max;
    }
    
    public double getScore(int label, int[] x)
    {
        return (a_weight[label] != null) ? a_weight[label].getScore(x) : -1;
    }
    
    public int predict(int[] x)
    {
        int label=-1;
        double highScore=Double.NEGATIVE_INFINITY;
        double score;
        for (int i=0; i<a_weight.length;++i)
            if ((score=getScore(i,x))>highScore)
            {
                highScore = score;
                label = i;
            }
        
        if (a_weight.length==1)
            return label>0?0:1;
        return label;
    }
    
    public JIntDoubleTuple get(ArrayList<Integer> x, double min, double max)
    {
        JIntDoubleTuple best = new JIntDoubleTuple(-1, min);
        
        for (int label=0; label<a_weight.length; label++)
        {
            if (a_weight[label] == null)    continue;
            double score = a_weight[label].getScore(x);
            if (score > max) score = max;
            score -= min;
            
            if (score >= best.value)    best.set(label, score);
        }
        
        return best;
    }
    
    /**
     * Returns a sorted list of predicated labels and their scores for a feature vector <code>x</code>.
     * The list is sorted in descending order with respect to the scores.
     * @param x feature vector
     * @param max maximum score
     * @param min minimum score (<= 0) to be in the positive zone
     */
    public ArrayList<JIntDoubleTuple> getAll(ArrayList<Integer> x, double min, double max)
    {
        ArrayList<JIntDoubleTuple> aPrediction = new ArrayList<JIntDoubleTuple>();
        
        outer: for (short label=0; label<a_weight.length; label++)
        {
            if (a_weight[label] == null)    continue;
            double score = a_weight[label].getScore(x);
            if (score < min)    continue;
            score  = (score > max) ? max : score;
            score -= min;
            
            for (int i=0; i<aPrediction.size(); i++)
            {
                if (aPrediction.get(i).value < score)
                {
                    aPrediction.add(i, new JIntDoubleTuple(label, score));
                    continue outer;
                }
            }
            
            aPrediction.add(new JIntDoubleTuple(label, score));
        }
        
        return aPrediction;
    }
}
