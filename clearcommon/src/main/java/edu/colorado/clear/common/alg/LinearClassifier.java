package edu.colorado.clear.common.alg;

import gnu.trove.map.TObjectIntMap;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Properties;

import edu.colorado.clear.liblinearbinary.Linear;
import edu.colorado.clear.liblinearbinary.SolverType;

public class LinearClassifier extends Classifier implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    SolverType solverType;
    double C;
    double eps = 1e-3;
    double bias = -1;
    edu.colorado.clear.liblinearbinary.Model model;
    int[] mLabelIdx;
    
    public LinearClassifier() {
    }
    
    @Override
    public void initialize(TObjectIntMap<String> labelMap, Properties prop) {
    	super.initialize(labelMap, prop);
    	
        try {
            solverType = SolverType.valueOf(prop.getProperty("liblinear.solverType", SolverType.L2R_L1LOSS_SVC_DUAL.toString()));
        } catch (IllegalArgumentException e) {
            solverType = SolverType.L2R_L1LOSS_SVC_DUAL;
            System.err.println("Invalid solver type: "+prop.getProperty("liblinear.solverType")+", using "+solverType);
        }
        
        C = 1;
        try {
            C = Double.parseDouble(prop.getProperty("liblinear.C", "1"));
        } catch (NumberFormatException e) {
            System.err.println("Invalid C value: "+prop.getProperty("liblinear.C")+", using C="+C);
        }
 
        bias = Double.parseDouble(prop.getProperty("liblinear.bias", "-1"));
    }
    
    @Override
    public int predictNative(Object x) {
        return Linear.predict(model, (int[])x);
        //return predict(convertToNodes(x));
    }
    
    @Override
    public boolean canPredictProb() {
        return solverType==SolverType.L2R_LR;
    }
    
    @Override
    public int predictProbNative(Object x, double[] prob) {

        double[] values = new double[model.getNrClass()];
        int label = Linear.predictProbability(model, (int[])x, values);
        
        Arrays.fill(prob, 0);
        
        if (label==0) {
            label = Linear.predictValues(model, (int[])x, values);
            prob[label-1]=1;
            return label;
        }
        
        for (int i=0; i<mLabelIdx.length; ++i)
            prob[mLabelIdx[i]-1] = values[i];
        return label;
    }
    
    @Override
    public int predictValuesNative(Object x, double[] val) {
    	double[] values = new double[model.getNrClass()];
        int label = Linear.predictValues(model, (int[])x, values);
        
        Arrays.fill(val, 0);
        for (int i=0; i<mLabelIdx.length; ++i)
            val[mLabelIdx[i]-1] = values[i];
        return label;
    }

    public void train (edu.colorado.clear.liblinearbinary.Problem problem)
    {
        edu.colorado.clear.liblinearbinary.Parameter param = new edu.colorado.clear.liblinearbinary.Parameter(solverType,C,eps);
        
        model = Linear.train(problem, param);
        mLabelIdx = model.getLabels();
    }
    
    
    @Override
	public BitSet getFeatureMask() {
    	BitSet mask = new BitSet();
    	double[] w = model.getFeatureWeights();
    	for (int i=0; i<w.length; ++i) {
    		if (w[i]!=0)
    			mask.set(i/model.getNrClass());
    	}
    	return mask;
    }
    
    @Override
	public Object getNativeFormat(int[] x) {
    	if (bias<=0)
    		return x;
    	int[] xMod = Arrays.copyOf(x, x.length+1);
    	xMod[x.length] = dimension>0?dimension+1:model.getNrFeature();
    	return xMod;
    }

    int[] convertToLibLinear(int[] x) {
        int[] xMod = new int[x.length+(model.getBias()>=0?1:0)];
        for (int i=0; i<x.length; ++i)
            xMod[i] = x[i]+1;
        if (model.getBias() >=0)
            xMod[xMod.length-1] = model.getNrFeature();
        return xMod;
    }
    
    /*
    FeatureNode[] convertToNodes(int[] x)
    {
        FeatureNode[] nodes = new FeatureNode[x.length+(model.getBias()>=0?1:0)];
        for (int i=0; i<x.length; ++i)
            nodes[i] = new FeatureNode(x[i]+1,1.0f);
        
        if (model.getBias() >=0)
            nodes[nodes.length-1] = new FeatureNode(model.getNrFeature(), model.getBias());
        
        return nodes;
    }
    */
    
    public static edu.colorado.clear.liblinearbinary.Problem convertToProblem(Object[] X, int[] Y, double[] weightY, double bias, int dimension) {
        edu.colorado.clear.liblinearbinary.Problem problem = new edu.colorado.clear.liblinearbinary.Problem();
        
        problem.bias = bias>0?1:-1;
        problem.l = X.length;
        problem.x = new int[X.length][];
        for (int i=0; i<X.length;++i)
            problem.x[i] = (int[])X[i];
        
        /*
        problem.x = new liblinear.FeatureNode[X.length][];
        
        for (int i=0; i<X.length;++i)
        {
            problem.x[i] = new FeatureNode[X[i].length+(problem.bias>=0?1:0)];
            for (int j=0; j<X[i].length;++j)
            {
                problem.x[i][j] = new FeatureNode(X[i][j]+1,1.0f);
                problem.n = Math.max(problem.n, problem.x[i][j].index);
            }
        }*/
        
        problem.n = problem.bias>0?dimension+1:dimension;
        
        /*if (problem.bias >=0) {
            ++problem.n;
            for (int i=0; i<X.length;++i)
                problem.x[i][problem.x[i].length-1] = problem.n;
                //problem.x[i][problem.x[i].length-1] = new FeatureNode(problem.n, problem.bias);
        }*/
        problem.y = Y;
        return problem;
    }

    @Override
    public void trainNative(Object[] X, int[] Y, double[] weightY) {
        train(convertToProblem(X,Y, weightY, bias, dimension));
    }
/*
    @Override
    public Classifier getNewInstance() {
        // TODO Auto-generated method stub
        Classifier classifier = new LinearClassifier();
        classifier.initialize(labelMap, prop);
        return classifier;
    }
*/

}
