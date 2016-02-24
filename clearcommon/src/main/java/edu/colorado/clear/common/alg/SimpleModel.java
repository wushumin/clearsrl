package edu.colorado.clear.common.alg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

public class SimpleModel<T extends Enum<T>> implements Serializable {   
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    transient Logger      logger;
    FeatureSet<T>         featureSet;
    TObjectIntMap<String> labelStringMap;
    TIntObjectMap<String> labelIndexMap;
    Classifier            classifier;
    transient List<int[]> trainingFeatures;
    transient TIntList    trainingLabels;
    transient TIntList    trainingSeeds;
    
    public SimpleModel(Set<EnumSet<T>> features) {
        logger = Logger.getLogger("clearcommon");
        featureSet = new FeatureSet<T>(features);
    }
    
    public void initialize() {
        featureSet.initialize();
        labelStringMap = new TObjectIntHashMap<String>();
    }
    
    public Set<EnumSet<T>> getFeatures() {
        return featureSet.getFeatures();
    }
    
    public EnumSet<T> getFeaturesFlat() {
        return featureSet.featuresFlat;
    }
    
    public int addTrainingSample(EnumMap<T,Collection<String>> sampleFlat, String label, boolean buildDictionary) {
        return addTrainingSample(sampleFlat, label, 1.0f, buildDictionary);
    }
        
    public int addTrainingSample(EnumMap<T,Collection<String>> sampleFlat, int seed, String label, boolean buildDictionary) {
        return addTrainingSample(sampleFlat, seed, label, 1.0f, buildDictionary);
    }
    
    public int addTrainingSample(EnumMap<T,Collection<String>> sampleFlat, String label, float weight, boolean buildDictionary) {
    	return addTrainingSample(sampleFlat, -1, label, 1.0f, buildDictionary);
    }
    
    public int addTrainingSample(EnumMap<T,Collection<String>> sampleFlat, int seed, String label, float weight, boolean buildDictionary) {
        if (buildDictionary) {
            featureSet.addToDictionary(sampleFlat, weight);
            return labelStringMap.adjustOrPutValue(label, 1, 1);
        } else {
        	if (labelStringMap.containsKey(label)) {
	            trainingFeatures.add(featureSet.getFeatureVector(sampleFlat));
	            trainingLabels.add(labelStringMap.get(label));
	            if (seed>=0)
	            	trainingSeeds.add(seed);
        	}
            return trainingLabels.size();
        }
    }
    
    public void finalizeDictionary(int featureCutOff, int labelCutOff) {
        featureSet.rebuildMap(featureCutOff);
        
        FeatureSet.trimMap(labelStringMap,labelCutOff);
        FeatureSet.buildMapIndex(labelStringMap, 0, true);
        
        labelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> tIter=labelStringMap.iterator(); tIter.hasNext();) {
            tIter.advance();
            labelIndexMap.put(tIter.value(), tIter.key());
        }
        trainingFeatures = new ArrayList<int[]>();
        trainingLabels = new TIntArrayList();
        trainingSeeds = new TIntArrayList();
    }
    
    public String[] train(Properties props) {
    	return train(props, false);
    }
    
    public String[] train(Properties props, boolean crossValidate) {
        classifier = new LinearClassifier();
        classifier.initialize(labelStringMap, props);
        
        int[][] X = trainingFeatures.toArray(new int[trainingFeatures.size()][]);
        int[] y = trainingLabels.toArray();
        
        int[] yV = null;
        if (crossValidate) {
        	int folds = Integer.parseInt(props.getProperty("crossvalidation.folds","5"));
            int threads = Integer.parseInt(props.getProperty("crossvalidation.threads","1"));
            
        	CrossValidator validator = new CrossValidator(classifier, threads);
            yV =  validator.validate(folds, X, y, null, null, true);
        } else {
	        classifier.train(X, y);
	        yV = new int[trainingFeatures.size()];
	        for (int i=0; i<trainingFeatures.size(); ++i)
	        	yV[i] = classifier.predict(trainingFeatures.get(i));
        }
        double score = 0;
        for (int i=0; i<trainingFeatures.size(); ++i)
        	score += (y[i]==yV[i])?1:0;
        
        logger.info(String.format("%s accuracy: %f\n", crossValidate?"validation":"training", score/trainingLabels.size()));
        
        String[] strLabels = new String[trainingFeatures.size()];
        for (int i=0; i<yV.length; ++i)
        	strLabels[i] = labelIndexMap.get(yV[i]);
        
        return strLabels;
    }
    
    public String predictLabel(EnumMap<T,Collection<String>> sample) {
        return labelIndexMap.get(classifier.predict(featureSet.getFeatureVector(sample)));
    }
    
    public int predictValues(EnumMap<T,Collection<String>> sample, double[] vals) {
        return classifier.predictValues(featureSet.getFeatureVector(sample), vals);
    }

    public TObjectIntMap<String> getLabelStringMap() {
        return labelStringMap;
    }
    
    public TIntObjectMap<String> getLabelIndexMap() {
        return labelIndexMap;
    }
    
    public Set<String> getLabelSet() {
        return labelStringMap.keySet();
    }   
}
