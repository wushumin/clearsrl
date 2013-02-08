package clearcommon.alg;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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

public class FeatureSet<T extends Enum<T>> implements Serializable {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	Set<EnumSet<T>>                                      features;
    EnumSet<T>                                           featuresFlat;
    Map<EnumSet<T>, TObjectIntHashMap<String>>           featureStrMap;
    
    boolean                                              dictionaryFinalized;
    int                                                  dimension;

    transient Map<EnumSet<T>, TObjectIntHashMap<String>> noArgFeatureStrMap;
    
    public FeatureSet(Set<EnumSet<T>> features) {
        this.features = features;
        initialize();
    }

    public FeatureSet(Class<T> cType, String[] featuresStr) {
        features = new TreeSet<EnumSet<T>>();
        
        for (String featureStr:featuresStr)
        {
            String[] fArray = featureStr.trim().split("-");
            List<T> fList = new ArrayList<T>(fArray.length);
            for (String fStr:fArray)
                fList.add(T.valueOf(cType,fStr));
            features.add(EnumSet.copyOf(fList));
        }
        
        initialize();
    }
    
    void initialize()
    {
        dictionaryFinalized = false;
        featureStrMap = new HashMap<EnumSet<T>, TObjectIntHashMap<String>>();
        noArgFeatureStrMap = new HashMap<EnumSet<T>, TObjectIntHashMap<String>>();
        
        List<T> featureList = new ArrayList<T>();
        for (EnumSet<T> feature:features)
        {
            featureStrMap.put(feature, new TObjectIntHashMap<String>());
            noArgFeatureStrMap.put(feature, new TObjectIntHashMap<String>());
            featureList.addAll(feature);
        }
        featuresFlat = EnumSet.copyOf(featureList);
    }
    
    public static <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> cType, String fString) throws IllegalArgumentException
    {
        String[] fArray = fString.trim().split("-");
        List<T> fList = new ArrayList<T>(fArray.length);
        for (String fStr:fArray)
            fList.add(T.valueOf(cType,fStr));
        return EnumSet.copyOf(fList);
    }
    
    public static <T extends Enum<T>> Set<EnumSet<T>> getBigramSet(Set<EnumSet<T>> input)
    {
    	return getBigramSet(input, false);
    }
    
    public static <T extends Enum<T>> Set<EnumSet<T>> getBigramSet(Set<EnumSet<T>> input, boolean allPairing)
    {
    	Set<EnumSet<T>> output = new HashSet<EnumSet<T>>(input);
    	if (allPairing) {
    		List<EnumSet<T>> enumList = new ArrayList<EnumSet<T>>(input);
    		for (int i=0; i<enumList.size()-1; ++i)
	        	for (int j=i+1; j<enumList.size(); ++j)
	        	{
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
    
    public Map<EnumSet<T>,List<String>> convertFlatSample(EnumMap<T,List<String>> sampleFlat)
    { 
        //System.out.println(sampleFlat);
        Map<EnumSet<T>,List<String>> sample = new HashMap<EnumSet<T>,List<String>>();
        
        for (EnumSet<T> feature:features)
        {   
            Iterator<T> iter = feature.iterator();
            List<String> sList = sampleFlat.get(iter.next());
            for (;iter.hasNext() && sList!=null && !sList.isEmpty();)
                sList = permute(sList, sampleFlat.get(iter.next()));
            
            if (sList!=null && !sList.isEmpty())
            {
                //if (feature.size()>1) System.out.println(toString(feature)+": "+sList);
                sample.put(feature, sList);
            }
        }
        
        return sample;   
    }

    static List<String> permute(List<String> lhs, List<String> rhs)
    {   
        if (lhs==null || rhs==null) return new ArrayList<String>(0);

        ArrayList<String> ret = new ArrayList<String>(lhs.size()*rhs.size());
        for (String a2:rhs)
            for (String a1:lhs)
                ret.add(a1+" "+a2);
        return ret;
    }
    
    public int[] addToFeatureVector(Map<EnumSet<T>,List<String>> featureValueMap, int[] vec)
    {
    	TIntHashSet featureSet = vec==null?new TIntHashSet():new TIntHashSet(vec);
    	
    	for(Map.Entry<EnumSet<T>,List<String>> entry:featureValueMap.entrySet())
        {
            TObjectIntHashMap<String> fMap = featureStrMap.get(entry.getKey());
            for (String fVal:entry.getValue())
            {
                int mapIdx = fMap.get(fVal);
                if (mapIdx>0) featureSet.add(mapIdx-1);
            }
        }
        int [] features = featureSet.toArray();
        Arrays.sort(features);
        
        return features;
    }
    
    public int[] getFeatureVector(Map<EnumSet<T>,List<String>> featureValueMap)
    {
        return addToFeatureVector(featureValueMap, null);
    }
    
    public void addToDictionary(EnumSet<T> type, List<String> values, boolean isNoArg)
    {
        if (dictionaryFinalized) return;

        TObjectIntHashMap<String> fMap = isNoArg?noArgFeatureStrMap.get(type):featureStrMap.get(type);
        for (String fVal:values)
            fMap.put(fVal, fMap.get(fVal)+1);
        
    }
    
    public void rebuildMap(long cutoff, long noArgCutoff) {

        for (EnumSet<T> feature:features)
        {
            //StringBuilder builder = new StringBuilder(toString(feature)+": "+featureStrMap.get(feature).size()+"/"+noArgFeatureStrMap.get(feature).size());
            trimMap(featureStrMap.get(feature),cutoff);
            trimMap(noArgFeatureStrMap.get(feature),noArgCutoff);
            //builder.append(" "+featureStrMap.get(feature).size()+"/"+noArgFeatureStrMap.get(feature).size());
            featureStrMap.get(feature).putAll(noArgFeatureStrMap.get(feature));
            //builder.append(" "+featureStrMap.get(feature).size());
            //builder.append(featureStrMap.get(feature).size()<=250?" "+Arrays.asList(featureStrMap.get(feature).keys()):"");
            //logger.info(builder.toString());
        }
        
        buildMapIndices();
        
        dictionaryFinalized = true;
    }
    
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
    }
    
    public static void trimMap(TObjectIntHashMap<String> featureMap, long threshold)
    {
        for (TObjectIntIterator<String> iter = featureMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            if (iter.value()<threshold)
                iter.remove();
        }
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (EnumSet<T> feature:features)
        {
            builder.append(toString(feature));
            builder.append(' ');
        }
        return builder.toString();
    }
    
    public String toString(EnumSet<T> feature) {
        
        Iterator<T> iter = feature.iterator();
        
        StringBuilder builder = new StringBuilder(iter.next().toString());
        for (;iter.hasNext();)
            builder.append("-"+iter.next().toString());
        return builder.toString();
    }

    public Set<EnumSet<T>> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    public EnumSet<T> getFeaturesFlat() {
        return featuresFlat;
    }
    
    public void addFeatures(EnumSet<T> key, TObjectIntHashMap<String> value, boolean isNoArg)
    {
    	if (isNoArg)
    		noArgFeatureStrMap.put(key, value);
    	else
    		featureStrMap.put(key, value);
    }
    
    public Map<EnumSet<T>, TObjectIntHashMap<String>> getFeatureStrMap() {
        return Collections.unmodifiableMap(featureStrMap);
    }

    public int getDimension() {
        return dimension;
    }

}
