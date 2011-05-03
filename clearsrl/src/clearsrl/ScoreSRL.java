package clearsrl;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeSet;

import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.util.PropertyUtil;

public class ScoreSRL {
    public static void main(String[] args) throws Exception
    {   
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(args[0]);
        props.load(in);
        in.close();
        
        props = PropertyUtil.filterProperties(props, "srl.");
        props = PropertyUtil.filterProperties(props, "score.", true);
        String dataFormat = props.getProperty("data.format", "default");
     
        String[] systems = props.getProperty("systems").trim().split(",");
        for (int i=0; i< systems.length; ++i)
            systems[i] = systems[i].trim();
       
        String[] labels = props.getProperty("labels").trim().split(",");
        
        for (int i=0; i< labels.length; ++i)
            labels[i] = labels[i].trim();

        SRLScore cScore =new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        SRLScore[] scores = new SRLScore[systems.length];
        
        for (int i=0; i<scores.length; ++i)
            scores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        
        Map<String, SortedMap<Integer, List<PBInstance>>>  goldPB = 
            PBUtil.readPBDir(props.getProperty("gold.pbdir"), 
                             props.getProperty("gold.pb.regex").trim(), 
                             props.getProperty("gold.tbdir"),
                             dataFormat.equals("ontonotes")?new OntoNoteTreeFileResolver():null);
        
        List<Map<String, SortedMap<Integer, List<PBInstance>>>> systemPBs = new ArrayList<Map<String, SortedMap<Integer, List<PBInstance>>>>();
        
        for (String system:systems)
        {
            System.out.println(system+".pbdir"+": "+props.getProperty(system+".pbdir"));
            systemPBs.add(PBUtil.readPBDir(props.getProperty(system+".pbdir"), 
                             props.getProperty(system+".pb.regex").trim(), 
                             props.getProperty(system+".tbdir"),
                             null));
        }
        
        for (int i=0; i<systemPBs.size(); ++i)
        {
            int goldArgCnt = 0;
            int sysArgCnt = 0;
            
            Map<String, SortedMap<Integer, List<PBInstance>>> systemPB = systemPBs.get(i);
            for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:goldPB.entrySet())
            {
                SortedMap<Integer, List<PBInstance>> sysMap = systemPB.get(entry.getKey());
                if (sysMap==null) continue;
                
                for (Map.Entry<Integer, List<PBInstance>> e2:entry.getValue().entrySet())
                {
                    List<PBInstance> sysProps = sysMap.get(e2.getKey());
                    if (sysProps==null) continue;
                    
                    List<PBInstance> goldProps = e2.getValue();
                    
                    for (int g=0, s=0; g<goldProps.size() && s<sysProps.size();)
                    {
                        int compare = goldProps.get(g).getPredicate().getTerminalIndex()-sysProps.get(s).getPredicate().getTerminalIndex();
                        if (compare<0) ++g;
                        else if (compare>0) ++s;
                        else
                        {
                            SRInstance goldInstance = new SRInstance(goldProps.get(g));
                            SRInstance sysInstance = new SRInstance(sysProps.get(s));
                            
                            goldArgCnt += goldInstance.getScoringArgs().size();
                            sysArgCnt += sysInstance.getScoringArgs().size();
                            
                            scores[i].addResult(sysInstance, goldInstance);
                            ++g; ++s;
                        }
                    }
                }
            }
            System.out.println(goldArgCnt+" "+sysArgCnt);
        }
        
        for (int i=0; i<scores.length; ++i)
        {
            System.out.println(systems[i]+":");
            scores[i].printResults(System.out);
        }
        
    }
}
