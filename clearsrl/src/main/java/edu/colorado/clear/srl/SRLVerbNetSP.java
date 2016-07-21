package edu.colorado.clear.srl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TObjectFloatMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectFloatHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.LanguageUtil.POS;
import edu.colorado.clear.common.util.PBFrame.Roleset;

public class SRLVerbNetSP extends SRLSelPref implements Serializable {

	/**
	 * 
	 */
    private static final long serialVersionUID = 1L;

    enum Type {
    	ROLE,
    	VN,
    	LEMMA,
    	ALL
    };
    
    private static Logger logger = Logger.getLogger("clearsrl");

    protected TObjectIntMap<String> roleIdxMap;
    protected Map<String, IndexedMap> vnRoleMap;
    protected Map<String, float[]> roleSP = null;
    protected Map<String, Map<String, float[]>> vnclsSP = null;
    protected Map<String, Map<String, float[]>> lemmaSP = null;

    protected Map<String, Map<String, TObjectFloatMap<String>>> predCntMap;
    protected Map<String, Map<String, TObjectFloatMap<String>>> ppCntMap;
    protected Map<String, TObjectFloatMap<String>> roleCntMap;
    
    protected boolean useRoleBackoff = false;
    
	public void setUseRoleBackoff(boolean useRoleBackoff) {
		this.useRoleBackoff = useRoleBackoff;
	}

	static class IndexedMap implements Serializable{

		/**
		 * 
		 */
        private static final long serialVersionUID = 1L;
	
        int[] indexArray;
        int[] reverseIndexArray;
        int size;
        
        public IndexedMap(int capacity) {
        	indexArray = new int[capacity];
        	Arrays.fill(indexArray, -1);
        	reverseIndexArray = null;
        }
        
        public void put(int idx, int val) {
        	if (indexArray[idx]<0)
        		++size;
        	if (val<0)
        		--size;
        		
        	indexArray[idx] = val;
        }
        
        public int get(int idx) {
        	return indexArray[idx];
        }
        
        public int getR(int rIdx) {
        	if (reverseIndexArray==null) {
        		int max = -1;
        		for (int val:indexArray)
        			if (val>max)
        				max = val;
        		reverseIndexArray = new int[max+1];
        		Arrays.fill(reverseIndexArray, -1);
        		for (int i=0; i<indexArray.length; ++i)
        			if (indexArray[i]>=0)
        				reverseIndexArray[indexArray[i]]=i;
        	}
        	return reverseIndexArray[rIdx];
        }
        
        public int size() {
        	return size;
        }
        
        @Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append('[');
			
			boolean first = true;
			for (int i=0; i<indexArray.length;++i) {
				if (indexArray[i]<0) continue;
				if (!first)
					builder.append(", ");
				builder.append(""+indexArray[i]+':'+i);
				first = false;
			}
			builder.append(']');
			return builder.toString();
		}
        
	}
	
	static class SparseVec implements Serializable{
		/**
		 * 
		 */
        private static final long serialVersionUID = 1L;
        
        TIntArrayList indices;
        TFloatArrayList values;
		
		public SparseVec() {
			this.indices = new TIntArrayList();
			this.values = new TFloatArrayList();
		}
		
		public SparseVec(int capacity) {
			this.indices = new TIntArrayList(capacity);
			this.values = new TFloatArrayList(capacity);
		}
		
		public void add(int index, float value) {
			indices.add(index);
			values.add(value);
		}
		
		public float[] getDense(int dimension) {
			float[] dense = new float[dimension];
			for (int i=0; i<indices.size(); ++i)
				dense[indices.get(i)] = values.get(i);
			return dense;
		}
		
		public void clear() {
			indices.clear();
			values.clear();
		}
		
		public void clear(int capacity) {
			indices.clear(capacity);
			values.clear(capacity);
		}
		
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append('[');
			for (int i=0; i<indices.size();++i) {
				if (i!=0)
					builder.append(", ");
				builder.append(""+indices.get(i)+':'+values.get(i));
			}
			builder.append(']');
			return builder.toString();
		}

		public boolean isEmpty() {
	        return indices.isEmpty();
        }

	}

	protected SRLVerbNetSP() {
		super();
	}

	@Override
	public void initialize(Properties props) throws IOException {
		String topDir = props.getProperty("indir");
		Type type = Type.valueOf(props.getProperty("type", Type.ROLE.toString()));
		
    	readSP(new File(topDir), 
    			type.equals(Type.VN)||type.equals(Type.ALL), 
    			type.equals(Type.LEMMA)||type.equals(Type.ALL));
    	
    	useRoleBackoff = !props.getProperty("useRoleBackoff", "true").equals("false");
    }
	
	static boolean readRow(BufferedReader reader, SparseVec rowVec, int rowDim) throws IOException {
		String line=reader.readLine();
		if (line==null)
			return false;
		
		rowVec.clear(rowDim);

		StringTokenizer tok = new StringTokenizer(line.trim(), " \t\n\r\f"); 
		int cnt = -1;
		while (tok.hasMoreTokens()) {
			String token = tok.nextToken();
			++cnt;
			if (token.equals("0.0"))
				continue;
			rowVec.add(cnt, Float.parseFloat(token));
		}
		return true;
	}

	static SparseVec[] readMatrix(File dir, int rowDim, int colDim, boolean transpose) throws IOException {
		SparseVec[] retMatrix = new SparseVec[transpose?colDim:rowDim];
	
		BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(dir, "vspace.txt.gz")))), 0x10000);
		
		String line = null;
		int rCnt=0;
		while ((line=reader.readLine())!=null) {
			int cCnt = -1;
/*
			Scanner scanner = new Scanner(line);
			float val;
			while (scanner.hasNext()) {
				++cCnt;
				val = scanner.nextFloat();
				if (val==0f) continue;
				
				if (retMatrix[transpose?cCnt:rCnt]==null)
					retMatrix[transpose?cCnt:rCnt] = new SparseVec();
				retMatrix[transpose?cCnt:rCnt].add(transpose?rCnt:cCnt, val);
			}
			scanner.close();*/
			
			StringTokenizer tok = new StringTokenizer(line.trim(), " \t\n\r\f"); 
			while (tok.hasMoreTokens()) {
				String token = tok.nextToken();
				++cCnt;
				if (token.equals("0.0"))
					continue;
				
				float val = Float.parseFloat(token);
				if (retMatrix[transpose?cCnt:rCnt]==null)
					retMatrix[transpose?cCnt:rCnt] = new SparseVec();
				retMatrix[transpose?cCnt:rCnt].add(transpose?rCnt:cCnt, val);
			}
			++rCnt;
		}
		reader.close();
		
		return retMatrix;
	}
	
	static String[] readLines(File file) throws IOException {
		List<String> lineList = new ArrayList<String>();
		
		 BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
         String line;
         while ((line=reader.readLine())!=null)
        	 lineList.add(line.trim());
         reader.close();
         return lineList.toArray(new String[lineList.size()]);
	}
	
	static TObjectIntMap<String> getAllRoles(File topDir) throws IOException {
		
		String[] lines = readLines(new File(topDir, "role_matrix/row_no.txt.gz"));
		
		TObjectIntMap<String> roleIdxMap = new TObjectIntHashMap<String>(lines.length, 0.5f, -1);
		
		for (String line:lines)
			roleIdxMap.put(line.trim().intern(), roleIdxMap.size());
		
		/*
		File[] files = new File(topDir, "VN_class_matrices").listFiles();
		Arrays.sort(files);
		
		TObjectIntMap<String> roleIdxMap = new TObjectIntHashMap<String>(60, 0.5f, -1);
		
		for (File roleDir:files) {
			if (!Pattern.matches("[A-Z].+", roleDir.getName()))
				continue;
			roleIdxMap.put(roleDir.getName().intern(), roleIdxMap.size());
		}*/
		
		return roleIdxMap;
	}
	
	static Map<String, IndexedMap> getVNRoles(File topDir, TObjectIntMap<String> roleIdxMap) throws IOException {
		
		Map<String, IndexedMap> vnRoleMap = new HashMap<String, IndexedMap>();
		
		File[] files = new File(topDir, "VN_class_matrices").listFiles();
		Arrays.sort(files);
		
		for (File roleDir:files) {
			int roleIdx = roleIdxMap.get(roleDir.getName());
			if (roleIdx<0) 
				continue;
			
			String[] vnClasses = readLines(new File(roleDir, "row_no.txt.gz"));
			for (String vnClass:vnClasses) {
				IndexedMap roleMap = vnRoleMap.get(vnClass);
				if (roleMap==null)
					vnRoleMap.put(vnClass.intern(), roleMap=new IndexedMap(roleIdxMap.size()));
				roleMap.put(roleIdx, roleMap.size());
			}
		}
		
		return vnRoleMap;
	}

	static Map<String, Map<String, float[]>> readSP(File dir, TObjectIntMap<String> roleIdxMap, Map<String, IndexedMap> vnRoleMap, boolean isLemma) throws IOException {
		Map<String, Map<String, float[]>> spMap = new HashMap<String, Map<String, float[]>>();
		
		String[] colIds = null;
		SparseVec rowVals = null;
		
		File[] files = dir.listFiles();
		Arrays.sort(files);
		for (File roleDir:files) {
			int roleIdx = roleIdxMap.get(roleDir.getName());
			if (roleIdx<0) 
				continue;
			
			System.out.println("Processing "+roleDir.getName());
			
			String[] rowIds = readLines(new File(roleDir, "row_no.txt.gz"));
			/*
			if (readIndexOnly) {
				for (String rowId:rowIds)
					spMap.put(rowId, null);
				continue;
			}*/
			
			if (colIds==null) {
				colIds = readLines(new File(roleDir, "col_no.txt.gz"));
				rowVals = new SparseVec(colIds.length);
			}
			
			int i=0;
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(roleDir, "vspace.txt.gz")))),0x10000);

			while (readRow(reader, rowVals, rowIds.length)) {
				System.out.print('.');
				
				if (rowVals.isEmpty()) continue;
				
				String vnClassId = rowIds[i];
				if (isLemma)
					vnClassId = vnClassId.substring(vnClassId.indexOf('-')+1);
				
				IndexedMap roleMap = vnRoleMap.get(vnClassId);
				
				if (roleMap==null) {
					System.err.println("Didn't find class "+vnClassId);
					continue;
				}

				Map<String, float[]> fillerMap = spMap.get(rowIds[i]);
				if (fillerMap==null)
					spMap.put(rowIds[i].intern(), fillerMap=new HashMap<String, float[]>());

				for (int j=0; j<rowVals.indices.size(); ++j) {
					String filler = colIds[rowVals.indices.get(j)];
					filler = filler.substring(0, filler.length()-2);
					float[] vals = fillerMap.get(filler);
					if (vals==null)
						fillerMap.put(filler.intern(), vals=new float[roleMap.size()+1]);
					vals[roleMap.get(roleIdx)] = rowVals.values.get(j);
				}
				
				++i;
				
			}
			reader.close();
			
			int fillerCnt = 0;
			for (Map.Entry<String, Map<String, float[]>> entry:spMap.entrySet())
				fillerCnt+=entry.getValue().size();
			
			System.out.printf("\ntotal classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		}

		int fillerCnt = 0;
		for (Map.Entry<String, Map<String, float[]>> entry:spMap.entrySet())
			//if (!readIndexOnly)
			fillerCnt+=entry.getValue().size();
		
		System.out.printf("\ntotal classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		
		return spMap;
	}

	void readSP(File topDir, boolean readVN, boolean readLeamma) throws IOException {

		roleIdxMap = getAllRoles(topDir);
		vnRoleMap = getVNRoles(topDir, roleIdxMap);
		
		roleSP = new HashMap<String, float[]>();
		{
			String[] fillerIds = readLines(new File(topDir, "role_matrix/col_no.txt.gz"));
			SparseVec[] matrix = readMatrix(new File(topDir, "role_matrix"), roleIdxMap.size(), fillerIds.length,  true);
			for (int i=0; i<matrix.length;++i)
				if (matrix[i]!=null)
					roleSP.put(fillerIds[i].substring(0, fillerIds[i].length()-2).intern(), matrix[i].getDense(roleIdxMap.size()+1));

			System.out.println("vocabulary: "+roleSP.size());
		}

		if (readVN) {
			System.out.println("Reading VN classes");	
			vnclsSP  = readSP(new File(topDir, "VN_class_matrices"), roleIdxMap, vnRoleMap, false);
			
			for (Map.Entry<String, Map<String, float[]>> ec:vnclsSP.entrySet()) {
				IndexedMap indexedRoleMap = vnRoleMap.get(ec.getKey());
				
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet()) {
					float[] roleSPVals = roleSP.get(ef.getKey());
					
					if (roleSPVals==null) {
						System.out.println("Huh? "+ec.getKey()+" "+ef.getKey());
						continue;
					}
					
					for (int i=0; i<ef.getValue().length-1; ++i)
						if (ef.getValue()[i]==0f && roleSPVals[indexedRoleMap.getR(i)]!=0f) {
							System.out.println("Ha! "+ec.getKey()+" "+ef.getKey()+" "+indexedRoleMap.getR(i)+" "+roleSPVals[indexedRoleMap.getR(i)]);
							ef.getValue()[i] = roleSPVals[indexedRoleMap.getR(i)];
						}
				}
			}
		}
			
		if (readLeamma) {
			System.out.println("Reading lemmas");
			lemmaSP = readSP(new File(topDir, "lemma_sense_matrices"), roleIdxMap, vnRoleMap, true);
			
			for (Map.Entry<String, Map<String, float[]>> ec:vnclsSP.entrySet()) {
				String vnId=ec.getKey().substring(ec.getKey().indexOf('-')+1);
				Map<String, float[]> vnMap = vnclsSP==null?null:vnclsSP.get(vnId);
				IndexedMap indexedRoleMap = vnRoleMap.get(ec.getKey());
				
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet()) {
					float[] roleSPVals = vnMap==null?roleSP.get(ef.getKey()):vnMap.get(ef.getKey());
					boolean useVNVal = vnMap!=null;
					
					if (roleSPVals==null) {
						System.out.println("Huh? "+ec.getKey()+" "+ef.getKey());
						continue;
					}
					
					for (int i=0; i<ef.getValue().length-1; ++i)
						if (ef.getValue()[i]==0f && roleSPVals[useVNVal?i:indexedRoleMap.getR(i)]!=0f) {
							System.out.println("Ha! "+ec.getKey()+" "+ef.getKey()+" "+indexedRoleMap.getR(i)+" "+roleSPVals[useVNVal?i:indexedRoleMap.getR(i)]);
							ef.getValue()[i] = roleSPVals[useVNVal?i:indexedRoleMap.getR(i)];
						}
				}
			}
			
		}
		
		for (Map.Entry<String, float[]> entry:roleSP.entrySet()) 
			normalize(entry.getValue());
		
		if (vnclsSP!=null)
			for (Map.Entry<String, Map<String, float[]>> ec:vnclsSP.entrySet())
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet())
					normalize(ef.getValue());
		
		if (lemmaSP!=null)
			for (Map.Entry<String, Map<String, float[]>> ec:lemmaSP.entrySet())
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet())
					normalize(ef.getValue());
	}

	static void normalize(float[] vals) {
	    double norm = 0;
	    for (int i=0; i<vals.length-1; ++i)
	    	norm += vals[i]*vals[i];
	    if (norm==0)
	    	return;
	    norm = Math.sqrt(norm);
	    
	    for (int i=0; i<vals.length-1; ++i)
	    	vals[i] /= norm;
	    
	    vals[vals.length-1] = (float)norm;
    }

	static double dotProduct(float [] vals1, float[] vals2) {
		double ret = 0;	
		for (int i=0; i<vals1.length-1; ++i)
			 ret += vals1[i]*vals2[i];
		return ret;
	}
	
	String makeClassSetKey(TreeSet<String> vnClasses) {
		if (vnClasses.size()==1)
			return vnClasses.first();
		StringBuilder builder = new StringBuilder();
		for (String clsName:vnClasses) {
			if (builder.length()>0)
				builder.append(' ');
			builder.append(clsName);
		}
		return 	builder.toString();	
	}
	
	
	<T> void merge(TObjectFloatMap<T> mergedMap, TObjectFloatMap<T> rhs, float factor) {
		for (TObjectFloatIterator<T> iter=rhs.iterator(); iter.hasNext(); ) {
			iter.advance();
			mergedMap.adjustOrPutValue(iter.key(), iter.value()*factor, iter.value()*factor);
		}
	}
	
	<T> void merge(Map<T, TObjectFloatMap<T>> mergedMap, Map<T, TObjectFloatMap<T>> rhs, float factor) {
		for (Map.Entry<T, TObjectFloatMap<T>> entry:rhs.entrySet()) {
			TObjectFloatMap<T> lhs = mergedMap.get(entry.getKey());
			if (lhs==null)
				mergedMap.put(entry.getKey(), lhs=new TObjectFloatHashMap<T>());
			merge(lhs, entry.getValue(), factor);
		}
	}
	
	protected Set<String> getVNClasses(String predKey, Set<String> vnclsSet) {
		if (vnclsSet==null)
			return null;
		
		String rolesetId = predKey;
		POS pos = rolesetId.endsWith("-v")?POS.VERB:(rolesetId.endsWith("-n")?POS.NOUN:(rolesetId.endsWith("-j")?POS.ADJECTIVE:null));
		rolesetId = rolesetId.substring(0,rolesetId.length()-2);

		Roleset roleset = langUtil.getRoleSet(rolesetId, pos);
		if (roleset==null || roleset.getClasses().isEmpty())
			return null;
		
		Set<String> foundClasses = new HashSet<String>();
		
		for (String vncls:roleset.getClasses()) {
			String key = vncls.substring(6);
			if (vnclsSet.contains(key))
				foundClasses.add(key.intern());
		}
		return foundClasses.isEmpty()?null:foundClasses;
	}

	public static String getArgHeadword(TBNode node, LanguageUtil langUtil) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return langUtil==null?head.getWord():langUtil.findStems(head).get(0);
	
	}
	
	public String getSPHeadword(TBNode node) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
    	TBNode headNode = getHeadNode(node);
		if (!langUtil.isNoun(headNode.getPOS()))
			return null;
		String lemma = langUtil.findStems(headNode).get(0);
		
		if (node.getPOS().equals("PP")) {
			TBNode pNode = headNode.getHeadOfHead();
			if (pNode.getConstituentByHead()!=node || pNode.getWord()==null)
				return null;
			return "PP-"+pNode.getWord().toLowerCase()+' '+lemma;
		}
		return lemma;
	}

	@Override
	public String getPredicateKey(TBNode predNode, String roleset) {
		POS pos = langUtil.getPOS(predNode.getPOS());
		String key = roleset==null?langUtil.findStems(predNode).get(0):roleset;
		
		if (pos==POS.VERB)
			key+="-v";
		else if (pos==POS.NOUN)
			key+="-n";
		else if (pos==POS.ADJECTIVE)
			key+="-j";
		
		return key;
	}
	
	public static SRLVerbNetSP makeSP(File topDir, boolean readVN, boolean readLemma) throws IOException {
		SRLVerbNetSP sp = new SRLVerbNetSP();
		sp.readSP(topDir, readVN, readLemma);
		return sp;
	}

	@Override
	public TObjectFloatMap<String> getSP(String headword, boolean discount) {
		if (roleSP==null || roleCntMap==null)
			return null;
		return getSP(headword, roleSP, roleCntMap, discount);
	}

	List<TObjectFloatMap<String>> getSP(List<String> headwords, Map<String, float[]> spModelMap, Map<String, TObjectFloatMap<String>> cntMap, boolean discount) {
		List<TObjectFloatMap<String>> retList = new ArrayList<TObjectFloatMap<String>>();
		for (String headword:headwords)
			retList.add(getSP(headword, spModelMap, cntMap, discount));
		return retList;
	}
	
	<T> void scale(TObjectFloatMap<T> map, float factor) {
		for (TObjectFloatIterator<T> iter=map.iterator(); iter.hasNext(); ) {
			iter.advance();
			iter.setValue(iter.value()*factor);
		}
	}

	List<TObjectFloatMap<String>> makeDummySP(int length) {
		List<TObjectFloatMap<String>> retList = new ArrayList<TObjectFloatMap<String>>(length);
		for (int i=0; i<length; ++i)
			retList.add(null);
		return retList;
	}
	
	@Override
	public List<TObjectFloatMap<String>> getSP(String predKey, List<String> headwords, boolean discount) {
		
		boolean isPredPP = predKey.startsWith("PP-");			
		Map<String, TObjectFloatMap<String>> localRoleCntMap = isPredPP?ppCntMap.get(predKey.substring(3)):predCntMap.get(predKey);
		if (localRoleCntMap==null) {
			if (useRoleBackoff)
				return getSP(headwords, roleSP, roleCntMap, discount);
			return makeDummySP(headwords.size());
		} else if (isPredPP)
			return getSP(headwords, roleSP, localRoleCntMap, discount);
		Set<String> foundClasses = getVNClasses(predKey, vnclsSP==null?null:vnclsSP.keySet());
		if (foundClasses==null) {
			if (vnclsSP==null || useRoleBackoff)
				return getSP(headwords, roleSP, localRoleCntMap, discount);
			return makeDummySP(headwords.size());
		}
		
		List<TObjectFloatMap<String>> retList = null;
		for (String vncls:foundClasses) {
			List<TObjectFloatMap<String>> clsSP = getSP(headwords, vnclsSP.get(vncls), localRoleCntMap, discount);
			if (retList==null) {
				retList=clsSP;
				continue;
			}
			for (int i=0; i<retList.size();++i)					
				if (retList.get(i)==null)
					retList.set(i, clsSP.get(i));
				else if (clsSP.get(i)!=null)
					merge(retList.get(i), clsSP.get(i), 1f);
		}
		if (foundClasses.size()>1)
			for (TObjectFloatMap<String> map:retList)
				if (map!=null)
					scale(map, 1f/foundClasses.size());
		
		return retList;
	}

	public TObjectFloatMap<String> getSP(String predKey, TBNode argNode, boolean discount) {
		return getSP(predKey, getSPHeadword(argNode), discount);
	}
	
	public TObjectFloatMap<String> getSP(String predKey, String headword, boolean discount) {
		if (headword==null)
			return null;
		
		if (headword.startsWith("PP-")) {
			String pp = headword.substring(3,headword.indexOf(' '));
			headword = headword.substring(headword.indexOf(' ')+1);

			Map<String, TObjectFloatMap<String>> wordCntMap = ppCntMap.get(pp);
			if (wordCntMap==null) {
				if (useRoleBackoff)
					return getSP(headword, roleSP, roleCntMap, discount);
				return null;
			}
			return getSP(headword, roleSP, wordCntMap, discount);
		}
		
		Map<String, TObjectFloatMap<String>> localRoleCntMap = predCntMap.get(predKey);
		if (localRoleCntMap==null) {
			if (useRoleBackoff)
				return getSP(headword, roleSP, roleCntMap, discount);
			return null;
		}
		
		Set<String> foundClasses = getVNClasses(predKey, vnclsSP==null?null:vnclsSP.keySet());
		if (foundClasses==null) {
			if (vnclsSP==null || useRoleBackoff)
				return getSP(headword, roleSP, localRoleCntMap, discount);
			return null;
		}
		
		TObjectFloatMap<String> ret = null;
		for (String vncls:foundClasses) {
			TObjectFloatMap<String> clsSP = getSP(headword, vnclsSP.get(vncls), localRoleCntMap, discount);
			if (ret==null) {
				ret=clsSP;
				continue;
			}
			if (clsSP!=null)
				merge(ret, clsSP, 1f);
		}
		if (foundClasses.size()>1 && ret !=null)
			scale(ret, 1f/foundClasses.size());
		return ret;
	}
	
	
	
	@Override
	public void makeSP(Map<String, Map<String, TObjectFloatMap<String>>> inputCntMap) {
		
		predCntMap = new HashMap<String, Map<String, TObjectFloatMap<String>>>();
		ppCntMap = new HashMap<String, Map<String, TObjectFloatMap<String>>>();
		roleCntMap = new HashMap<String, TObjectFloatMap<String>>();

		for (Map.Entry<String, Map<String, TObjectFloatMap<String>>> ec:inputCntMap.entrySet()) {
			merge(roleCntMap, ec.getValue(), 1f);
			
			Map<String, TObjectFloatMap<String>> newMap = new HashMap<String, TObjectFloatMap<String>>();
			merge(newMap, ec.getValue(), 1f);
			
			if (ec.getKey().startsWith("PP-"))
				ppCntMap.put(ec.getKey().substring(3), newMap);
			else
				predCntMap.put(ec.getKey(), newMap);
			
			/*
			Set<String> foundClasses = getVNClasses(ec.getKey(), vnclsSP.keySet());
			if (foundClasses==null)
				continue;
			
			for (String vncls:foundClasses) {
				Map<String, TObjectFloatMap<String>> cntMap = predCntMap.get(vncls);
				if (cntMap==null)
					predCntMap.put(vncls, cntMap = new HashMap<String, TObjectFloatMap<String>>());
				merge(cntMap, ec.getValue(), 1f/foundClasses.size());
			}*/
		}
		
		//if (predCntMap!=null)
		for (Map.Entry<String, Map<String, TObjectFloatMap<String>>> entry:predCntMap.entrySet()) {
			TObjectFloatMap<String> tCntMap = new TObjectFloatHashMap<String>();
			for (Map.Entry<String, TObjectFloatMap<String>> e2:entry.getValue().entrySet())
				merge(tCntMap, e2.getValue(), 1f);
			entry.getValue().put(null, tCntMap);
		}
		for (Map.Entry<String, Map<String, TObjectFloatMap<String>>> entry:ppCntMap.entrySet()) {
			TObjectFloatMap<String> tCntMap = new TObjectFloatHashMap<String>();
			for (Map.Entry<String, TObjectFloatMap<String>> e2:entry.getValue().entrySet())
				merge(tCntMap, e2.getValue(), 1f);
			entry.getValue().put(null, tCntMap);
		}
		
		TObjectFloatMap<String> tCntMap = new TObjectFloatHashMap<String>();
		for (Map.Entry<String, TObjectFloatMap<String>> entry:roleCntMap.entrySet())
			merge(tCntMap, entry.getValue(), 1f);
		roleCntMap.put(null, tCntMap);
		
	}

}
