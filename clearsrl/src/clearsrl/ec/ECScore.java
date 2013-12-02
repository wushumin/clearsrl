package clearsrl.ec;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.SortedSet;

public class ECScore {
	SortedSet<String> labelSet;
	TObjectIntMap<String> labelMap;
	int[][] countMatrix;
	
	public ECScore(SortedSet<String> labelSet) {
		this.labelSet = labelSet;
		labelMap = new TObjectIntHashMap<String>();
		
		int count=0;
		for (String ecType:labelSet)
		{
		    if (ECCommon.NOT_EC.equals(ecType))
		        continue;
			labelMap.put(ecType, ++count);
		}
		countMatrix = new int[count+1][count+1];
	}
	
	public void addResult(String systemLabel, String goldLabel) {
		String[] systemLabels = systemLabel.split(" ");
		String[] goldLabels = goldLabel.split(" ");

		if (systemLabels.length==goldLabels.length)
			for (int i=0; i<goldLabels.length && i<systemLabels.length; ++i)
				countMatrix[labelMap.get(systemLabels[i])][labelMap.get(goldLabels[i])]++;
		else if (goldLabels.length<systemLabels.length) {
			if (goldLabels[0]==systemLabels[0])
				for (int i=0; i<systemLabels.length; ++i)
					if (i<goldLabels.length)
						countMatrix[labelMap.get(systemLabels[i])][labelMap.get(goldLabels[i])]++;
					else
						countMatrix[labelMap.get(systemLabels[i])][labelMap.get(ECCommon.NOT_EC)]++;
			else
				for (int i=0; i<systemLabels.length; ++i)
					if (i+goldLabels.length<systemLabels.length)
						countMatrix[labelMap.get(systemLabels[i])][labelMap.get(ECCommon.NOT_EC)]++;
					else
						countMatrix[labelMap.get(systemLabels[i])][labelMap.get(goldLabels[i-systemLabels.length+goldLabels.length])]++;
		} else if (systemLabels.length<goldLabels.length) {
			if (goldLabels[0]==systemLabels[0])
				for (int i=0; i<goldLabels.length; ++i)
					if (i<systemLabels.length)
						countMatrix[labelMap.get(systemLabels[i])][labelMap.get(goldLabels[i])]++;
					else
						countMatrix[labelMap.get(ECCommon.NOT_EC)][labelMap.get(goldLabels[i])]++;
			else
				for (int i=0; i<goldLabels.length; ++i)
					if (i+systemLabels.length<goldLabels.length)
						countMatrix[labelMap.get(ECCommon.NOT_EC)][labelMap.get(goldLabels[i])]++;
					else
						countMatrix[labelMap.get(systemLabels[i-goldLabels.length+systemLabels.length])][labelMap.get(goldLabels[i])]++;
		}
	}
		
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		builder.append("\n********** unlabeled EC Results **********");
		builder.append(toString(countMatrix, false));
		builder.append("\n********** labeled EC Results **********");
		builder.append(toString(countMatrix, true));
		builder.append("************************\n\n");
		
		return builder.toString();
	}
		
	String toString(int[][] count, boolean labeled)	 {
		StringBuilder builder = new StringBuilder();
		int pTotal=0, rTotal=0, fpTotal=0, frTotal=0;
		double p, r, f;
		for (String label: labelSet)
		{
			if (label.equals(ECCommon.NOT_EC)) continue;
			
			int idx = labelMap.get(label);
			
			int pArgT=0, rArgT=0, fpArgT=0, frArgT=0;
			
			for (int i=0; i<count[idx].length; ++i) pArgT+=count[idx][i];
			for (int i=0; i<count.length; ++i) rArgT+=count[i][idx];
			
			if (labeled)
				fpArgT = frArgT = count[idx][idx];
			else {
				fpArgT = pArgT-count[idx][0];
				frArgT = rArgT-count[0][idx];
			}
			
			p = pArgT==0?0:((double)fpArgT)/pArgT;
			r = rArgT==0?0:((double)frArgT)/rArgT;
			f = p==0?0:(r==0?0:2*p*r/(p+r));

			builder.append(String.format("\n%s(%d,%d,%d,%d): precision: %f recall: %f f-measure: %f", label, fpArgT, pArgT, frArgT, rArgT, p*100, r*100, f*100));
			
			pTotal += pArgT;
			rTotal += rArgT;
			fpTotal += fpArgT;
			frTotal += frArgT;
		}
		
		p = pTotal==0?0:((double)fpTotal)/pTotal;
		r = rTotal==0?0:((double)frTotal)/rTotal;
		f = p==0?0:(r==0?0:2*p*r/(p+r));
		
		builder.append(String.format("\n%s(%d,%d,%d,%d): precision: %f recall: %f f-measure: %f\n", "all", fpTotal, pTotal, frTotal, rTotal, p*100, r*100, f*100));
		
		return builder.toString();
	}
}
