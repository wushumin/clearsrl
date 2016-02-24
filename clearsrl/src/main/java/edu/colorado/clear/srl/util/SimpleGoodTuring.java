package clearsrl.util;

/**
 * Simple Good-Turing smoothing, based on code from Sampson, available at:
 * ftp://ftp.informatics.susx.ac.uk/pub/users/grs2/SGT.c <p/>
 *
 * See also http://www.grsampson.net/RGoodTur.html
 * 
 * @author Bill MacCartney (wcmac@cs.stanford.edu)
 */
public class SimpleGoodTuring {

	private static final int MIN_INPUT = 5;
	private static final double CONFID_FACTOR = 1.96;
	private static final double TOLERANCE = 1e-12;

	private int[] r; // for each bucket, a frequency
	private int[] n; // for each bucket, number of items w that frequency
	private int rows; // number of frequency buckets

	private int bigN = 0; // total count of all items
	private double pZero; // probability of unseen items
	private double bigNPrime;
	private double slope;
	private double intercept;
	private double[] z;
	private double[] logR;
	private double[] logZ;
	private double[] rStar;
	private double[] p;

	/**
	 * Each instance of this class encapsulates the computation of the smoothing
	 * for one probability distribution. The constructor takes two arguments
	 * which are two parallel arrays. The first is an array of counts, which
	 * must be positive and in ascending order. The second is an array of
	 * corresponding counts of counts; that is, for each i, n[i] represents the
	 * number of types which occurred with count r[i] in the underlying
	 * collection. See the documentation for main() for a concrete example.
	 */
	public SimpleGoodTuring(int[] r, int[] n) {
		if (r == null)
			throw new IllegalArgumentException("r must not be null!");
		if (n == null)
			throw new IllegalArgumentException("n must not be null!");
		if (r.length != n.length)
			throw new IllegalArgumentException("r and n must have same size!");
		if (r.length < MIN_INPUT)
			throw new IllegalArgumentException("r must have size >= "
			        + MIN_INPUT + "!");
		this.r = new int[r.length];
		this.n = new int[n.length];
		System.arraycopy(r, 0, this.r, 0, r.length); // defensive copy
		System.arraycopy(n, 0, this.n, 0, n.length); // defensive copy
		this.rows = r.length;
		compute();
		validate(TOLERANCE);
	}

	/**
	 * Returns the probability allocated to types not seen in the underlying
	 * collection.
	 */
	public double getProbabilityForUnseen() {
		return pZero;
	}

	/**
	 * Returns the probabilities allocated to each type, according to their
	 * count in the underlying collection. The returned array parallels the
	 * arrays passed in to the constructor. If the returned array is designated
	 * p, then for all i, p[i] represents the smoothed probability assigned to
	 * types which occurred r[i] times in the underlying collection (where r is
	 * the first argument to the constructor).
	 */
	public double[] getProbabilities() {
		return p;
	}

	private void compute() {
		int i, j, next_n;
		double k, x, y;
		boolean indiffValsSeen = false;

		z = new double[rows];
		logR = new double[rows];
		logZ = new double[rows];
		rStar = new double[rows];
		p = new double[rows];

		for (j = 0; j < rows; ++j)
			bigN += r[j] * n[j]; // count all items
		next_n = row(1);
		pZero = (next_n < 0) ? 0 : n[next_n] / (double) bigN;
		for (j = 0; j < rows; ++j) {
			i = (j == 0 ? 0 : r[j - 1]);
			if (j == rows - 1)
				k = (double) (2 * r[j] - i);
			else
				k = (double) r[j + 1];
			z[j] = 2 * n[j] / (k - i);
			logR[j] = Math.log(r[j]);
			logZ[j] = Math.log(z[j]);
		}
		findBestFit();
		for (j = 0; j < rows; ++j) {
			y = (r[j] + 1) * smoothed(r[j] + 1) / smoothed(r[j]);
			if (row(r[j] + 1) < 0)
				indiffValsSeen = true;
			if (!indiffValsSeen) {
				x = (r[j] + 1) * (next_n = n[row(r[j] + 1)]) / (double) n[j];
				if (Math.abs(x - y) <= CONFID_FACTOR
				        * Math.sqrt(sq(r[j] + 1.0) * next_n
				                / (sq((double) n[j]))
				                * (1 + next_n / (double) n[j])))
					indiffValsSeen = true;
				else
					rStar[j] = x;
			}
			if (indiffValsSeen)
				rStar[j] = y;
		}
		bigNPrime = 0.0;
		for (j = 0; j < rows; ++j)
			bigNPrime += n[j] * rStar[j];
		for (j = 0; j < rows; ++j)
			p[j] = (1 - pZero) * rStar[j] / bigNPrime;
	}

	/**
	 * Returns the index of the bucket having the given frequency, or else -1 if
	 * no bucket has the given frequency.
	 */
	private int row(int freq) {
		int i = 0;
		while (i < rows && r[i] < freq)
			i++;
		return ((i < rows && r[i] == freq) ? i : -1);
	}

	private void findBestFit() {
		double XYs, Xsquares, meanX, meanY;
		int i;
		XYs = Xsquares = meanX = meanY = 0.0;
		for (i = 0; i < rows; ++i) {
			meanX += logR[i];
			meanY += logZ[i];
		}
		meanX /= rows;
		meanY /= rows;
		for (i = 0; i < rows; ++i) {
			XYs += (logR[i] - meanX) * (logZ[i] - meanY);
			Xsquares += sq(logR[i] - meanX);
		}
		slope = XYs / Xsquares;
		intercept = meanY - slope * meanX;
	}

	private double smoothed(int i) {
		return (Math.exp(intercept + slope * Math.log(i)));
	}

	private static double sq(double x) {
		return (x * x);
	}

	/**
	 * Ensures that we have a proper probability distribution.
	 */
	private void validate(double tolerance) {
		double sum = pZero;
		for (int i = 0; i < n.length; i++) {
			sum += (n[i] * p[i]);
		}
		double err = 1.0 - sum;
		if (Math.abs(err) > tolerance) {
			throw new IllegalStateException(
			        "ERROR: the probability distribution sums to " + sum);
		}
	}
	  
	public String toString() {
		StringBuilder builder = new StringBuilder();
	
	    int i;
	    builder.append(String.format("%6s %6s %8s %8s%n", "r", "n", "p", "p*"));
	    builder.append(String.format("%6s %6s %8s %8s%n", "----", "----", "----", "----"));
	    builder.append(String.format("%6d %6d %8.4g %8.4g%n", 0, 0, 0.0, pZero));
	    for (i = 0; i < rows; ++i)
	    	builder.append(String.format("%6d %6d %8.4g %8.4g%n", r[i], n[i], 1.0 * r[i] / bigN, p[i]));
	    
	    
	    double factor = pZero;
	    for (i = 0; i < rows; ++i)
	    	factor += n[i]*p[i];
	    builder.append(String.format("factor: %8.4g%n", bigN/factor));
	    
	    return builder.toString();
	}
	
	public static void main(String[] args) throws Exception {
	    int[] r = {2,3,5,8,10};
	    int[] n = {6,4,2,2,1};
	    SimpleGoodTuring sgt = new SimpleGoodTuring(r, n);
	   	System.out.println(sgt.toString());
	}
}
