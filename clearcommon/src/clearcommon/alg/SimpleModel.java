package clearcommon.alg;

import java.io.Serializable;
import java.util.ArrayList;
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
		
	public void addTrainingSample(EnumMap<T,List<String>> sampleFlat, String label, boolean buildDictionary) {
		addTrainingSample(sampleFlat, label, 1.0f, buildDictionary);
	}
	
	public void addTrainingSample(EnumMap<T,List<String>> sampleFlat, String label, float weight, boolean buildDictionary) {
		if (buildDictionary) {
			featureSet.addToDictionary(sampleFlat, weight);
			labelStringMap.adjustOrPutValue(label, 1, 1);
		} else {
			trainingFeatures.add(featureSet.getFeatureVector(sampleFlat));
			trainingLabels.add(labelStringMap.get(label));
		}
	}
	
	public void finalizeDictionary(int featureCutOff, int labelCutOff) {
		featureSet.rebuildMap(featureCutOff);
		
		FeatureSet.trimMap(labelStringMap,labelCutOff);
		FeatureSet.buildMapIndex(labelStringMap, 0);
		
		labelIndexMap = new TIntObjectHashMap<String>();
		for (TObjectIntIterator<String> tIter=labelStringMap.iterator(); tIter.hasNext();) {
			tIter.advance();
			labelIndexMap.put(tIter.value(), tIter.key());
		}
		trainingFeatures = new ArrayList<int[]>();
		trainingLabels = new TIntArrayList();
	}
	
	public void train(Properties props) {
		classifier = new LinearClassifier();
        classifier.initialize(labelStringMap, props);
        classifier.train(trainingFeatures.toArray(new int[trainingFeatures.size()][]), trainingLabels.toArray());
        
        double score = 0;
    	for (int i=0; i<trainingFeatures.size(); ++i)
            score += (classifier.predict(trainingFeatures.get(i))==trainingLabels.get(i))?1:0;
        logger.info(String.format("training accuracy: %f\n", score/trainingLabels.size()));
	}
	
	public String predictLabel(EnumMap<T,List<String>> sample) {
		return labelIndexMap.get(classifier.predict(featureSet.getFeatureVector(sample)));
	}
	
	public int predictValues(EnumMap<T,List<String>> sample, double[] vals) {
		return classifier.predictValues(featureSet.getFeatureVector(sample), vals);
	}

	public TObjectIntMap<String> getLabelStringMap() {
		return labelStringMap;
	}
	
	public TIntObjectMap<String> getLabelIndexMap() {
		return labelIndexMap;
	}
	
	public Set<String> getLabels() {
		return labelStringMap.keySet();
	}
}
