package clearsrl.ec;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

import clearsrl.ec.ECCommon.Feature;

public class ECSample {
    public ECSample(EnumMap<Feature,Collection<String>> features, String label) {
        this.features = features;
        this.label = label;
        
        if (this.label==null) this.label = ECCommon.NOT_EC;
    }
    EnumMap<Feature,Collection<String>> features;
    String label;
}