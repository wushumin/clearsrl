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

import gnu.trove.TIntDoubleHashMap;

import java.util.Arrays;
import java.util.ArrayList;


/**
 * Sparse vector.
 * @author Jinho D. Choi
 * <b>Last update:</b> 11/19/2009
 */
public class SparseVector
{
	/** HashMap using primitive keys and values */
	private TIntDoubleHashMap m_vector;
	/** Bias (b_0) of the vector */
	private double            d_bias;
	/** Initial value (default is 0) */
	private double            d_mu;
	
	/**
	 * Initialize values of all dimensions to <code>mu</code>. 
	 * @param mu initial value
	 */
	public SparseVector(double mu)
	{
		m_vector = new TIntDoubleHashMap();
		d_mu     = mu;
	}
	
	/**
	 * Initializes the sparse-vector with <code>aIndex</code> and <code>aValue</code>.
	 * Values not in <code>aIndex</code> are initialized to 0.
	 * @param aIndex array containing indices
	 * @param aValue array containing values
	 */
	public SparseVector(int[] aIndex, double[] aValue)
	{
		m_vector = new TIntDoubleHashMap(aIndex.length-1);
		d_bias   = aValue[0];
		d_mu     = 0;
		
		for (int i=1; i<aIndex.length; i++)
			m_vector.put(aIndex[i], aValue[i]);
	}
		
	/**
	 * Returns the score of a feature vector <code>x</code>.
	 * <code>x</code> is a binary feature vector consists of only indices whose values are 1.
	 * @param x binary feature vector
	 * @return the score of a feature vector <code>x</code>
	 */
	public double getScore(ArrayList<Integer> x)
	{
		double score = d_bias;
		
		for (int idx : x)	score += m_vector.get(idx);
		return score;
	}
	
	/**
	 * Returns the score of a feature vector <code>x</code>.
	 * <code>x</code> is a binary feature vector consists of only indices whose values are 1.
	 * @param x binary feature vector
	 * @return the score of a feature vector <code>x</code>
	 */
	public double getScore(int[] x)
	{
		double score = d_bias;
		
		for (int idx : x)	score += m_vector.get(idx);
		return score;
	}
	
	/**
	 * Returns the value corresponding to <code>index</code>.
	 * @param index index to get value of
	 * @return value corresponding to <code>index</code>
	 */
	public double get(int index)
	{
		return m_vector.containsKey(index) ? m_vector.get(index) : d_mu;
	}
	
	/** <code>vector[index] *= value</code>. */
	public void multiply(int index, double value)
	{
		if (m_vector.containsKey(index))
			m_vector.put(index, m_vector.get(index) * value);
		else
			m_vector.put(index, d_mu * value);
	}
	
	/** <code>vector[index] += value</code>. */
	public void add(int index, double value)
	{
		if (m_vector.containsKey(index))
			m_vector.put(index, m_vector.get(index) + value);
		else
			m_vector.put(index, d_mu + value);
	}
	
	/**
	 * Returns all indices with values in ascending order.
	 * @return all indices with values in ascending order
	 */
	public int[] getIndices()
	{
		int[] keys = m_vector.keys();
		Arrays.sort(keys);
		
		return keys;
	}
	
	@Override
    public String toString()
	{
		int[] indices = getIndices();
		String    str = "";
		
		for (int idx : indices)
			str += SuperTrainer.COL_DELIM + idx + SuperTrainer.FTR_DELIM + m_vector.get(idx);
		
		return str.trim();
	}
}
