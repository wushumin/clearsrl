package clearcommon.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class PBFrame {
    public class Roleset {
        String id;
        private Map<String, String> roleMap;
        private Set<String> classes;
        
        public Roleset(String id) {
        	this(id, null);
        }
        
        public Roleset(String id, Set<String> classes) {
            this.id = id;
            this.roleMap = new HashMap<String, String>();
            this.classes = classes;
        }
        
        public void addRole(String label, String auxLabel) {
        	roleMap.put(label.toLowerCase(), auxLabel==null?null:auxLabel.toLowerCase());
        }
        
        public boolean hasRole(String label) {
        	return roleMap.containsKey(label.toLowerCase());
        }
        
        public String getId() {
            return id;
        }

        public Set<String> getRoles() {
            return Collections.unmodifiableSet(roleMap.keySet());
        }
        
        public Map<String, String> getRoleMap() {
            return Collections.unmodifiableMap(roleMap);
        }
        
        public String getAuxLabel(String label) {
        	return roleMap.get(label.toLowerCase());
        }
        
        public Set<String> getClasses() {
        	return Collections.unmodifiableSet(classes);
        }
 
        public String toString() {
            return id+' '+roleMap;
        }
    }
    
    String predicate;
    LanguageUtil.POS pos;
    SortedMap<String, Roleset> rolesets;

    public PBFrame(String predicate, LanguageUtil.POS pos) {
        this.predicate = predicate;
        this.pos = pos;
        rolesets = new TreeMap<String, Roleset>();
    }
    
    public String getPredicate() {
        return predicate;
    }
    
    public LanguageUtil.POS getPos() {
        return pos;
    }
    
    public SortedMap<String, Roleset> getRolesets() {
        return Collections.unmodifiableSortedMap(rolesets);
    }
    
    public void addRoleset(Roleset roleset) {
        rolesets.put(roleset.id, roleset);
    }
    
    public String toString() {
        StringBuilder builder = new StringBuilder();
        char pos = this.pos.equals(LanguageUtil.POS.NOUN)?'n':(this.pos.equals(LanguageUtil.POS.VERB)?'v':'j');
        
        builder.append(predicate+'-'+pos+'\n');
        for (Map.Entry<String, Roleset> entry:rolesets.entrySet())
            builder.append("  "+entry.getValue()+"\n");
        return builder.toString();
    }
    
}
