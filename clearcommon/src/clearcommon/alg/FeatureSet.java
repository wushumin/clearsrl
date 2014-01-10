package clearcommon.alg;

import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.TObjectFloatMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectFloatHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Encapsulate binary feature representation. Basic feature types are 
 * enumerations. Each specific feature can be a set of enumerated values (to 
 * support n-gram of different basic features). The class keeps track of 
 * number of feature instances in the training examples and prunes away rare 
 * features. It also supports converting feature values to linear indices 
 * for training/decoding by ML classifiers.
 * 
 * ex: we have basic feature types like LEMMA, POS, etc, and specific feature
 *     types like LEMMA, LEMMA-POS, etc
 * 
 * @author Shumin Wu
 *
 * @param <T>
 */
public class FeatureSet<T extends Enum<T>> implements Serializable {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    Set<EnumSet<T>>                                    features;
    EnumSet<T>                                         featuresFlat;
    Map<EnumSet<T>, TObjectIntMap<String>>             featureStrMap;
    
    boolean                                            dictionaryFinalized;
    int                                                dimension;

    transient Map<EnumSet<T>, TObjectFloatMap<String>> featureCountMap;
    
    public FeatureSet(Set<EnumSet<T>> features) {
        this.features = features;
    }

    public FeatureSet(Class<T> cType, String[] featuresStr) {
        features = new TreeSet<EnumSet<T>>();
        
        for (String featureStr:featuresStr) {
            String[] fArray = featureStr.trim().split("-");
            List<T> fList = new ArrayList<T>(fArray.length);
            for (String fStr:fArray)
                fList.add(T.valueOf(cType,fStr));
            features.add(EnumSet.copyOf(fList));
        }
    }
    
    public void initialize() {
        dictionaryFinalized = false;
        featureStrMap = new HashMap<EnumSet<T>, TObjectIntMap<String>>();
        featureCountMap = new HashMap<EnumSet<T>, TObjectFloatMap<String>>();
        
        List<T> featureList = new ArrayList<T>();
        for (EnumSet<T> feature:features) {
            featureStrMap.put(feature, new TObjectIntHashMap<String>());
            featureCountMap.put(feature, new TObjectFloatHashMap<String>());
            featureList.addAll(feature);
        }
        featuresFlat = EnumSet.copyOf(featureList);
    }
    
    /**
     * Converts String representation of features to EnumSet. N-gram 
     * features are delimited with '-', unigram feature are the String 
     * values of the enum type.
     * @param cType
     * @param fString
     * @return
     * @throws IllegalArgumentException
     */
    public static <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> cType, String fString) throws IllegalArgumentException {
        String[] fArray = fString.trim().split("-");
        List<T> fList = new ArrayList<T>(fArray.length);
        for (String fStr:fArray)
            fList.add(T.valueOf(cType,fStr));
        return EnumSet.copyOf(fList);
    }
    
    public static <T extends Enum<T>> Set<EnumSet<T>> getBigramSet(Set<EnumSet<T>> input) {
        return getBigramSet(input, false);
    }
    
    /**
     * Generate bigrams of features
     * @param input
     * @param allPairing whether to generate bigrams of any n-gram features in the input
     * @return
     */
    public static <T extends Enum<T>> Set<EnumSet<T>> getBigramSet(Set<EnumSet<T>> input, boolean allPairing) {
        Set<EnumSet<T>> output = new HashSet<EnumSet<T>>(input);
        if (allPairing) {
            List<EnumSet<T>> enumList = new ArrayList<EnumSet<T>>(input);
            for (int i=0; i<enumList.size()-1; ++i)
                for (int j=i+1; j<enumList.size(); ++j) {
                    List<T> featureList = new ArrayList<T>(enumList.get(i));
                    featureList.addAll(enumList.get(j));
                    output.add(EnumSet.copyOf(featureList));
                }
        } else {
            List<T> featureList = new ArrayList<T>();
            for (EnumSet<T> feature:input)
                featureList.addAll(feature);
            
            for (int i=0; i<featureList.size()-1; ++i)
                for (int j=i+1; j<featureList.size(); ++j)
                    output.add(EnumSet.of(featureList.get(i), featureList.get(j)));
        }
        
        return output;
    }
    
    /**
     * convert extracted unigram feature values to n-gram feature values 
     * @param sampleFlat
     * @return
     */
    public Map<EnumSet<T>,Collection<String>> convertFlatSample(EnumMap<T,Collection<String>> sampleFlat)  { 
        //System.out.println(sampleFlat);
        Map<EnumSet<T>,Collection<String>> sample = new HashMap<EnumSet<T>,Collection<String>>();
        
        for (EnumSet<T> feature:features)  {   
            Iterator<T> iter = feature.iterator();
            
            Collection<String> sList = sampleFlat.get(iter.next());
            for (;iter.hasNext();)
                sList = permute(sList, sampleFlat.get(iter.next()));

            if (sList!=null) {
            	/*
	            for (Iterator<String> sIter=sList.iterator(); sIter.hasNext();)
	            	if (sIter.next().matches("null( null)*"))
	            		sIter.remove();
            	 */
	            if (!sList.isEmpty()) {
	                //if (feature.size()>1) System.out.println(toString(feature)+": "+sList);
	                sample.put(feature, sList);
	            }
            }
        }
        
        return sample;   
    }

    static Collection<String> permute(Collection<String> lhs, Collection<String> rhs) {   
        if (lhs==null || rhs==null) return null;
    	//if (lhs==null) lhs = Arrays.asList("null");
    	//if (rhs==null) rhs = Arrays.asList("null");
    		
    	List<String> ret = new ArrayList<String>(lhs.size()*rhs.size());
        for (String a2:rhs)
            for (String a1:lhs)
                ret.add(a1+" "+a2);
        return ret;
    }
    
    /**
     * add some feature values to an existing feature vector
     * @param featureValueMap
     * @param vec
     * @return
     */
    public int[] addToFeatureVector(Map<EnumSet<T>,Collection<String>> featureValueMap, int[] vec) {
        TIntSet featureSet = new TIntHashSet();
        if (vec!=null)
            for (int val:vec)
                featureSet.add(val);
        
        for(Map.Entry<EnumSet<T>,Collection<String>> entry:featureValueMap.entrySet()) {
            TObjectIntMap<String> fMap = featureStrMap.get(entry.getKey());
            // unused feature that was extracted
            if (fMap==null) continue;
            
            for (String fVal:entry.getValue()) {
                int mapIdx = fMap.get(fVal);
                if (mapIdx>0) featureSet.add(mapIdx-1);
            }
        }
        int [] features = featureSet.toArray();
        Arrays.sort(features);
        
        return features;
    }
    
    /**
     * convert extracted feature values to indexed array
     * @param featureValueMap
     * @return
     */
    public int[] getFeatureVector(Map<EnumSet<T>,Collection<String>> featureValueMap) {
        return addToFeatureVector(featureValueMap, null);
    }
    
    public int[] getFeatureVector(EnumMap<T,Collection<String>> sampleFlat) {
        return getFeatureVector(convertFlatSample(sampleFlat));
    }
    
    public void addToDictionary(EnumSet<T> type, Collection<String> values) {
        addToDictionary(type, values, 1f);
    }
    
    /**
     * Add feature values to dictionary
     * @param type
     * @param values
     * @param weight changes the occurrence count for this feature value
     */
    public void addToDictionary(EnumSet<T> type, Collection<String> values, float weight) {
        if (dictionaryFinalized) return;

        TObjectFloatMap<String> fMap = featureCountMap.get(type);
        if (fMap==null) return;
        for (String fVal:values)
            fMap.adjustOrPutValue(fVal, weight, weight);
            //fMap.put(fVal, fMap.get(fVal)+weight); 
    }
    
    public void addToDictionary(EnumMap<T,Collection<String>> sampleFlat) {
        addToDictionary(sampleFlat, 1f);
    }
    
    public void addToDictionary(EnumMap<T,Collection<String>> sampleFlat, float weight) {
        for (Map.Entry<EnumSet<T>,Collection<String>> entry:convertFlatSample(sampleFlat).entrySet())
            addToDictionary(entry.getKey(),entry.getValue(),weight);
    }
    
    /**
     * Prunes away rare features (occurs less than cutoff) and indexes all remain feature values
     * @param cutoff
     */
    public void rebuildMap(float cutoff) {
        rebuildMapOptimized(cutoff);
        dictionaryFinalized = true;
        featureCountMap.clear();
    }
    
    void rebuildMapFast(float cutoff) {
        int dimension=0;
        for (EnumSet<T> feature:features) {
            TObjectIntMap<String> indexMap = new TObjectIntHashMap<String>();
            featureStrMap.put(feature, indexMap);
            
            for (TObjectFloatIterator<String> iter = featureCountMap.get(feature).iterator();iter.hasNext();) {
                iter.advance();
                if (iter.value()>=cutoff)
                    indexMap.put(iter.key(), ++dimension);
            }
        }
    }
    
    class FeatureValue implements Comparable<FeatureValue>{
        EnumSet<T> type;
        String value;
        float frequency;
        
        public FeatureValue(EnumSet<T> type, String value, float frequency) {
            this.type = type;
            this.value = value;
            this.frequency = frequency;
        }
        
        @Override
        public int compareTo(FeatureValue rhs) {
            if (frequency!=rhs.frequency)
                return frequency>rhs.frequency?-1:1;
            return 0;
        }
        
    }
    
    // Sort the feature by frequency so that the generated spare vector will
    // be clustered by frequency. This may speed up lookup during training, 
    // but probably won't speed up prediction as it's bounded by feature 
    // extraction and dictionary lookup
    void rebuildMapOptimized(float cutoff) {
        
        List<FeatureValue> featVals = new ArrayList<FeatureValue>();
        for (EnumSet<T> feature:features) {
            TObjectIntMap<String> indexMap = new TObjectIntHashMap<String>();
            featureStrMap.put(feature, indexMap);
            
            for (TObjectFloatIterator<String> iter = featureCountMap.get(feature).iterator();iter.hasNext();) {
                iter.advance();
                if (iter.value()>=cutoff)
                    featVals.add(new FeatureValue(feature, iter.key(), iter.value()));
            }
        }
        Collections.sort(featVals);
        
        int dimension=0;
        for (FeatureValue featVal:featVals)
            featureStrMap.get(featVal.type).put(featVal.value, ++dimension);
    }
    
    public static int buildMapIndex(TObjectIntMap<String> mapObj, int startIdx) {
        String[] keys = mapObj.keys(new String[mapObj.size()]);
        mapObj.clear();
        for (String key:keys)
            mapObj.put(key, ++startIdx);
        return startIdx;
    }
    
    /*
    void buildMapIndices()
    {   
        //featureStringMap.put(EnumSet.of(Feature.ARGLIST), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        //featureStringMap.put(EnumSet.of(Feature.ARGLISTPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        //featureStringMap.put(EnumSet.of(Feature.ARGTYPE), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        //featureStringMap.put(EnumSet.of(Feature.ARGTYPEPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        
        dimension=0;
        for (Map.Entry<EnumSet<T>, TObjectIntHashMap<String>> entry: featureStrMap.entrySet())
        {
            String[] keys = entry.getValue().keys(new String[entry.getValue().size()]);
            entry.getValue().clear();
            for (String key:keys)
                entry.getValue().put(key, ++dimension);   
        }
    }*/
    
    public static void trimMap(TObjectIntMap<String> featureMap, int threshold) {
        for (Object key:featureMap.keys())
            if (featureMap.get(key)<threshold)
                featureMap.remove(key);
    }
    
    public String toString() {
        return toString(features);
    }
    
    public static <T extends Enum<T>> String toString(Set<EnumSet<T>> features) {
        StringBuilder builder = new StringBuilder();
        for (EnumSet<T> feature:features) {
            builder.append(toString(feature));
            builder.append(' ');
        }
        return builder.toString();
    }
    
    public static <T extends Enum<T>> String toString(EnumSet<T> feature) {
        StringBuilder builder = new StringBuilder();
        for (T t:feature)
            builder.append("-"+t.toString());
        return builder.toString();
    }

    public Set<EnumSet<T>> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    public EnumSet<T> getFeaturesFlat() {
        return featuresFlat;
    }
    
    /*
    public void addFeatures(EnumSet<T> key, TObjectIntHashMap<String> value, boolean isNoArg)
    {
        if (isNoArg)
            noArgFeatureStrMap.put(key, value);
        else
            featureStrMap.put(key, value);
    }*/
    
    public Map<EnumSet<T>, TObjectIntMap<String>> getFeatureStrMap() {
        return Collections.unmodifiableMap(featureStrMap);
    }

    public int getDimension() {
        return dimension;
    }

}
