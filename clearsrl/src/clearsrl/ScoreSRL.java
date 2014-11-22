package clearsrl;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.OntoNotesTokenizer;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBTokenizer;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.SRInstance.OutputFormat;
import clearsrl.util.Topics;

public class ScoreSRL {
	
	static Logger logger = Logger.getLogger("clearsrl");
	
	enum Verbose {
		ALL,
		VERB,
		NOUN,
		EC,
		NONE
	}
	
	@Option(name="-prop",usage="properties file")
    private File propFile = null;
	
	@Option(name="-d",usage="directory")
    private String dirName = null;

	@Option(name="-v",usage="verbosity (of output difference)")
    private Verbose verbose = Verbose.NONE;
	
	@Option(name="-t",usage="types of scoring")
    private SRLScore.Type type = SRLScore.Type.ALL;
	
	@Option(name="-h",usage="help message")
    private boolean help = false;
	
	static String getHeadStr(SRInstance instance) {
		String a0="";
		String a1="";
		for (SRArg arg:instance.args)
			if (arg.getLabel().equals("ARG0")) {
				a0 = Topics.getTopicHeadword(arg.node, null);
				break;
			}
		for (SRArg arg:instance.args)
			if (arg.getLabel().equals("ARG1")) {
				a1 = Topics.getTopicHeadword(arg.node, null);
				break;
			}
		return a0+'-'+instance.getPredicateNode().getWord()+'-'+a1;		
	}
	
	static void printARGDiff(SRInstance sys, SRInstance gold) {
		
		boolean match = true;
		
		for (SRArg gArg:gold.args)
			if (gArg.getLabel().matches("ARG[01]")) {
				for (SRArg sArg:sys.args) {
					if (gArg.getLabel().equals(sArg.getLabel())) {
						if (!gArg.getTokenSet().equals(sArg.tokenSet))
							match = false;
						break;
					}
				}
				if (match==false)
					break;
			}

		if (match)
			return;
		
		System.out.println(gold.getTree());
		System.out.println(getHeadStr(gold)+' '+gold.toString());
		System.out.println(getHeadStr(sys)+' '+sys.toString());
		System.out.println(sys.getTree());
		System.out.print("\n");
	}
	
    public static void main(String[] args) throws Exception {
    	
    	ScoreSRL options = new ScoreSRL();
    	CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e)
        {
            System.err.println("invalid options:"+e);
            parser.printUsage(System.err);
            System.exit(0);
        }
        if (options.help){
            parser.printUsage(System.err);
            System.exit(0);
        }
    	
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(options.propFile);
        props.load(in);
        in.close();
        
        props = PropertyUtil.resolveEnvironmentVariables(props);

        props = PropertyUtil.filterProperties(props, "srl.", true);
        props = PropertyUtil.filterProperties(props, "score.", true);

        {
	        String logLevel = props.getProperty("logger.level");
	        if (logLevel!=null) {
		        ConsoleHandler ch = new ConsoleHandler();
		        ch.setLevel(Level.parse(logLevel));
		        logger.addHandler(ch);
		        logger.setLevel(Level.parse(logLevel));
		        Logger.getLogger("clearcommon").addHandler(ch);
		        Logger.getLogger("clearcommon").setLevel(Level.parse(logLevel));
	        }
        }
        
        PBTokenizer goldTokenizer = props.getProperty("gold.pb.tokenizer")==null?(props.getProperty("gold.data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(props.getProperty("gold.pb.tokenizer")).newInstance();
     
        
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language").trim()+'.', true);
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps)) {
            System.err.println(String.format("Language utility (%s) initialization failed",props.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        String[] systems = props.getProperty("systems").trim().split(",");
        for (int i=0; i< systems.length; ++i)
            systems[i] = systems[i].trim();
       
        String[] labels = props.getProperty("labels").trim().split(",");
        
        for (int i=0; i< labels.length; ++i)
            labels[i] = labels[i].trim();

        SRLScore iScore =new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        SRLScore uScore =new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        SRLScore[] scores = new SRLScore[systems.length];
        SRLScore[] vScores = new SRLScore[systems.length];
        SRLScore[] nScores = new SRLScore[systems.length];
        boolean printNScore = false;
        
        for (int i=0; i<scores.length; ++i) {
            scores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
            vScores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
            nScores[i] = new SRLScore(new TreeSet<String>(Arrays.asList(labels)));
        }
        Map<String, SortedMap<Integer, List<PBInstance>>>  goldPB = 
            PBUtil.readPBDir(props.getProperty("gold.pbdir"), 
                             props.getProperty("gold.pb.regex").trim(), 
                             props.getProperty("gold.tbdir"),
                             goldTokenizer);
        
        List<Map<String, SortedMap<Integer, List<PBInstance>>>> systemPBs = new ArrayList<Map<String, SortedMap<Integer, List<PBInstance>>>>();
        
        for (String system:systems) {
        	String propDir = options.dirName==null?props.getProperty(system+".pb.regex").trim():options.dirName;
            systemPBs.add(PBUtil.readPBDir(propDir, 
                             props.getProperty(system+".pb.regex").trim(), 
                             props.getProperty(system+".tbdir"),
                             new DefaultPBTokenizer()));
        }
        
        String outTemplate = props.getProperty("output.propfile");
        PrintStream goldPropOut = null;
        PrintStream interPropOut = null;
        PrintStream unionPropOut = null;
        List<PrintStream> sysPropOuts = new ArrayList<PrintStream>();
        if (outTemplate == null)
        {
            interPropOut = unionPropOut = goldPropOut = new PrintStream("/dev/null");
            for (String system:systems)
                sysPropOuts.add(goldPropOut);
        }
        else
        {
            goldPropOut = new PrintStream(outTemplate.replace("SYSTEM", "gold"));
        if (systems.length>1)
        {
            interPropOut = new PrintStream(outTemplate.replace("SYSTEM", "intersection"));
            unionPropOut = new PrintStream(outTemplate.replace("SYSTEM", "union"));
        }
            for (String system:systems)
                sysPropOuts.add(new PrintStream(outTemplate.replace("SYSTEM", system)));
        }
        
        for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:goldPB.entrySet())
        {
            SortedMap<Integer, List<PBInstance>> goldMap = entry.getValue();
            List<SortedMap<Integer, List<PBInstance>>> sysMaps = new ArrayList<SortedMap<Integer, List<PBInstance>>>();
            for (Map<String, SortedMap<Integer, List<PBInstance>>> systemPB:systemPBs)
            {
                sysMaps.add(systemPB.get(entry.getKey()));
                if (sysMaps.get(sysMaps.size()-1)==null) break;
            }
            if (sysMaps.get(sysMaps.size()-1)==null) continue;
            
            for (Map.Entry<Integer, List<PBInstance>> e2:goldMap.entrySet())
            {
                List<PBInstance> goldProps = e2.getValue();
                
                if (!goldProps.isEmpty())
                	TBUtil.linkHeads(goldProps.get(0).getTree(), langUtil.getHeadRules());
                
                List<List<PBInstance>> sysPropsList = new ArrayList<List<PBInstance>>();
                for (SortedMap<Integer, List<PBInstance>> sysMap:sysMaps)
                {
                    sysPropsList.add(sysMap.get(e2.getKey()));
                    if (sysMap.get(e2.getKey())!=null && !sysMap.get(e2.getKey()).isEmpty())
                    	TBUtil.linkHeads(sysMap.get(e2.getKey()).get(0).getTree(), langUtil.getHeadRules());
                    
                    if (sysPropsList.get(sysPropsList.size()-1)==null) break;
                }
                if (sysPropsList.get(sysPropsList.size()-1)==null) continue;
                
                for (PBInstance goldProp:goldProps)
                {
                    SRInstance goldInstance = new SRInstance(goldProp);
                    for (SRArg arg:goldInstance.args)
                    	arg.label = LanguageUtil.removePBLabelModifier(arg.label);
                    
                    // don't evaluate the actual light verb
                    if (goldInstance.getRolesetId().endsWith(".LV")) continue;
                    List<SRInstance> sysInstances = new ArrayList<SRInstance>();
                    
                    boolean found = false;
                    for (List<PBInstance> sysProps:sysPropsList)
                    {
                        found = false;
                        for (PBInstance sysProp:sysProps)
                            if (goldProp.getPredicate().getTokenIndex()==sysProp.getPredicate().getTokenIndex())
                            {
                                found = true;
                                SRInstance sysInstance = new SRInstance(sysProp);
                                for (SRArg arg:sysInstance.args)
                                	arg.label = LanguageUtil.removePBLabelModifier(arg.label);
                                sysInstances.add(sysInstance);
                                break;
                            }
                        if (!found) break;
                    }
                    if (!found) continue;
                    
                    for (int i=0; i<scores.length;++i) {
                    	if (options.verbose==Verbose.EC)
                    		printARGDiff(sysInstances.get(i), goldInstance);
                        boolean match = scores[i].addResult(sysInstances.get(i), goldInstance, options.type);
                        if (!match && options.verbose!=Verbose.NONE && (options.verbose==Verbose.ALL ||
                        			options.verbose==Verbose.VERB && goldInstance.getPredicateNode().getPOS().startsWith("V") ||
                        			options.verbose==Verbose.NOUN && !goldInstance.getPredicateNode().getPOS().startsWith("V"))) { 
                    		System.out.println(goldInstance.toString(OutputFormat.TEXT));
                    		System.out.println(sysInstances.get(i).toString(OutputFormat.TEXT));
                    		System.out.println(sysInstances.get(i).getTree().toPrettyParse());
                    		System.out.println(goldInstance.getTree().toPrettyParse());
                        }
                        
                        
                        
                        if (goldInstance.getPredicateNode().getPOS().startsWith("V")) {
                            vScores[i].addResult(sysInstances.get(i), goldInstance, options.type);
                        } else {
                            nScores[i].addResult(sysInstances.get(i), goldInstance, options.type);
                            printNScore = true;
                        }
                        sysPropOuts.get(i).println(sysInstances.get(i).toPropbankString());
                    }
                    goldPropOut.println(goldInstance.toPropbankString());
                    
                    
                    
                    if (scores.length==1) continue;
                    
                    SRInstance interInstance = sysInstances.get(0);
                    for (int i=1; i<sysInstances.size(); ++i)
                        interInstance = SRLScore.getIntersection(interInstance, sysInstances.get(i));
                    
                    iScore.addResult(interInstance, goldInstance, options.type);
                    interInstance.cleanUpArgs();
                    interPropOut.println(interInstance.toPropbankString());
                    
                    SRInstance unionInstance = sysInstances.get(0);
                    for (int i=1; i<sysInstances.size(); ++i)
                        unionInstance = SRLScore.getUnion(unionInstance, sysInstances.get(i));
                    
                    uScore.addResult(unionInstance, goldInstance, options.type);
                    unionInstance.cleanUpArgs();
                    unionPropOut.println(unionInstance.toPropbankString());
                }
            }
        }
        /*
        
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
        */
        for (int i=0; i<scores.length; ++i)
        {
            //System.out.println(systems[i]+":");
        	if (printNScore) {
        		System.out.println("All predicates:");
        		System.out.println(scores[i].toMacroString());
        	}
            System.out.println("verb predicates:");
            System.out.println(vScores[i].toMacroString());
            if (printNScore) {
	            System.out.println("nominal predicates:");
	            System.out.println(nScores[i].toMacroString());
            }
            //System.out.println("pro argument:");
            //System.out.println(proScore[i]);
            //System.out.println("no pro argument:");
            //System.out.println(noproScore[i]);
        }
        
        if (scores.length>1)
        {
            System.out.println("intersection:");
            System.out.println(iScore);
            
            System.out.println("union:");
            System.out.println(uScore);
        } 
    }
}
