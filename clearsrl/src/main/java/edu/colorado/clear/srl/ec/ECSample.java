package edu.colorado.clear.srl.ec;

import java.util.Collection;
import java.util.EnumMap;
import edu.colorado.clear.srl.ec.ECCommon.Feature;

public class ECSample {
    public ECSample(EnumMap<Feature,Collection<String>> features, String label) {
        this.features = features;
        this.label = label;
        
        if (this.label==null) this.label = ECCommon.NOT_EC;
    }
    EnumMap<Feature,Collection<String>> features;
    String label;
}