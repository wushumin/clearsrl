package clearcommon.alg;

import java.io.Serializable;
import java.util.Properties;

import liblinearbinary.Linear;
import liblinearbinary.SolverType;

import gnu.trove.TObjectIntHashMap;

public class LinearClassifier extends Classifier implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	SolverType solverType;
	double C;
	double eps = 1e-3;
	liblinearbinary.Model model;
	double[] values;
	int[] mLabelIdx;
	
	public LinearClassifier(TObjectIntHashMap<String> labelMap, Properties prop)
	{
		super(labelMap, prop);
	}

	@Override
    public int predict(int[] x) {
		return Linear.predictValues(model, convertToLibLinear(x), values);
		//return predict(convertToNodes(x));
	}
/*
	public int predict(FeatureNode[] x) {
		return Linear.predictValues(model, x, values);
	}
*/
	@Override
	public int predictProb(int[] x, double[] prob) {
		int label = Linear.predictProbability(model, convertToLibLinear(x), values);
		
		for (int i=0; i<values.length; ++i)
			prob[labelIdxMap.get(mLabelIdx[i])] = values[i];
		return label;
	}
	
	@Override
	public int predictValues(int[] x, double[] val) {
		int label = Linear.predictValues(model, convertToLibLinear(x), values);
		for (int i=0; i<values.length; ++i)
			val[labelIdxMap.get(mLabelIdx[i])] = values[i];
		return label;
	}

	public void train (liblinearbinary.Problem problem)
	{
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
		
		liblinearbinary.Parameter param = new liblinearbinary.Parameter(solverType,C,eps);
		
		model = Linear.train(problem, param);
		values = new double[model.getNrClass()];
		mLabelIdx = model.getLabels();
	}
	
	int[] convertToLibLinear(int[] x)
	{
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
	public static liblinearbinary.Problem convertToProblem(int[][] X, int[] Y, double[] weightY, double bias)
	{
		liblinearbinary.Problem problem = new liblinearbinary.Problem();
		
		problem.bias = bias;
		problem.l = X.length;
		problem.x = new int[X.length][];
		for (int i=0; i<X.length;++i)
		{
			problem.x[i] = new int[X[i].length+(bias>0?1:0)];
			for (int j=0; j<X[i].length;++j)
			{
				problem.x[i][j] = X[i][j]+1;
				problem.n = Math.max(problem.n, problem.x[i][j]);
			}
		}
		
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
		
		if (problem.bias >=0)
		{
			++problem.n;
			for (int i=0; i<X.length;++i)
				problem.x[i][problem.x[i].length-1] = problem.n;
				//problem.x[i][problem.x[i].length-1] = new FeatureNode(problem.n, problem.bias);
		}
		problem.y = Y;
		return problem;
	}

	@Override
	public void train(int[][] X, int[] Y, double[] weightY) {
		train(convertToProblem(X,Y, weightY, Double.parseDouble(prop.getProperty("liblinear.bias", "-1"))));
	}

    @Override
    public Classifier getNewInstance() {
        // TODO Auto-generated method stub
        return new LinearClassifier(labelMap, prop);
    }


}
