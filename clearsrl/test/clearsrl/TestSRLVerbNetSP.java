package clearsrl;

import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.TObjectFloatMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import clearsrl.SRLVerbNetSP.Type;

public class TestSRLVerbNetSP {

    private static Logger logger = Logger.getLogger("clearsrl");
    
    @Option(name="-prop",usage="properties file")
    transient private File propFile = null; 
    
    @Option(name="-type",usage="type of sp models to build")
    transient private Type type = Type.ROLE; 

    @Option(name="-inDir",usage="VerbNet input directory")
    transient private String topDir;
    
    @Option(name="-inTrain",usage="SRL training headword count file")
    transient private File trainFile;
    
    @Option(name="-inDev",usage="SRL developement headword count file")
    transient private File devFile;
    
    @Option(name="-inTest",usage="SRL developement headword count file")
    transient private File testFile;
    
    @Option(name="-backoff",usage="use backoff")
    transient private boolean backoff;
    
    @Option(name="-h",usage="help message")
    transient private boolean help = false;
    
    static void validate(SRLVerbNetSP sp, Map<String, Map<String, TObjectFloatMap<String>>> countDB, boolean coreOnly, boolean discount) {
    	float cCntG = 0;
        float pTotalG = 0;
        float rTotalG = 0;
    	float cCnt = 0;
        float pTotal = 0;
        float rTotal = 0;
        for (Map.Entry<String, Map<String, TObjectFloatMap<String>>> entry:countDB.entrySet()) {

        	
        	Set<String> vocabSet = new HashSet<String>();
        	for (Map.Entry<String, TObjectFloatMap<String>> e2:entry.getValue().entrySet())
        		vocabSet.addAll(e2.getValue().keySet());
        	List<String> headwords = new ArrayList<String>(vocabSet);
        	Collections.sort(headwords);
        	
        	List<TObjectFloatMap<String>> spList = sp.getSP(entry.getKey(), headwords, discount);
        	Map<String, String> spLabels = new HashMap<String, String>();
        	for (int i=0; i<headwords.size(); ++i) {
        		String highLabel = SRLSelPref.getHighLabel(spList.get(i));
        		if (highLabel!=null && !highLabel.matches("ARG\\d"))
        			highLabel = SRLSelPref.getHighLabel(sp.getSP(headwords.get(i), discount));
        		spLabels.put(headwords.get(i),highLabel);
        	}
        	for (Map.Entry<String, TObjectFloatMap<String>> e2:entry.getValue().entrySet())
        		for (TObjectFloatIterator<String> iter=e2.getValue().iterator(); iter.hasNext(); ) {
        			iter.advance();
        			if (!coreOnly || e2.getKey().matches("ARG\\d"))
        				rTotal += iter.value();
        			String label = spLabels.get(iter.key());
        			if (label!=null && (!coreOnly || label.matches("ARG\\d")))
        				pTotal+=iter.value();
        			if (e2.getKey().equals(label) && (!coreOnly || e2.getKey().matches("ARG\\d")))
        				cCnt+=iter.value();
        		}
            float p = pTotal==0?0:cCnt/pTotal;
            float r = rTotal==0?0:cCnt/rTotal;
            float f = p==0?0:(r==0?0:2*p*r/(p+r));
            
            logger.info(String.format("Processed %s %f(%d/%d) %f(%d/%d) %f", entry.getKey(), p, (int)cCnt, (int)pTotal, r, (int)cCnt, (int)rTotal, f)); 
            cCntG+=cCnt;
            pTotalG+=pTotal;
            rTotalG+=rTotal;
            
        }

        float p = pTotalG==0?0:cCntG/pTotalG;
        float r = rTotalG==0?0:cCntG/rTotalG;
        float f = p==0?0:(r==0?0:2*p*r/(p+r));
        
        System.out.printf("final F-score %f(%d/%d) %f(%d/%d) %f\n", p, (int)cCntG, (int)pTotalG, r, (int)cCntG, (int)rTotalG, f); 
        
    }
    
    
	public static void main(String[] args) throws Exception {  
		
		TestSRLVerbNetSP options = new TestSRLVerbNetSP();
		
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
        Reader in = new InputStreamReader(new FileInputStream(options.propFile), "UTF-8");
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        
        props = PropertyUtil.filterProperties(props, "srl.", true);
        
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language").trim()+'.', true);
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps)) {
            logger.severe(String.format("Language utility (%s) initialization failed",props.getProperty("language.util-class")));
            System.exit(-1);
        }
        
        Properties selprefProps = PropertyUtil.filterProperties(props, "selpref.", true);
        
        if (options.topDir!=null)
        	selprefProps.setProperty("indir", options.topDir);
        selprefProps.setProperty("type", options.type.toString());
        
        SRLVerbNetSP sp = new SRLVerbNetSP();
        sp.setLangUtil(langUtil);
        sp.initialize(selprefProps);
        

        Map<String, Map<String, TObjectFloatMap<String>>> countDB = SRLSelPref.readTrainingCount(options.trainFile);
        sp.makeSP(countDB);
        
        sp.useRoleBackoff = options.backoff;
        boolean coreOnly = true;
        //System.out.println("Train: ");
        //validate(sp, countDB, coreOnly, true);
        
        if (options.devFile!=null) {
        	System.out.println("Dev: ");
        	countDB = SRLSelPref.readTrainingCount(options.devFile);
        	validate(sp, countDB, coreOnly, false);
        }
        
        if (options.testFile!=null) {
        	System.out.println("Test: ");
        	countDB = SRLSelPref.readTrainingCount(options.testFile);
        	validate(sp, countDB, coreOnly, false);
        }
        
/*
		if (options.modelFile!=null && !options.modelFile.exists()) {
			ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(options.modelFile)));
			mOut.writeObject(sp);
			mOut.close();
		}
		
		Map<String, Map<String, TObjectFloatMap<String>>> db = SRLSelPref.readTrainingCount(options.cntFile);
		sp.makeSP(db);
		*/
		
	}
    
}
