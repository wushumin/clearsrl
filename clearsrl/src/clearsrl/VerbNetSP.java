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
        int size;
        
        public IndexedMap(int capacity) {
        	indexArray = new int[capacity];
        	Arrays.fill(indexArray, -1);
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
	
	TObjectIntMap<String> getAllRoles(File topDir) {
		File[] files = new File(topDir, "VN_class_matrices").listFiles();
		Arrays.sort(files);
		
		TObjectIntMap<String> roleIdxMap = new TObjectIntHashMap<String>(60, 0.5f, -1);
		
		for (File roleDir:files) {
			if (!Pattern.matches("[A-Z].+", roleDir.getName()))
				continue;
			roleIdxMap.put(roleDir.getName().intern(), roleIdxMap.size());
		}
		
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
	
	Map<String, Map<String, double[]>> readSP(File dir, TObjectIntMap<String> roleIdxMap, Map<String, IndexedMap> vnRoleMap, boolean isLemma) throws IOException {
		Map<String, Map<String, double[]>> spMap = new HashMap<String, Map<String, double[]>>();
		
		String[] colIds = null;
		SparseVec rowVals = null;
		
		
		for (File roleDir:dir.listFiles()) {
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
				System.out.println("Processing row "+i);
				
				if (rowVals.isEmpty()) continue;
				
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

				for (int j=0; j<rowVals.indices.size(); ++j) {
					String filler = colIds[rowVals.indices.get(j)];
					filler = filler.substring(0, filler.length()-2);
					double[] vals = fillerMap.get(filler);
					if (vals==null)
						fillerMap.put(filler.intern(), vals=new double[roleMap.size()]);
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
			for (Map.Entry<String, Map<String, double[]>> entry:spMap.entrySet()) {
				fillerCnt+=entry.getValue().size();
			}
			
			System.out.printf("total classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		}

		int fillerCnt = 0;
		for (Map.Entry<String, Map<String, double[]>> entry:spMap.entrySet()) {
			fillerCnt+=entry.getValue().size();
		}
		
		System.out.printf("total classes: %d, total fillers %d\n", spMap.size(), fillerCnt);
		
		return spMap;
	}
	
	public static VerbNetSP readSP(File topDir) throws IOException {
		VerbNetSP sp = new VerbNetSP();		
		
		TObjectIntMap<String> roleIdxMap = sp.getAllRoles(topDir);
		
		Map<String, IndexedMap> vnRoleMap = sp.getVNRoles(topDir, roleIdxMap);
		
		String[] rowIds = sp.readLines(new File(topDir, "role_matrix/row_no.txt.gz"));
		String[] colIds = sp.readLines(new File(topDir, "role_matrix/col_no.txt.gz"));
		
		SparseVec[] matrix = sp.readMatrix(new File(topDir, "role_matrix"), rowIds.length, colIds.length, true);
		int fillerCnt = 0;
		for (SparseVec vec:matrix)
			if (vec!=null)
				fillerCnt++;
		
		System.out.println("vocabulary: "+fillerCnt);
		
		System.out.println("Reading VN classes");
		
		Map<String, Map<String, double[]>> classSP  = sp.readSP(new File(topDir, "VN_class_matrices"), roleIdxMap, vnRoleMap, false);
		
		
		//Map<String, Map<String, double[]>> lemmaSP = sp.readSP(new File(topDir, "lemma_sense_matrices"), roleIdxMap, vnRoleMap, true);
		
		
		return sp;
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
		VerbNetSP sp = VerbNetSP.readSP(new File(args[0]));
	}
	
}
