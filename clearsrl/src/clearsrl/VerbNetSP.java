package clearsrl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import clearcommon.treebank.TBNode;
import clearcommon.util.LanguageUtil;

public class VerbNetSP extends SelectionalPreference {

	VerbNetSP() {
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
	
	boolean readRow(BufferedReader reader, SparseVec rowVec, int rowDim) throws IOException {
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
	
	
	SparseVec[] readMatrix(File dir, int rowDim, int colDim, boolean transpose) throws IOException {
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
	
	String[] readLines(File file) throws IOException {
		List<String> lineList = new ArrayList<String>();
		
		 BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
         String line;
         while ((line=reader.readLine())!=null)
        	 lineList.add(line.trim());
         reader.close();
         return lineList.toArray(new String[lineList.size()]);
	}
	
	TObjectIntMap<String> getAllRoles(File topDir) throws IOException {
		
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
	
	Map<String, IndexedMap> getVNRoles(File topDir, TObjectIntMap<String> roleIdxMap) throws IOException {
		
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
	
	Map<String, Map<String, float[]>> readSP(File dir, TObjectIntMap<String> roleIdxMap, Map<String, IndexedMap> vnRoleMap, boolean isLemma) throws IOException {
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
			
			/*
			SparseVec[] matrix = readMatrix(roleDir,rowIds.length, colIds.length, false);
			
			System.out.println("Processing "+roleDir.getName());
			
			for (int i=0; i<matrix.length; ++i) {
				if (matrix[i]==null) continue;
				System.out.println("Processing row "+i);
				String vnClassId = rowIds[i];
				if (isLemma)
					vnClassId = vnClassId.substring(vnClassId.indexOf('-')+1);
				
				IndexedMap roleMap = vnRoleMap.get(vnClassId);
				
				if (roleMap==null) {
					System.err.println("Didn't find class "+vnClassId);
					continue;
				}

				Map<String, double[]> fillerMap = spMap.get(rowIds[i]);
				if (fillerMap==null)
					spMap.put(rowIds[i].intern(), fillerMap=new HashMap<String, double[]>());

				for (int j=0; j<matrix[i].indices.size(); ++j) {
					String filler = colIds[matrix[i].indices.get(j)];
					filler = filler.substring(0, filler.length()-2);
					double[] vals = fillerMap.get(filler);
					if (vals==null)
						fillerMap.put(filler.intern(), vals=new double[roleMap.size()]);
					vals[roleMap.get(roleIdx)] = matrix[i].values.get(j);
				}
				// let garbage collection start early
				matrix[i] = null;
			}*/
			int fillerCnt = 0;
			for (Map.Entry<String, Map<String, float[]>> entry:spMap.entrySet()) {
				fillerCnt+=entry.getValue().size();
			}
			
			System.out.printf("\ntotal classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		}

		int fillerCnt = 0;
		for (Map.Entry<String, Map<String, float[]>> entry:spMap.entrySet()) {
			fillerCnt+=entry.getValue().size();
		}
		
		System.out.printf("\ntotal classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		
		return spMap;
	}
	
	
	
	public static VerbNetSP readSP(File topDir, boolean useVNSP, boolean useLemmaSP) throws IOException {
		VerbNetSP sp = new VerbNetSP();		
		
		TObjectIntMap<String> roleIdxMap = sp.getAllRoles(topDir);
		Map<String, IndexedMap> vnRoleMap = sp.getVNRoles(topDir, roleIdxMap);
		
		Map<String, float[]> roleSP = new HashMap<String, float[]>();
		{
			String[] fillerIds = sp.readLines(new File(topDir, "role_matrix/col_no.txt.gz"));
			SparseVec[] matrix = sp.readMatrix(new File(topDir, "role_matrix"), roleIdxMap.size(), fillerIds.length,  true);
			for (int i=0; i<matrix.length;++i)
				if (matrix[i]!=null)
					roleSP.put(fillerIds[i].substring(0, fillerIds[i].length()-2).intern(), matrix[i].getDense(roleIdxMap.size()+1));

			System.out.println("vocabulary: "+roleSP.size());
		}
		
		Map<String, Map<String, float[]>> classSP = null;
		Map<String, Map<String, float[]>> lemmaSP = null;
		
		if (useVNSP) {
			System.out.println("Reading VN classes");	
			classSP  = sp.readSP(new File(topDir, "VN_class_matrices"), roleIdxMap, vnRoleMap, false);
			
			for (Map.Entry<String, Map<String, float[]>> ec:classSP.entrySet()) {
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
		
		if (useLemmaSP) {
			System.out.println("Reading lemmas");
			lemmaSP = sp.readSP(new File(topDir, "lemma_sense_matrices"), roleIdxMap, vnRoleMap, true);
			
			for (Map.Entry<String, Map<String, float[]>> ec:classSP.entrySet()) {
				String vnId=ec.getKey().substring(ec.getKey().indexOf('-')+1);
				Map<String, float[]> vnMap = classSP==null?null:classSP.get(vnId);
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
		
		if (classSP!=null)
			for (Map.Entry<String, Map<String, float[]>> ec:classSP.entrySet())
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet())
					normalize(ef.getValue());
		
		if (lemmaSP!=null)
			for (Map.Entry<String, Map<String, float[]>> ec:lemmaSP.entrySet())
				for (Map.Entry<String, float[]> ef:ec.getValue().entrySet())
					normalize(ef.getValue());

		return sp;
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
	
	@Override
	TObjectDoubleMap<String> getSP(Map<String, TObjectDoubleMap<String>> argSPDB, TBNode node,  LanguageUtil langUtil) {
		if (argSPDB!=null) {
			String headword = getArgHeadword(node, langUtil);
			if (headword==null)
				return null;
			
			TObjectDoubleMap<String> sp = argSPDB.get(headword);
			if (sp!=null)
				return sp;
			
			return argSPDB.get(headword.toLowerCase());
		}
		return null;
	}

	@Override
	public String getArgHeadword(TBNode node, LanguageUtil langUtil) {	
		TBNode head = getHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return langUtil==null?head.getWord():langUtil.findStems(head).get(0);
	}
	
	public static void main(String[] args) throws Exception {  
		
		boolean readLemma = false;
		boolean readVN = false;
		
		if (args.length>1)
			if (args[1].equals("lemma"))
				readLemma = true;
			else if (args[1].equals("VN"))
				readVN = true;
			else if (args[1].equals("all")) {
				readLemma = true;
				readVN = true;
			}
		
		VerbNetSP sp = VerbNetSP.readSP(new File(args[0]), readVN, readLemma);
	}
	
}
