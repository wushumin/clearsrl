package edu.colorado.clear.srl.util;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

public class LDAModel implements Serializable {
	
	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;
    
    class WordTopic {
    	public WordTopic(int[] dist, double count) {
    		this.dist = dist;
    		this.count = count;
    	}
    	int[] dist;
    	double count;
    }
    
	public class Document {
		Document(TIntObjectMap<WordTopic> wordTopicDistribution, int numTopics) {
			this.wordCounts = wordTopicDistribution;
			topicDistribution = new double[numTopics];
			for (TIntObjectIterator<WordTopic> iter=wordTopicDistribution.iterator(); iter.hasNext();) {
				iter.advance();
				for (int i=0; i<iter.value().dist.length; ++i)
					topicDistribution[iter.value().dist[i]]+=(i+1>iter.value().count)?iter.value().count-i:1;
			}
		}
		
		double[] topicDistribution;
		TIntObjectMap<WordTopic> wordCounts;
	}

	static public class SparseCount implements Serializable{
		/**
		 * 
		 */
        private static final long serialVersionUID = 1L;
		public SparseCount(TIntIntMap valMap) {
			indices = valMap.keys(new int[valMap.size()]);
			Arrays.sort(indices);
			values = new double[indices.length];
			for (int i=0; i<indices.length; ++i)
				values[i] = valMap.get(indices[i]);
		}
		
		public SparseCount(double[] dist) {
			TIntList indiceList = new TIntArrayList();
			TDoubleList valueList = new TDoubleArrayList();
			
			for (int i=0; i<dist.length; ++i) {
				if (dist[i]==0)
					continue;
				//int val = (int)Math.round(dist[i]);
				//if (val==0)
				//	continue;
				indiceList.add(i);
				valueList.add(dist[i]);
			}
			indices = indiceList.toArray();
			values = valueList.toArray();
		}
		
		public SparseCount(int[] indices, double[] values) {
			this.indices = indices;
			this.values = values;
		}
		
		public double[] getDenseCount(int dimension) {
			double[] dense = new double[dimension];
			for (int i=0; i<indices.length; ++i)
				dense[indices[i]] = values[i];
			return dense;
		}
		
		public double getTotalCount() {
			double sum = 0;
			for (double value:values)
				sum += value;
			return sum;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append('[');
			for (int i=0; i<indices.length;++i) {
				if (i!=0)
					builder.append(", ");
				builder.append(""+indices[i]+':'+values [i]);
			}
			builder.append(']');
			return builder.toString();
		}
		
		int[] indices;
		double[] values;
	}
	
	TObjectIntMap<String> wordIdxMap;
	TIntObjectMap<String> idxWordMap;
	
	int numTopics=-1;
	SparseCount[] topicCounts;
	long[] globalCounts;
	double alpha;
	double beta;
	boolean lemmatize = false;
	
	transient Random rand;
	
	LDAModel() {
		rand = new Random(System.currentTimeMillis());
	}

	public boolean getLemmatize() {
		return lemmatize;
	}
	
	static SparseCount getSparse(double[] dist) {
		TIntList indices = new TIntArrayList();
		TDoubleList values = new TDoubleArrayList();
		
		for (int i=0; i<dist.length; ++i) {
			if (dist[i]==0)
				continue;
			int val = (int)Math.round(dist[i]);
			if (val==0)
				continue;
			indices.add(i);
			values.add(val);
		}
		return new SparseCount(indices.toArray(), values.toArray());
	}
	
	public static int[] getTopTopics(SparseCount cnts, int maxTopics, double sumThreshold, double highValThreshold) {
		double sum=0;
		for (double value:cnts.values)
			sum+=value;
		if (sum<sumThreshold)
			return null;
		
		double[] valCopy = Arrays.copyOf(cnts.values, cnts.values.length);
		Arrays.sort(valCopy);
		for (int i=0; i<(valCopy.length/2);++i) {
			double tmp = valCopy[i];
			valCopy[i] = valCopy[valCopy.length-i-1];
			valCopy[valCopy.length-i-1] = tmp;
		}
		
		if (valCopy[0]<highValThreshold)
			return null;
		
		double halfHighValThreshold = highValThreshold/2;
		
		double sumSoFar = 0;
		int j=0;
		for (; j<valCopy.length; ++j) {
			sumSoFar += valCopy[j];
			if (j+1==valCopy.length || valCopy[j+1]<halfHighValThreshold || sumSoFar*2>sum && valCopy[j+1]*2<valCopy[j])
				break;
		}
		if (sumSoFar*2<=sum) return null;
		if (j+1>maxTopics) {
			int topSum = 0;
			for (int i=0; i<maxTopics;++i)
				topSum += valCopy[i];
			if (topSum*2<=sum)
				return null;
			j=maxTopics-1;
			/*if (j+1<valCopy.length)
				// make sure to never exceed maxTopics in case of ties
				while (valCopy[j]==valCopy[j+1]) {
					j--;
					if (j<0) return null;
				}*/
		}
		TIntList valList = new TIntArrayList();
		for (int i=0; i<cnts.values.length; ++i)
			if (cnts.values[i]>=valCopy[j])
				valList.add(cnts.indices[i]);
		return valList.toArray();
	}
	
	public Map<String, int[]> getWordTopics() {
		Map<String, int[]> constrainedTopicMap = new HashMap<String, int[]>();
		
		for (int wi=0; wi<topicCounts.length; ++wi) {
			int[] topics = getTopTopics(topicCounts[wi], 3, 10, 5);
			if (topics!=null)
				constrainedTopicMap.put(idxWordMap.get(wi), topics);
		}
		
		return constrainedTopicMap;
	}
	
	int getAccumulativeSample(double[] distribution) {
		double distributionSum = 0.0;
		for (int i = 0; i < distribution.length; ++i)
		    distributionSum += distribution[i];

		double choice = rand.nextDouble() * distributionSum;
		double sumSoFar = 0.0;
		for (int i = 0; i < distribution.length; ++i) {
			sumSoFar += distribution[i];
		    if (sumSoFar >= choice)
		    	return i;
		}

		return -1;
	}
	
	double[] generateTopicDistributionForWord(Document doc, int word) {
		double[] distribution = topicCounts[word].getDenseCount(numTopics);		
		for (int k = 0; k < numTopics; ++k) 
			distribution[k] = (distribution[k]+beta)*
					(doc.topicDistribution[k]+alpha)/
					(globalCounts[k]+topicCounts.length*beta);
			
		return distribution;
	}
	
	void sampleNewTopicsForDocument(Document doc) {
		for (TIntObjectIterator<WordTopic> tIter=doc.wordCounts.iterator();tIter.hasNext();) {
			tIter.advance();
			for (int i=0; i<tIter.value().dist.length; ++i) {
				double[] topicDistribution = generateTopicDistributionForWord(doc, tIter.key());
				int newTopic = getAccumulativeSample(topicDistribution);
				
				if (newTopic<0) continue;
				
				double update = (i+1>tIter.value().count)?tIter.value().count-i:1;
				
				doc.topicDistribution[tIter.value().dist[i]]-=update;
				doc.topicDistribution[newTopic]+=update;
				tIter.value().dist[i] = newTopic;
			}
		}
	}

	public static LDAModel readLDAModel(File modelFile) {
		LDAModel model = null;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(modelFile)),StandardCharsets.UTF_8))) {
			int lineNum = 0;
			model = new LDAModel();
			TIntObjectMap<SparseCount> cntMap = new TIntObjectHashMap<SparseCount>();
			
			model.wordIdxMap = new TObjectIntHashMap<String>();
			model.idxWordMap = new TIntObjectHashMap<String>();
			
			String line;
			while ((line=reader.readLine())!=null) {
				String[] tokens = line.split("\\s+");
				if (lineNum==0) {
					if (tokens.length<3) return null;
					model.numTopics = Integer.parseInt(tokens[0]);
					model.alpha = Double.parseDouble(tokens[1]);
					model.beta = Double.parseDouble(tokens[2]);
					if (tokens.length==4)
						model.lemmatize = Boolean.parseBoolean(tokens[3]);
					lineNum++;
					continue;
				}
				if (tokens.length<=1)
					continue;
				
				TIntIntMap counts = new TIntIntHashMap();
				for (int i=1;i<tokens.length;++i) {
					int split = tokens[i].indexOf(':');
					counts.put(Integer.parseInt(tokens[i].substring(split+1)),Integer.parseInt(tokens[i].substring(0,split)));
				}
				
				model.wordIdxMap.put(tokens[0], lineNum-1);
				model.idxWordMap.put(lineNum-1,tokens[0]);
				cntMap.put(lineNum-1, new SparseCount(counts));		
				lineNum++;
			}
			model.topicCounts = new SparseCount[cntMap.size()];
			model.globalCounts = new long[model.numTopics];
			
			for (TIntObjectIterator<SparseCount> iter=cntMap.iterator(); iter.hasNext();) {
				iter.advance();
				model.topicCounts[iter.key()] = iter.value();
				for (int i=0; i<iter.value().indices.length; ++i)
					model.globalCounts[iter.value().indices[i]] += iter.value().values[i];
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			model = null;
		}
		
		return model;		
	}
	
	public Map<String, double[]> infer(TObjectDoubleMap<String> wordMap, int totalIter, int burnInIter) {

		TIntObjectMap<WordTopic> wordTopicMap = new TIntObjectHashMap<WordTopic>();
		for (TObjectDoubleIterator<String> iter=wordMap.iterator(); iter.hasNext();) {
			iter.advance();
			int wordIdx = wordIdxMap.get(iter.key());
			if (wordIdx<0) continue;

			int[] topics = new int[(int)Math.ceil(iter.value())];
			for (int i=0; i<iter.value();++i)
				topics[i] = rand.nextInt(numTopics);
			wordTopicMap.put(wordIdx, new WordTopic(topics,iter.value()));
		}
		if (wordTopicMap.size()<2)
			return null;
		
		Document doc = new Document(wordTopicMap, numTopics);

		double[] topicDistribution = new double[numTopics];
		Map<String, double[]> wordTopicDistribution = new HashMap<String, double[]>();
		
		for (int iter = 0; iter < totalIter; ++iter) {
	        sampleNewTopicsForDocument(doc);
	        if (iter >= burnInIter) {
	        	for (int i = 0; i < doc.topicDistribution.length; ++i)
	        		topicDistribution[i] += doc.topicDistribution[i];

	        	for (TIntObjectIterator<WordTopic> tIter=doc.wordCounts.iterator();tIter.hasNext();) {
	        		tIter.advance();
	        		
	        		double[] dist = wordTopicDistribution.get(idxWordMap.get(tIter.key()));
	        		if (dist==null)
	        			wordTopicDistribution.put(idxWordMap.get(tIter.key()), dist=new double[numTopics]);
	        		
	        		for (int i=0; i<tIter.value().dist.length; ++i)
	        			dist[tIter.value().dist[i]]+=(i+1>tIter.value().count)?tIter.value().count-i:1;
	        	}
	        }
		}
		wordTopicDistribution.put(null, topicDistribution);
		
		double factor = totalIter-burnInIter;
		if (factor==1)
			return wordTopicDistribution;
		
		for (Map.Entry<String, double[]> entry:wordTopicDistribution.entrySet())
			for (int i=0; i<entry.getValue().length; ++i)
				entry.getValue()[i]/=factor;
		
		return wordTopicDistribution;
	}

	private void readObject(java.io.ObjectInputStream in)
   	     throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		rand = new Random(System.currentTimeMillis());
	}
	
	public static void main(String[] args) throws Exception {  
		LDAModel model = LDAModel.readLDAModel(new File(args[0]));
		Map<String, int[]> wordTopicMap = model.getWordTopics();
		/*
		int topicCnt = 0;
		for (Map.Entry<String, int[]> entry:wordTopicMap.entrySet()) {
			System.out.println(entry.getKey()+" "+Arrays.toString(entry.getValue()));
			topicCnt+=entry.getValue().length;
		}
		System.out.println(wordTopicMap.size()+" "+topicCnt);
		*/
		
		try (BufferedReader reader = new BufferedReader(new FileReader(args[1]))) {
			String line;
			while ((line=reader.readLine())!=null) {
				String[] tokens = line.split("\\s+");
				if (tokens.length==0)
					continue;

				TObjectDoubleMap<String> wordMap = new TObjectDoubleHashMap<String>();
				for (int i=1; i<tokens.length; i+=2)
					wordMap.put(tokens[i-1], Integer.parseInt(tokens[i]));
					
				Map<String, double[]> distMap = model.infer(wordMap, 15, 10);
				
				for (Map.Entry<String, double[]> entry:distMap.entrySet()) {
					int[] globalTopics = wordTopicMap.get(entry.getKey());
					if (globalTopics!=null) {
						int[] topics = getTopTopics(new SparseCount(entry.getValue()), globalTopics.length, 2, 1);
						if (topics!=null)
							System.out.println(entry.getKey()+": "+Arrays.toString(topics)+Arrays.toString(globalTopics));
					}
				}
				
				double[] dist = distMap.get(null);
				for (int i=0; i<dist.length;++i)
					if (dist[i]!=0)
						System.out.print(" "+dist[i]+"("+i+")");
				System.out.print("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}