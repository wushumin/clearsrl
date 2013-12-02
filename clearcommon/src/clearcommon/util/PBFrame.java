package clearcommon.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import clearcommon.treebank.TBNode;

public class PBFrame {
	public class Roleset {
		String id;
		Set<String> roles;
		
		public Roleset(String id) {
			this.id = id;
			this.roles = new HashSet<String>();
		}
		
		public String getId() {
			return id;
		}

		public Set<String> getRoles() {
			return Collections.unmodifiableSet(roles);
		}
		
		public String toString() {
			return id+' '+roles;
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
	
	public static String makeKey(TBNode node, LanguageUtil langUtil) {
		String lemma = langUtil.findStems(node).get(0);
		String pos = langUtil.isVerb(node.getPOS())?"-v":(langUtil.isNoun(node.getPOS())?"-n":(langUtil.isAdjective(node.getPOS())?"-j":""));
		return lemma+pos;
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
