package clearcommon.alg;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

public class FeatureSelector extends SuperTrainer
{
	private int[]    n_x1;
	private double[] d_rel;
	private double[] d_mut;
	
	public FeatureSelector(String instanceFile, String modelFile) throws FileNotFoundException
	{
		super(instanceFile);
		init();
		
		short[] aLabels = s_labels.toArray();
		Arrays.sort(aLabels);
		
		for (short currLabel : aLabels)
		{
			measureRel(currLabel);
			System.out.println("l = "+currLabel+": measure relevance");
			print(modelFile+"."+currLabel);
		}
	}
	
	private void init()
	{
		n_x1  = new int[D];
		d_rel = new double[D];
		d_mut = new double[D];
		
		for (int i=0; i<N; i++)
		{
			int[] x = a_features.get(i);
			for (int idx : x)	n_x1[idx]++;
		}
	}
	
	private void measureRel(int currLabel)
	{
		int     y1  = 0;
		int[][] yx1 = new int[2][D];
		
		for (int i=0; i<N; i++)
		{
			int   y = (a_labels.get(i) != currLabel) ? 0 : 1;
			int[] x = a_features.get(i);
		
			y1 += y;
			for (int idx : x)	yx1[y][idx]++;
		}
		
		double[] py = {(double)(N - y1)/N, (double)y1/N};
		
		for (int i=1; i<D; i++)
		{
			int y0x1 = yx1[0][i], y0x0 = N - y1 - y0x1;
			int y1x1 = yx1[1][i], y1x0 =     y1 - y1x1;
			
			double[]   px  = {(double)(N - n_x1[i])/N, (double)n_x1[i]/N};
			double[][] pyx = {{(double)y0x0/N, (double)y0x1/N}, {(double)y1x0/N, (double)y1x1/N}};
			
			d_mut[i] = d_rel[i] = measureMut(px, py, pyx);
		}
	}
	
	private double measureMut(double[] px, double[] py, double[][] pyx)
	{
		double mut = 0;
		
		for (int i=0; i<py.length; i++)
			for (int j=0; j<px.length; j++)
			{
				double num = pyx[i][j]   + Double.MIN_VALUE;
				double den = py[i]*px[j] + Double.MIN_VALUE;
				mut += pyx[i][j] * log2(num / den);
			}

		return mut;
	}
	
	double log2(double a)
	{
		return Math.log(a) / Math.log(2);
	}
	
	private void print(String filename)
	{
		PrintStream fout;
        try {
            fout = new PrintStream(new FileOutputStream(filename));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
		
		for (int i=1; i<D; i++)
			if (d_mut[i] > 0)	fout.println(i + COL_DELIM + d_mut[i]);
		
		fout.close();
	}
}
