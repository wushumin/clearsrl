package clearcommon.alg;

import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;

public class PairWiseClassifier extends Classifier implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Classifier [][] classifiers;
	Classifier probClassifier;
	
	double[] values;
	
	public PairWiseClassifier(TObjectIntHashMap<String> labelMap, Properties prop)
	{
		super(labelMap, prop);
		classifiers = new Classifier[labelIdx.length][labelIdx.length];
		labelMap.getValues();
		values = new double[labelIdx.length];
	}
	
	public int predict(int[] x)
	{
	    return predictValues(x, values);
	}
	
	@Override
	public int predictValues(int[] x, double[] values) {
		//double[] prob = new double[probClassifier.getClassCnt()];
		//probClassifier.predictProb(x, prob);
		Arrays.fill(values, 0);
		for (int i=0; i<labels.length-1; ++i)
			for (int j=i+1; j<labels.length; ++j)
			{
				double[] probs = new double[classifiers[i][j].getClassCnt()];
				int label = classifiers[i][j].predictValues(x,probs);
				//double a = 1 / (1 + Math.exp(-probs[0]));
				//double b = 1 / (1 + Math.exp(-probs[1]));
				//double s = a+b;
				
				//values[i]+= a/s;
				//values[j]+= b/s;
				
				if (label==labelIdx[i])
					values[i]++;
				else if (probs[0]<0)
					values[j]++;
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
		int highIdx = 0;
		for (int i=1; i<values.length; ++i)
			if (values[i]>values[highIdx])
				highIdx = i;
		
		int cnt = 0;
		for (int i=0; i<values.length; ++i)
			if (values[i]==values[highIdx])
				cnt++;
		
		if (cnt>1)
			System.out.print("TIE ");
			
		return labelIdx[highIdx];
		
	}
	
	@Override
	public void train(int[][] X, int[] Y, double[] weightY) {
		
		TIntArrayList[] classLabels = new TIntArrayList[labels.length];
		for (int i=0; i<labels.length; ++i)
			classLabels[i] = new TIntArrayList();
		for (int i=0; i<Y.length; ++i)
			classLabels[labelIdxMap.get(Y[i])].add(i);
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
		for (int i=0; i<labels.length-1; ++i)
		{
			for (int j=i+1; j<labels.length; ++j)
			{
				System.out.printf("Training %s(%d) -- %s(%d) (%d)\n", labels[i], classLabels[i].size(), labels[j], classLabels[j].size(), Y.length);
				System.out.println("Pairwise:");
				{
					TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
					map.put(labels[i], labelIdx[i]);
					map.put(labels[j], labelIdx[j]);
					
					classifiers[i][j] = new LinearClassifier(map, prop);
					
					int[][] XPair = new int[classLabels[i].size()+classLabels[j].size()][];
					int[] YPair = new int[classLabels[i].size()+classLabels[j].size()];
					for (int c=0; c<classLabels[i].size(); ++c)
					{
						XPair[c] = X[classLabels[i].get(c)];
						YPair[c] = labelIdx[i];
					}
					for (int c=0; c<classLabels[j].size(); ++c)
					{
						XPair[c+classLabels[i].size()] = X[classLabels[j].get(c)];
						YPair[c+classLabels[i].size()] = labelIdx[j];
					}
					double [] weights =null;
					if (weightY!=null)
					{
					    weights = new double[2];
					    weights[0] = weightY[i];
					    weights[1] = weightY[j];
					    System.out.println(weights[0]+" "+weights[1]);
					}
					classifiers[i][j].train(XPair, YPair, weights);
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
	}


}
