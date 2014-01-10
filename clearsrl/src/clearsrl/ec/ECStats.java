package clearsrl.ec;

import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.util.ChineseUtil;
import clearcommon.util.PropertyUtil;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ECStats {
    
    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
 
    @Option(name="-c",usage="corpus name")
    private String corpus = null;

    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    public static void main(String[] args) throws Exception {       
        ECStats options = new ECStats();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("invalid options:"+e);
            parser.printUsage(System.err);
            System.exit(0);
        }
        
        if (options.help) {
            parser.printUsage(System.err);
            System.exit(0);
        }
        
        Properties props = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(options.propFile), "UTF-8");
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        props = PropertyUtil.filterProperties(props, "clearsrl.ecger.", true);

        ChineseUtil chLangUtil = new ChineseUtil();
        if (!chLangUtil.init(PropertyUtil.filterProperties(props, "chinese.", true)))
            System.exit(-1);
        
        options.corpus=options.corpus==null?"":options.corpus+"."; 
        Map<String, TBTree[]> tbMap = TBUtil.readTBDir(props.getProperty(options.corpus+"tbdir"), props.getProperty(options.corpus+"tb.regex"), chLangUtil.getHeadRules());
        Map<String, TBTree[]> parseMap = TBUtil.readTBDir(props.getProperty(options.corpus+"parsedir"), props.getProperty(options.corpus+"tb.regex"), chLangUtil.getHeadRules());
        Map<String, SortedMap<Integer, List<PBInstance>>>  propMap = 
                PBUtil.readPBDir(props.getProperty(options.corpus+"propdir"), props.getProperty(options.corpus+"pb.regex"), new TBReader(parseMap));
        
        TObjectIntMap<String> traceMap = new TObjectIntHashMap<String>();
        TObjectIntMap<String> coverageMap = new TObjectIntHashMap<String>();
        TObjectIntMap<String> totalMap = new TObjectIntHashMap<String>();
        
        int tokenCount = 0;
        int trainCount = 0;
        int testCount = 0;
        int maxCount = 0;
        int headCoverage = 0;
        
        for (Map.Entry<String, TBTree[]> entry : parseMap.entrySet()) {
            TBTree[] tbTrees = tbMap.get(entry.getKey());
            TBTree[] parseTrees = entry.getValue();

            SortedMap<Integer, List<PBInstance>> pbInstances = propMap.get(entry.getKey());
            
            for (int i=0; i<parseTrees.length; ++i) {
                String[] labels = new String[tbTrees[i].getTokenCount()];
                
                BitSet[] goldBitsets = ECCommon.getECCandidates(tbTrees[i]);
                BitSet[] parsedBitsets = ECCommon.getECCandidates(parseTrees[i]);

                tokenCount += tbTrees[i].getTokenCount();
                maxCount += tbTrees[i].getTokenCount()*tbTrees[i].getTokenCount();
                
                int tokenIdx = 0;
                for (TBNode node : tbTrees[i].getTerminalNodes()) {
                    if (!node.isEC()) {
                        trainCount+=goldBitsets[node.getTokenIndex()].cardinality();
                        testCount+=parsedBitsets[node.getTokenIndex()].cardinality();
                        tokenIdx = node.getTokenIndex()+1;
                        continue;
                    }

                    for (BitSet coverage:parsedBitsets)
                        if (coverage.get(tokenIdx)) {
                            headCoverage++;
                            break;
                        }
                    
                    totalMap.adjustOrPutValue(node.getECType(), 1, 1);
                    TBNode head = node.getHeadOfHead();
                    
                    if (head!=null && head.isToken() && !parsedBitsets[head.getTokenIndex()].get(tokenIdx)) {
                        //System.err.println("Not covered: "+entry.getKey()+' '+i+' '+node.getECType()+'/'+node.getTerminalIndex()+'-'+(head==null?"NULL":head)+' '+parseTrees[i].getRootNode().toParse());
                        coverageMap.adjustOrPutValue(node.getECType(), 1, 1);
                    }
                    String label = null;
                    if (node.getParent().hasFunctionTag("SBJ")) {
                        label = "sbj-"+node.getECType();
                        if (head.getPOS().matches("BA|LB|SB")) {
                            List<TBNode> nodes = head.getDependentNodes();
                            if (nodes.size()==1 || nodes.get(0).getPOS().startsWith("V"))
                                head = nodes.get(0);
                            else if (head.getChildIndex()==0 && head.getParent().getChildren().length==2)
                                head = head.getParent().getChildren()[1].getHead();
                            else {
                                for (int c = head.getChildIndex(); c<head.getParent().getChildren().length; ++c)
                                    if (head.getParent().getChildren()[c].isPos("VP")) {
                                        head = head.getParent().getChildren()[c].getHead();
                                        break;
                                    }
                            }
                        } 
//                      if (!head.getPOS().startsWith("V"))
//                          System.out.println(entry.getKey()+' '+tbTrees[i].getRootNode().toParse());
                    } else if (node.getParent().hasFunctionTag("OBJ")) {
                        label = "obj-"+node.getECType();
//                      if (node.getECType().equals("*pro*") && head.getPOS().startsWith("V"))
//                          System.out.println(entry.getKey()+' '+tbTrees[i].getRootNode().toParse());
                    } else 
                        label = node.getECType();
                        
                    // if (node.getWord().equals("*OP*"))
                    //  label = node.getECType();
                    if (head==null || !head.isToken()) {
                        System.err.println(entry.getKey()+' '+node.getECType()+'-'+(head==null?"NULL":head)+' '+tbTrees[i].getRootNode().toParse());
                        continue;
                    }
                    

                    
                    if (!head.getPOS().matches("V.*|DEC")) {
                        System.err.println(entry.getKey()+' '+tbTrees[i].getIndex()+' '+node.getECType()+'-'+(head==null?"NULL":head)+' '+tbTrees[i].getRootNode().toParse());
                        
                        boolean hasVP=false;
                        for (TBNode ancestor:head.getPathToAncestor(head.getConstituentByHead()))
                            if (ancestor.isPos("VP")) {
                                hasVP = true;
                                while (ancestor.getParent()!=null && ancestor.getParent().isPos("VP") && ancestor.getParent().getHead()==head)
                                    ancestor=ancestor.getParent();
                                if (ancestor.getParent()!=node.getParent().getParent())
                                    System.err.println(entry.getKey()+' '+node.getECType()+'-'+(head==null?"NULL":head)+' '+tbTrees[i].getRootNode().toParse());
                                break;
                            }
                        if (!hasVP)
                            System.out.println(entry.getKey()+' '+node.getECType()+'-'+(head==null?"NULL":head)+' '+tbTrees[i].getRootNode().toParse());
                        //if (hasVP)
                        //  continue;
                        
                        //System.out.println(entry.getKey()+' '+tbTrees[i].getRootNode().toParse());
                        //System.out.println(entry.getKey()+' '+parseTrees[i].getRootNode().toParse());
                        //continue;
                    }
                    if (label !=null) {                     
                        if (labels[head.getTokenIndex()]!=null)
                            label = labels[head.getTokenIndex()]+','+label;

//                      if (label.equals("sbj-*pro*,sbj-*pro*"))
//                          System.out.println(entry.getKey()+' '+tbTrees[i].getRootNode().toParse());
                        
                        labels[head.getTokenIndex()] = label;
                        traceMap.adjustOrPutValue(label+' '+head.getPOS(), 1, 1);
                    }
                    
                }
            }
        }
        
        for (Object obj:traceMap.keys())
            System.out.println(obj+" "+traceMap.get((String)obj));
        
        System.out.print("\n\n");
        
        int t1=0, t2=0;
        for (String key:totalMap.keySet()) {
            System.out.printf("%s: %d/%d\n",key, coverageMap.get(key),totalMap.get(key));
            t1+=coverageMap.get(key);
            t2+=totalMap.get(key);
        }
        System.out.printf("Filtering recall: %d/%d/%d %f%%\n", t2-t1, headCoverage, t2, (t2-t1)*100.0/t2);
        System.out.printf("training samples: %d/%d/%d/%d\n", tokenCount, trainCount, testCount, maxCount);
        
        /*
        Map<String, TBTree[]> treebank = TBUtil.readTBDir(props.getProperty("corpus.treebank"), props.getProperty("corpus.regex"));
        Map<String,SortedMap<Integer,List<PBInstance>>> propbank = PBUtil.readPBDir(props.getProperty("corpus.propbank"), props.getProperty("corpus.regex"), props.getProperty("corpus.treebank"));

        TObjectIntHashMap<String> traceMap = new TObjectIntHashMap<String>();
        
        TObjectIntHashMap<String> traceCnt = new TObjectIntHashMap<String>();
        TObjectIntHashMap<String> traceACnt = new TObjectIntHashMap<String>();

        int predCnt = 0;
        int argCnt = 0;
        
        for (Map.Entry<String, SortedMap<Integer,List<PBInstance>>> entry : propbank.entrySet())
        {
            for (Map.Entry<Integer,List<PBInstance>> treeEntry:entry.getValue().entrySet() )
            {
                
                for (PBInstance instance:treeEntry.getValue())
                {
                    ++predCnt;
                    Set<String> traceTypes = new TreeSet<String>();
                    for (PBArg arg : instance.getArgs())
                    {
                        ++argCnt;
                        Set<String> traceATypes = new TreeSet<String>();
                        for (TBNode node : arg.getTerminalNodes())
                        {
                            if (node.isEC())
                            {
                                //if (node.word.equals("*OP*") || node.word.equals("*PRO*") || node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
                                //if (node.word.equals("*OP*") || node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
                                //  continue;
                                
                                String tType = TBNode.WORD_PATTERN.matcher(node.getWord()).group(1);
                                
                                //Matcher matcher = TBTree.TRACE_PATTERN.matcher(tType);
                                //if (matcher.matches())
                                //  tType = matcher.group(1);
                                    
                                traceMap.put(tType, traceMap.get(tType)+1);
                                
                                traceATypes.add(tType);
                                traceTypes.add(tType);
                            }
                        }
                        if (!traceATypes.isEmpty())
                        {
                            for (String trace:traceATypes)
                                traceACnt.put(trace, traceACnt.get(trace)+1);
                            traceACnt.put("ALL", traceACnt.get("ALL")+1);
                        }
                    }
                    if (!traceTypes.isEmpty())
                    {
                        for (String trace:traceTypes)
                            traceCnt.put(trace, traceCnt.get(trace)+1);
                        traceCnt.put("ALL", traceCnt.get("ALL")+1);
                    }
                }
            }
        }
        
        
        for (TObjectIntIterator<String> iter=traceCnt.iterator(); iter.hasNext();)
        {
            iter.advance();
            System.out.printf("%s: %f\n", iter.key(), 100.0*iter.value()/predCnt);
        }
        System.out.println("--------------------------------");
        for (TObjectIntIterator<String> iter=traceACnt.iterator(); iter.hasNext();)
        {
            iter.advance();
            System.out.printf("%s: %f\n", iter.key(), 100.0*iter.value()/argCnt);
        }
        
        //System.out.printf("trace: %d/%d %f\n", traceCnt, predCnt, 100.0*traceCnt.get("ALL")/predCnt);
        //System.out.printf("trace: %d/%d %f\n", traceACnt, argCnt, 100.0*traceACnt.get("ALL")/argCnt);
        
        int sum = 0;
        for (TObjectIntIterator<String> iter=traceMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            sum += iter.value();
        }
        for (TObjectIntIterator<String> iter=traceMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            System.out.printf("%s: %d %f\n", iter.key(), iter.value(), 100.0*iter.value()/sum);
        }
        System.out.println("--------------------------------");
        traceMap = new TObjectIntHashMap<String>();
        for (Map.Entry<String, TBTree[]> entry : treebank.entrySet())
            for (TBTree tree:entry.getValue())
                for (TBNode node : tree.getRootNode().getTerminalNodes())
                {
                    if (node.isEC())
                    {
                        //if (node.word.equals("*OP*") || node.word.equals("*PRO*") || node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
                        //if (node.word.equals("*OP*") || node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
                        //  continue;
                        String tType = TBNode.WORD_PATTERN.matcher(node.getWord()).group(1);
                        
                        
                        //Matcher matcher = TBTree.TRACE_PATTERN.matcher(tType);
                        //if (matcher.matches())
                        //  tType = matcher.group(1);
                            
                        traceMap.put(tType, traceMap.get(tType)+1);
                    }
                }
        
        sum = 0;
        for (TObjectIntIterator<String> iter=traceMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            sum += iter.value();
        }
        for (TObjectIntIterator<String> iter=traceMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            System.out.printf("%s: %d %f\n", iter.key(), iter.value(), 100.0*iter.value()/sum);
        } */
    }
}
