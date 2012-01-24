package clearcommon.alg;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class FeatureSet<T extends Enum<T>> {
    
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
    
    public int[] getFeatureVector(Map<EnumSet<T>,List<String>> sample)
    {
        TIntHashSet featureSet = new TIntHashSet();
    
        for(Map.Entry<EnumSet<T>,List<String>> entry:sample.entrySet())
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
    
}
