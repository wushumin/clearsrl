package edu.colorado.clear.common.util;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class PBFrame implements Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public class Roleset implements Serializable{
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		String id;
        private Map<String, String> roleMap;
        private Set<String> classes;
        private Set<String> aliases;


		public Roleset(String id) {
        	this(id, null, null);
        }
        
        public Roleset(String id, Set<String> classes, Set<String> aliases) {
            this.id = id;
            this.roleMap = new HashMap<String, String>();
            this.classes = classes;
            this.aliases = aliases;
        }
        
        public void addRole(String label, String auxLabel) {
        	roleMap.put(label.toLowerCase(), auxLabel==null||auxLabel.trim().isEmpty()?null:auxLabel.trim().toLowerCase());
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
        
        public void addClass(String clsId) {
			if (classes==null)
				classes = new HashSet<String>();
			classes.add(clsId);
		}
        
        public Set<String> getClasses() {
        	return classes==null?Collections.emptySet():Collections.unmodifiableSet(classes);
        }
        
		public void addAlias(String alias) {
			if (aliases==null)
				aliases = new HashSet<String>();
			aliases.add(alias);
		}
        
        public boolean hasAlias(String alias) {
        	return aliases==null?true:aliases.contains(alias);
        }
        
        public Set<String> getAliases() {
			return aliases==null?Collections.emptySet():Collections.unmodifiableSet(aliases);
		}
 
        @Override
		public String toString() {
            return id+' '+getAliases()+' '+roleMap+' '+getClasses();
        }


    }
    
    String id;
    SortedMap<String, Roleset> rolesets;

    public PBFrame(String id) {
        this.id = id;
        rolesets = new TreeMap<String, Roleset>();
    }
    
    public String getId() {
        return id;
    }
    
    public SortedMap<String, Roleset> getRolesets() {
        return Collections.unmodifiableSortedMap(rolesets);
    }
    
    public void addRoleset(Roleset roleset) {
        rolesets.put(roleset.id, roleset);
    }
    
    @Override
	public String toString() {
        StringBuilder builder = new StringBuilder();
        //char pos = this.pos.equals(LanguageUtil.POS.NOUN)?'n':(this.pos.equals(LanguageUtil.POS.VERB)?'v':'j');
        
        builder.append(id+'\n');
        for (Map.Entry<String, Roleset> entry:rolesets.entrySet())
            builder.append("  "+entry.getValue()+"\n");
        return builder.toString();
    }
    
}
