package sofya.apps.dejavu;

//import static sofya.base.Utility.isParsableInt;
import gnu.trove.THashMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Map.Entry;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProgramUnit;
import sofya.base.ProjectDescription;
import sofya.base.SConstants.BlockType;
//import sofya.base.Utility.IntegerPtr;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.EmptyFileException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.ed.structural.CoverageTrace;
import sofya.ed.structural.TraceHandler;
import sofya.graphs.Edge;
import sofya.graphs.Node;
//import sofya.graphs.Graph;
import sofya.graphs.GraphCache;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFGCacheFactory;
import sofya.graphs.cfg.CFHandler;
//import sofya.tools.TestHistoryBuilder;
import sofya.apps.dejavu.EdgesNCoveredbyUnAffectedTest;
//import sofya.apps.dejavu.EdgesCoveredByAffectedTest.NumericStringComparator;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;
@SuppressWarnings("unchecked")
public class EdgesNCoveredbyUnAffectedTest {
     private static final BufferedReader stdin = new BufferedReader(
                                                 new InputStreamReader(
                                                     System.in));
     private InputParser parser;
     private GraphTraverser traverser;
     private PrintStream stderr = System.err;
     private String tag=null;
     private int typeFlags = 0x00000000;
     private CFHandler cfHandler,cfHandlerforNCFG;
    // private static final boolean CHECK_NUMBERING = true;
     private static final boolean DEBUG = false;
     private String Dir="/home/zxu/Documents/jtcas/newoutputs/v";
     Map<Object, Edge[]> uncoveredforNCFG=new THashMap();
     
     Vector<TestcaseInfor> testinfor=new Vector<TestcaseInfor>();
     
     Map<Object, ArrayList<PredicateInfor>> cvpredicates=new THashMap();
     
     Map<String, Map<Integer, Integer>> oldnewmap=new THashMap();
     
     Map<String, String> namemap=new THashMap();
     
     Vector<String> lname=new Vector<String>();
     Vector<String> mname=new Vector<String>();
     
     public EdgesNCoveredbyUnAffectedTest(int typeFlags,String tag){
         this.typeFlags=typeFlags;
         this.tag=tag;
         
     }

    public boolean buildTestHistory(String version, String suiteNo,List<String> classList) {
        if ((version == null) ||(suiteNo==null)|| (classList == null)) {
        throw new IllegalArgumentException("Required parameter is null");
    }
    //get the unaffected test cases
        Vector<Integer> TS=new Vector<Integer>();
        String readts=Dir+version+"/"+suiteNo+"/affectedTS";
       
        TS=readAfftesTS(readts);
        String readpath="/home/zxu/Documents/jtcas/outputs/"+suiteNo+"/pathinfor";
        readpathinfor(readpath);
        try{
            writetoreruntestsuite(TS, version, suiteNo);
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
    // Get the list of trace files
    File tDir = new File("/home/zxu/Documents/jtcas/outputs/"+suiteNo);
    if (!tDir.exists()) {
        stderr.println("Trace directory cannot be found");
        return false;
    }
    if (!tDir.canRead()) {
        stderr.println("Cannot read trace directory");
        return false;
    }
    long startTime = System.currentTimeMillis();
    String[] fileList = (tDir.list(new FilenameFilter() {
                             public boolean accept(File dir, String name) {
                                 if (name.endsWith(".tr")) return true;
                                 return false;
                             }
                         }));
   
    if (fileList.length == 0) {
        stderr.println("No .tr files found in directory");
        return false;
    }

    Arrays.sort(fileList, new NumericStringComparator());
    if (DEBUG) {
        System.out.println(Arrays.toString(fileList));
    }
    String[] Unaffected=new String[fileList.length-TS.size()];
    int m=0;
    for(int i=0;i<fileList.length;i++){
        String num=fileList[i].substring(0, fileList[i].indexOf('.'));
        if(!TS.contains(Integer.parseInt(num)+1)){
            Unaffected[m]=fileList[i];
            m++;
        }
    }
   
    GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
    TraceHandler trHandler = new TraceHandler();
    typeFlags |= BlockType.MASK_BASIC;
    TestHistoryHandler thHandler = new TestHistoryHandler(typeFlags);
    Map<Object, Object> nameSigMap = new THashMap();
    cfHandler = new CFHandler(procCFGs);
   // Map<Object, Object> coveredEdges = new THashMap();
   // Vector Edgecover=new Vector();
   
   
    // Tell the handler to collect all the CFGs, instead of clearing
    // them out each time a new control flow file is read
    cfHandler.setClearOnLoad(false);
   
    // Iterate over the classes creating empty test histories for each
    // method and caching the CFGs for block-type screening later
    String[] procList = null;
    CFG procCFG = null;
    String className;
    int size = classList.size();
    Iterator iterator = classList.iterator();
    for (int i = size; i-- > 0; ) {
        className = (String) iterator.next();
       
        try {
            cfHandler.readCFFile(className + ".java", tag);
        }
        catch (FileNotFoundException e) {
            stderr.println("WARNING: CF file does not exist for class " +
                className);
            continue;
        }
        catch (BadFileFormatException e) {
            stderr.println(e.getMessage());
            return false;
        }
        catch (EmptyFileException e) {
            stderr.println("WARNING: CF file for class " + className +
                " is empty");
            continue;
        }
        catch (IOException e) {
            stderr.println("I/O error: \"" + e.getMessage() +
                           "\" while reading CF file for class " +
                           className);
            return false;
        }

        Iterator procs = procCFGs.iterator(className);
        while (procs.hasNext()) {
            procCFG = (CFG) procs.next();
            MethodSignature mSig = procCFG.getSignature();
            if (mSig == null) {
                stderr.println("Cannot operate on legacy control flow " +
                    "files");
                return false;
            }
           
            // For now, traces still don't use signatures
            String mName = procCFG.getMethodName();
            nameSigMap.put(mName, mSig);
           
            thHandler.setTestHistory(mName, new TestHistory(
                procCFG.getHighestNodeId(), Unaffected.length));
        }
    }//end of For

    procList = thHandler.getMethodList();
    //TestHistory th;
    CoverageTrace trace;
   
   /* List<String> irregulars = CHECK_NUMBERING ?
        irregulars = new ArrayList<String>(4) : null;*/
       
//get the new CFGs of new version and put all the edges into uncovered map.
        GraphCache<CFG> procNCFGs = CFGCacheFactory.createCache();
        String[] procListNCFG = null;
        CFG procNCFG = null;
        String classNameN;
        cfHandlerforNCFG = new CFHandler(procNCFGs);
        cfHandlerforNCFG.setClearOnLoad(false);
        int sizeN = classList.size();
        Iterator iteratorN = classList.iterator();
        for (int i = sizeN; i-- > 0; ) {
            classNameN = (String) iteratorN.next();
           
            try {
                cfHandlerforNCFG.readCFFile(classNameN + ".java", "concolic"+version);
            }
            catch (FileNotFoundException e) {
                stderr.println("WARNING: CF file does not exist for class " +
                    classNameN);
                continue;
            }
            catch (BadFileFormatException e) {
                stderr.println(e.getMessage());
                return false;
            }
            catch (EmptyFileException e) {
                stderr.println("WARNING: CF file for class " + classNameN +
                    " is empty");
                continue;
            }
            catch (IOException e) {
                stderr.println("I/O error: \"" + e.getMessage() +
                               "\" while reading CF file for class " +
                               classNameN);
                return false;
            }

            Iterator procns = procNCFGs.iterator(classNameN);
            while (procns.hasNext()) {
                procNCFG = (CFG) procns.next();
                Edge[] a=new Edge[0];
                uncoveredforNCFG.put(procNCFG.getMethodName(), procNCFG.getEdges(a));
                //cvpredicates:get all the predicates in this method
                ArrayList<PredicateInfor> predarray=new ArrayList<PredicateInfor>();
               
                for(int in=1;in<procNCFG.getHighestNodeId()+1;in++){
                    if(procNCFG.getBlock(in).getSubType().toString().equalsIgnoreCase("if")){
                        ArrayList<String> traceinfor=new ArrayList();
                        PredicateInfor pred=new PredicateInfor(in,traceinfor);
                        predarray.add(pred);
                    }
                   
                }//end of for for the NCFG
                cvpredicates.put(procNCFG.getMethodName(), predarray);
               

            }
        }//end of For all the class in new cfg
   
        // Now iterate over all of the trace files, and for each match hit
        // blocks to test histories for all methods of interest.
    //int testNum = 0;
    int numFiles = Unaffected.length;
    String traceFile;
    for (int fileIdx = 0; fileIdx < numFiles; fileIdx++) {
       /* if (CHECK_NUMBERING &&
                !Unaffected[fileIdx].startsWith(String.valueOf(testNum))) {
            int dotPos = Unaffected[fileIdx].indexOf('.');
            String namePrefix = Unaffected[fileIdx].substring(0, dotPos);
            IntegerPtr prefixNum = new IntegerPtr();
            if (!isParsableInt(namePrefix, 10, prefixNum)) {
                irregulars.add(Unaffected[fileIdx]);
                continue;
            }
            int prefixVal = prefixNum.value;
            if (prefixVal > testNum) {
                if (prefixVal > (testNum + 1)) {
                    stderr.println("WARNING: Trace files not found " +
                        "for tests " + testNum + "-" + (prefixVal - 1) +
                        "! Skipping and setting test number to " +
                        prefixVal + ".");
                }
                else {
                    stderr.println("WARNING: Trace file not found " +
                        "for test " + testNum + "! Skipping and " +
                        "incrementing test number.");
                }
                testNum = prefixVal;
            }
            else {
                // Must be less than expected test number
                irregulars.add(Unaffected[fileIdx]);
                continue;
            }
        }//end of if
*/       
        traceFile =
            "/home/zxu/Documents/jtcas/outputs/"+suiteNo + File.separatorChar + Unaffected[fileIdx];
       /* if (DEBUG) {
            System.out.println("INFO: Processing \"" + Unaffected[fileIdx] +
                "\" as test number " + testNum);
        }*/
       
        // Read the trace
        try {
            trHandler.readTraceFile(traceFile);
        }
        catch (EmptyFileException e) {
            //testNum += 1;
            continue;
        }
        catch (BadFileFormatException e) {
             stderr.println("Error opening trace file " +
                 fileList[fileIdx]);
             stderr.println(e.getMessage());
             return false;
        }
        catch (IOException e) {
            stderr.println("I/O error: \"" + e.getMessage() +
                "\" while reading trace file for " + traceFile);
            return false;
        }
       
        // Transform method name keys in trace file into format
        // expected in CFGs. Ideally this should be unified under
        // the new format used in trace files at some point.
        Map<Object, Object> rekeyedTraces = new THashMap();
        Set<String> trProcs = trHandler.getUnsortedMethodList();
        Iterator<String> trIter = trProcs.iterator();
        int trCount = trProcs.size();
        for (int i = trCount; i-- > 0; ) {
            String trProc = trIter.next();
            try {
                rekeyedTraces.put(Handler.unpackSignature(trProc),
                    trHandler.getTrace(trProc));
            }
            catch (MethodNotFoundException e) {
                throw new AssertionError(e);
            }
        }
      
       
        //for (int i = 0; i < procList.length; i++) {
           /* trace = (CoverageTrace) rekeyedTraces.get(procList[i]);
            if (trace == null) {
                // This method wasn't witnessed in the trace
                continue;
            }

            MethodSignature mSig =
                (MethodSignature) nameSigMap.get(procList[i]);
            procCFG = (CFG) procCFGs.get(mSig).getGraph();
            ArrayList<Edge> traveredEdges=new ArrayList<Edge>();
            traveredEdges=FindtraveredEdges(trace,procCFG);
            coveredEdges.put(procList[i], traveredEdges);//Map(for each trace file)
            Edgecover.add(procList[i]+"+"+traveredEdges);//
*/            traverser=new GraphTraverser(new BytecodeNodeComparer(), new CFEdgeSelector());
            try {
                parser = new InputParser("concolic.prog", "concolic.prog", "concolic", "concolic"+version,
                                         CFGLoader.class);
            }
            catch (BadFileFormatException e) {
                e.printStackTrace();
                System.exit(1);
            }
            catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
          
            ClassPair[] classList1=parser.getClassPairs();
            for(int j=0;j<classList1.length;j++){
                ClassPair clazz = classList1[j];
                MethodPair[] methodPairs = null;


                try {
                    methodPairs = parser.getMethods(clazz);
                }
                catch (FileNotFoundException e) {
                    System.err.println("WARNING: Database files not found for " +
                        clazz.name);
                    continue;
                }
                catch (BadFileFormatException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                catch(MethodNotFoundException e){
                   
                }

                for (int k = 0; k< methodPairs.length; k++) {
                    MethodPair mp = methodPairs[k];
                    if(!lname.contains(mp.name)){
                        lname.add(mp.name);
                    }
                    if(!oldnewmap.containsKey(mp.name)){
                        oldnewmap.put(mp.name, null);
                    }
                    trace = (CoverageTrace) rekeyedTraces.get(mp.name);
                    if (trace == null) {
                        // This method wasn't witnessed in the trace
                        continue;
                    }

                    MethodSignature mSig =
                        (MethodSignature) nameSigMap.get(mp.name);
                    System.out.println(mp.name);
                    procCFG = (CFG) procCFGs.get(mSig).getGraph();
                   
                    FindcoveredPredicates(trace,procCFG, Unaffected[fileIdx]);
                   
                    ArrayList<Edge> traveredEdges=new ArrayList<Edge>();
                    //ArrayList coveredpredicates=new ArrayList();
                   
                   // System.out.println("methodname:"+mSig+"traceno:"+traceFile);
                    traveredEdges=FindtraveredEdges(trace,procCFG);//for each methodpair
                   
                    //System.out.println(Unaffected[fileIdx]+":"+mp.name+":"); 
                 
                   
			 			
			 			
                   // Edgecover.add(mp.name+"+"+traveredEdges);//
                    try{
                        Edge[] ucv=new Edge[0];
                        ucv=uncoveredforNCFG.get(mp.name);
                        //List<Edge> ucvlist=Arrays.asList(ucv);
                       
                        if(ucv!=null){
                        //System.out.println((Edge[])uncoveredforNCFG.get(mp.name));
                           
                            Edge[] Uncoverededges = traverser.getUncoveredEDges(mp,traveredEdges,ucv,oldnewmap);
                            uncoveredforNCFG.put(mp.name, Uncoverededges);
                        }
                    }catch (Exception e){
                        System.out.println(e);
                        e.printStackTrace();
                    }
                    
                }
        }
           
       
       /// testNum += 1;
   // }//for procList.length
   
       
    }//end for # of traced files
    // Write covered edge file, it is useful???
    Mapname();
    try {
        String newpathinfor="/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/"+"unaffectedpathinfor";
        writeunaffectedpathinfor(newpathinfor,Unaffected);
    }
    catch (IOException e) {
        stderr.println("I/O error: \"" + e.getMessage() +
            "\" while writing edge covered file '" +Dir+version+"/"+suiteNo+"/covered" + "'");
        return false;
    }
    try {
        writeUncoveredEdges(Dir+version+"/"+suiteNo+"/uncovered", uncoveredforNCFG);
    }
    catch (IOException e) {
        stderr.println("I/O error: \"" + e.getMessage() +
            "\" while writing edge uncovered file '" + Dir+version+"/"+suiteNo+"/uncovered" + "'");
        return false;
    }
 
   
    try {
        writeHitPredicated(Dir+version+"/"+suiteNo+"/hitpre");
    }
    catch (IOException e) {
        stderr.println("I/O error: \"" + e.getMessage() +
            "\" while writing edge uncovered file '" + Dir+version+"/"+suiteNo+"/hitpre" + "'");
        return false;
    }
    long endTime = System.currentTimeMillis();
    System.out.println("Time elapsed (ms): " + (endTime - startTime));
    return true;
}
 
private void Mapname() {
        // TODO Auto-generated method stub
    Iterator il=mname.iterator();
    while(il.hasNext()){
        String cname=(String)il.next();
        String cname_tmp=cname.substring(cname.indexOf(" ")+1, cname.indexOf('('));
        //cname=cname.replace("_", " ");
        Iterator ill=lname.iterator();
        while(ill.hasNext()){
            String sname=(String)ill.next();
            //String sname_tmp=sname.replace(" ", "_");
            if(sname.contains(cname_tmp)){
                namemap.put(cname, sname);
                break;
            }
        }
    }
       
    }
private ArrayList<Edge> Findcovered(ArrayList<Edge> covered) {
	// TODO Auto-generated method stub
	ArrayList<Edge> refine=new ArrayList<Edge>();
	
	//now covered does not contain the "<any>" type edge
	int tmp=6666;
	for(int i=1;i<covered.size();i++){
		Edge a=covered.get(i-1);
		Edge b=covered.get(i);
		if(a.getPredNodeID()!=0){
			if(a.getPredNodeID()!=b.getPredNodeID()){
				refine.add(a);
			}
			else {
				Edge c=covered.get(i+1);
				if(a.getSuccNodeID()==c.getPredNodeID()){
					refine.add(a);
					tmp=b.getPredNodeID();
					b.setPredNodeID(0);
					
	
					
				}
				else{
					//refine.add(b);
					//a.setPredNodeID(0);

				}
			}
		}
		else{
			a.setPredNodeID(tmp);
		}
	}
	refine.add(covered.get(covered.size()-1));
	
	return refine;
}
private void writeunaffectedpathinfor(String newpathinfor, String[] unaffected) throws IOException {
        // TODO Auto-generated method stub
     PrintWriter pw = new PrintWriter(
               new BufferedWriter(
               new OutputStreamWriter(
                   openOutputFile(newpathinfor, false))), true);
     //System.out.println(Arrays.toString(unaffected));
     
        for(int i=0;i<unaffected.length;i++){
            String num=unaffected[i].substring(0, unaffected[i].indexOf('.'));
            int number=Integer.parseInt(num);
            TestcaseInfor tcinfor=testinfor.get(number);
            Vector<Trace> tr=tcinfor.trace;
            for(int j=0;j<tr.size();j++){
                Trace one=tr.elementAt(j);
                String sname=namemap.get(one.methodname);
               
                Map<Integer, Integer> m=oldnewmap.get(sname);
                one.blockID=m.get(one.blockID);
            }
            String tccstring=Arrays.toString(tcinfor.input);
            pw.println("@ "+tccstring);
            Iterator ill=tcinfor.trace.iterator();
            while(ill.hasNext()){
                Trace trc=(Trace)ill.next();
                pw.println("1 "+trc.methodname+":"+trc.blockID+"**"+trc.symbol);
            }
            pw.println("2 "+tcinfor.path);
        }
       
       
            // Vector<String> saveinput=new Vector();

            /*Iterator il=testinfor.iterator();
            while(il.hasNext()){
                TestcaseInfor tcc=(TestcaseInfor)il.next();
                String tccstring=Arrays.toString(tcc.input);
                pw.println("@ "+tccstring);
                Iterator ill=tcc.trace.iterator();
                while(ill.hasNext()){
                    Trace trc=(Trace)ill.next();
                    pw.println("1 "+trc.methodname+":"+trc.blockID+"**"+trc.symbol);
                }
                pw.println("2 "+tcc.path);
               
            }*/
            pw.close();
            if (pw.checkError()) {
                throw new IOException("Error writing unaffected test case path information file");
        }
           
    }

private void writetoreruntestsuite(Vector<Integer> ts,String version, String suiteNo) throws Exception{
        // TODO Auto-generated method stub
        /*Save all the test cases which are needed to rerun into a new test suite
         *
         */
    String fileName="/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/rerunts";
     PrintWriter pw = new PrintWriter(
           new BufferedWriter(
           new OutputStreamWriter(
               openOutputFile(fileName, false))), true);
    // Vector<String> saveinput=new Vector();

    String filename_1="/home/zxu/Documents/jtcas/testplans.alt/"+suiteNo;
    File file=null;
    FileReader filereader=null;
    LineNumberReader linereader=null;
    int linecount=0;
    String readline=null;
    try{
        file=new File(filename_1);
        filereader=new FileReader(file);
        linereader=new LineNumberReader(filereader);
    }
    catch(FileNotFoundException ex){
    }
    try{
        while(linereader.ready()){
            readline=linereader.readLine();
            linecount++;
            if(ts.contains(linecount)){
                //saveinput.add(readline);
                pw.println(readline);
            }
           
   
        }//while
    }catch(Exception e){
        System.out.println(e.getMessage());
    }//try
   
   
     
    pw.close();
    if (pw.checkError()) {
        throw new IOException("Error writing rerun test suite file");
}
    }

private Vector<Integer> readAfftesTS(String string) {
        // TODO Auto-generated method stub
    Vector<Integer> ts=new Vector<Integer>();
    String filename_1=string;
    File file=null;
    FileReader filereader=null;
    LineNumberReader linereader=null;
    //int linecount=0;
    String readline=null;
    boolean ready=false;
   
   
    try{
        file=new File(filename_1);
        filereader=new FileReader(file);
        linereader=new LineNumberReader(filereader);
    }
    catch(FileNotFoundException ex){
    }
   
    try{
        while(linereader.ready()){
            readline=linereader.readLine();
            if(readline.startsWith("All")){
                ready=true;
            }
            if(ready==true){
                linereader.readLine();
                readline=linereader.readLine();
                String number="";
                for(int i=0;i<readline.length();i++){
                    if(readline.charAt(i)!=' '){
                        number=number+readline.charAt(i);
                        if(readline.charAt(i+1)==' '){
                            int num=Integer.parseInt(number);
                            ts.add(num);
                            number="";
                        }
                    }
                }
                readline=linereader.readLine();
                if(readline!=""){
                	number="";
                    for(int i=0;i<readline.length();i++){
                        if(readline.charAt(i)!=' '){
                            number=number+readline.charAt(i);
                            if(readline.charAt(i+1)==' '){
                                int num=Integer.parseInt(number);
                                ts.add(num);
                                number="";
                            }
                        }
                    }
                }
                break;
            }
   
        }//while
    }catch(Exception e){
        System.out.println(e.getMessage());
    }//try
        return ts;
    }

public void FindcoveredPredicates(CoverageTrace trace1, CFG procCFG1, String trace){
    //int[] blockhit=new int[procCFG1.getHighestNodeId()+1];
    //save informtaion to cvpredicates
     ArrayList predicatehit=cvpredicates.get(procCFG1.getMethodName());
     Iterator il=predicatehit.iterator();
     while(il.hasNext()){
         PredicateInfor pred=(PredicateInfor)il.next();
         if(trace1.query(pred.NodeID)){
             pred.HitPredInfor.add(trace);
             
             }
         }
}
     
     
public ArrayList FindtraveredNodes(CoverageTrace trace1,CFG procCFG1){
    ArrayList<Node> node=new ArrayList<Node>();
    //int[] blockhit=new int[procCFG1.getHighestNodeId()+1];
     for(int i=1; i<procCFG1.getHighestNodeId()+1;i++){
         if(trace1.query(i)){
           
             node.add(procCFG1.getNode(i));
             if(procCFG1.getNode(i).getSuccessorCount()==2){
                 Node pre=procCFG1.getNode(i);
                 Node suc=procCFG1.getNode(i).getSuccessorsList().get(0);
                 Edge a =procCFG1.getEdge(pre, suc);
                 if(a.getLabel()!=null&&(a.getLabel().equals("<any>")||a.getLabel().equals("<r>"))){
                     node.add(suc);
                 }
                 Node pre1=procCFG1.getNode(i);
                 Node suc1=procCFG1.getNode(i).getSuccessorsList().get(1);
                 Edge a1 =procCFG1.getEdge(pre1, suc1);
                 if(a1.getLabel()!=null&&(a1.getLabel().equals("<any>")||a1.getLabel().equals("<r>"))){
                     node.add(suc1);
                 }
             }
         }
     }
    return node;
}

 public ArrayList FindtraveredEdges(CoverageTrace trace1,CFG procCFG1){
     ArrayList<Node> node=new ArrayList<Node>();
     node=FindtraveredNodes(trace1,procCFG1);
     //String traceString=trace1.toString();
     //int[] blockhit=new int[procCFG1.getHighestNodeId()+1];
     ArrayList<Edge> edgeArray=new ArrayList<Edge>();
     Iterator il=node.iterator();
     while(il.hasNext()){
         Node pre=(Node)il.next();
         List<Node> sucList=procCFG1.getNode(pre.getID()).getSuccessorsList();
         Iterator ill=sucList.iterator();
         while(ill.hasNext()){
             Node suc=(Node)ill.next();
             if(node.contains(suc)){
                 Edge e=new Edge();
                 e=procCFG1.getEdge(pre, suc);
                 edgeArray.add(e);
               
             }
         }
     }
   
     return edgeArray;
       
    }
    private class NumericStringComparator implements Comparator<Object> {
        public int compare(Object o1, Object o2) {
            String str1 = (String) o1;
            String str2 = (String) o2;
            int compareLength = (str1.length() > str2.length())
                                ? str1.length()
                                : str2.length();
            for (int i = compareLength - str1.length(); i > 0; i--) {
                str1 = "0" + str1;
            }
            for (int i = compareLength - str2.length(); i > 0; i--) {
                str2 = "0" + str2;
            }
            return str1.compareTo(str2);
        }
    }
   
    public void writeCoveredEdges(String fileName, Vector Coverededges)throws IOException {
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName, false))), true);

// Edges covered information
        pw.println("0 Test History Information");
        pw.println("0 File: " + fileName + "  Created: " +
           new Date(System.currentTimeMillis()));
        pw.println("0 Version: " + ProjectDescription.versionString);
        pw.println("0 Trace type: ");
       
        //String[] methodNames = getMethodList();
        Iterator il=Coverededges.iterator();
        while(il.hasNext()){
            String l=il.next().toString();
            pw.println(l);
           // System.out.println(l);
        }
        /*for (int i = 0; i < Co; i++) {
             writeTestHistory(pw, methodNames[i].replace(' ', '_'),
                    (TestHistory) histories.get(methodNames[i]));
         }*/

        pw.close();
         if (pw.checkError()) {
             throw new IOException("Error writing edge covered file");
     }
   }
    /*write the predicate hit information to a file
     * (not change) if the formate is changed, the readfile part should be changed too
     */
    public void writeHitPredicated(String fileName)throws IOException {
        PrintWriter pw = new PrintWriter(
                new BufferedWriter(
                new OutputStreamWriter(
                    openOutputFile(fileName, false))), true);

//Predicates covered information
        pw.println("0 Predicates hit by unaffected test cases Information for New version");
        pw.println("0 File: " + fileName + "  Created: " +
                new Date(System.currentTimeMillis()));
        pw.println("0 Version: " + ProjectDescription.versionString);
        pw.println("0 Trace type: " );
       
        //Map<Object, ArrayList<PredicateInfor>> cvpredicates

        Set<Entry<Object, ArrayList<PredicateInfor>>> hp;
        hp=cvpredicates.entrySet();
        Iterator il=hp.iterator();
        while(il.hasNext()){
            Entry<Object, ArrayList<PredicateInfor>> l;
            l=(Entry)il.next();
            pw.print("1 "+l.getKey());
            pw.print("+");
            //System.out.print("1 "+l.getKey());
            //System.out.print("+");
            ArrayList<PredicateInfor> test = l.getValue();
            if(test.size()==0){
                pw.println("null");
                //System.out.println("null");
            }
            else{
            Iterator il1=test.iterator();
            while(il1.hasNext()){
                PredicateInfor pred=(PredicateInfor)il1.next();
                pw.print(pred.NodeID+":");
                //System.out.print(pred.NodeID+":");
                ArrayList<String> traces=pred.HitPredInfor;
                if(traces.size()==0){
                    pw.print("null$");
                   // System.out.print("null$");
                }
                else{
                Iterator il2=traces.iterator();
                while(il2.hasNext()){
                    String num=(String)il2.next();
                    num=num.substring(0, num.indexOf('.'));
                    int number=Integer.parseInt(num);
                    pw.print(number+1);
                    pw.print(",");
                    //System.out.print(il2.next().toString());
                    //System.out.print(",");
                }// arraylist of traces
                pw.print("$");
                }
               
            }//araylist of the predicates for this method
            pw.println();
            //System.out.println();
            }//else

    //System.out.println("" +test.toString());
   
    //System.out.println(l.getKey()+"+"+l.getValue().toString());
}
pw.close();
if (pw.checkError()) {
     throw new IOException("Error writing edge uncovered file");
}
    }
   
   
    public void writeUncoveredEdges(String fileName, Map<Object, Edge[]> uncoverededges)throws IOException {
        PrintWriter pw = new PrintWriter(
                         new BufferedWriter(
                         new OutputStreamWriter(
                             openOutputFile(fileName, false))), true);

// Edges covered information
        pw.println("0 Edges uncovered Information for New version");
        pw.println("0 File: " + fileName + "  Created: " +
           new Date(System.currentTimeMillis()));
        pw.println("0 Version: " + ProjectDescription.versionString);
        pw.println("0 Trace type: ");
       
       
       
        Set<Entry<Object, Edge[]>> uncoveredforprint;
        uncoveredforprint=uncoverededges.entrySet();
        Iterator il=uncoveredforprint.iterator();
        while(il.hasNext()){
            Entry<Object, Edge[]> l;
            l=(Entry)il.next();
            pw.print("1 "+l.getKey());
            pw.print("+");
            //System.out.print("1 "+l.getKey());
            //System.out.print("+");
            Edge[] test = l.getValue();
            if(test.length==0){
                pw.println("null");
               // System.out.println("null");
            }
            else{
                for(int i=0;i<test.length;i++){
                    pw.print(test[i].toString()+",");
                    //System.out.print(test[i].toString()+",");
                }
                pw.println();
                //System.out.println();
            }
           
            //System.out.println("" +test.toString());
           
            //System.out.println(l.getKey()+"+"+l.getValue().toString());
        }
        /*for (int i = 0; i < Co; i++) {
             writeTestHistory(pw, methodNames[i].replace(' ', '_'),
                    (TestHistory) histories.get(methodNames[i]));
         }*/

        pw.close();
         if (pw.checkError()) {
             throw new IOException("Error writing edge uncovered file");
     }
   }
    private void readpathinfor(String string) {
        // TODO Auto-generated method stub
        String filename_1=string;
        File file=null;
        FileReader filereader=null;
        LineNumberReader linereader=null;
        //int linecount=0;
        String readline=null;
        try{
            file=new File(filename_1);
            filereader=new FileReader(file);
            linereader=new LineNumberReader(filereader);
        }
        catch(FileNotFoundException ex){
        }
        try{
            while(linereader.ready()){
                readline=linereader.readLine();
               
                TestcaseInfor tc=new TestcaseInfor();
                if(readline.startsWith("@")){
                   
                    //figure out the input correctly
                    String input=readline.substring(2);
                    if(input.startsWith(" ")){
                        input=input.substring(1);   
                    }
                    input=input.replace("  ", " ");
                    String[] input1=input.split(" ");
                    tc.input=input1;
                    //figure out trace
                    readline=linereader.readLine();
                    //tracelist without []
                    String tracelist=readline.substring(readline.indexOf('[')+1, readline.indexOf(']'));
                    String[] tracearray=tracelist.split(", ");
                    Vector<Trace> trace=tc.trace;
                    for(int i=0;i<tracearray.length;i++){
                        Trace one=new Trace();
                        one.methodname=tracearray[i].substring(0, tracearray[i].indexOf(':'));
                        if(!mname.contains(one.methodname)){
                            mname.add(one.methodname);
                        }
                        one.blockID=Integer.parseInt(tracearray[i].substring(tracearray[i].indexOf(':')+1, tracearray[i].indexOf("**")));
                        one.symbol=tracearray[i].substring(tracearray[i].indexOf("**")+2);
                        trace.add(one);
                    }
                    //tc.trace=trace;
                    //path vector
                    Vector<String> path=tc.path;
                    readline=linereader.readLine();
                    String pathlist=readline.substring(readline.indexOf('[')+1, readline.indexOf(']'));
                    String[] patharray=pathlist.split(", ");
                    for(int j=0;j<patharray.length;j++){
                        path.add(patharray[j]);
                    }
                }
                testinfor.add(tc);
            }
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
    }
/*    public String[] getMethodList() {
        String[] methodNames = (String[]) histories.keySet().toArray(
            new String[histories.size()]);
        Arrays.sort(methodNames);
        return methodNames;
    }*/
    /*public String getTypeString() {
        if ((typeFlags & BlockType.MASK_VALID) == 0) {
            throw new IllegalArgumentException("No valid trace type specified");
        }
        StringBuffer sb = new StringBuffer();
        if ((typeFlags & BlockType.MASK_BASIC) == BlockType.MASK_BASIC) {
            sb.append("Basic ");
        }
        if ((typeFlags & BlockType.MASK_ENTRY) == BlockType.MASK_ENTRY) {
            sb.append("Entry ");
        }
        if ((typeFlags & BlockType.MASK_EXIT) == BlockType.MASK_EXIT) {
            sb.append("Exit ");
        }
        if ((typeFlags & BlockType.MASK_CALL) == BlockType.MASK_CALL) {
            sb.append("Call ");
        }
        if ((typeFlags & BlockType.MASK_RETURN) == BlockType.MASK_RETURN) {
            sb.append("Return ");
        }
        return sb.toString().trim();
    }*/
    public static final FileOutputStream openOutputFile(
            String fileName, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileOutputStream(fileName, append);
    }
public static void main(String[] argv) {
    String version = null,suiteNo=null,tag="concolic";
    int typeFlags = 0x00000000;
    int argIndex = 1;
    List<String> classList = new ArrayList<String>();
        // if no arguments given, prompt for test dir name
        if (argv.length < argIndex ) {
            System.out.print("Enter version number: ");
            try{
            version = stdin.readLine();
            }catch(IOException e){
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
        else{
            version=argv[0];
            suiteNo=argv[1];

           
        }
        List<ProgramUnit> unitList=new ArrayList<ProgramUnit>();
        try{
            Handler.readProgFile("concolic.prog", "concolic", unitList);
            int size=unitList.size();
            Iterator units=unitList.iterator();
            for(int i=size;i-->0;){
                ProgramUnit pUnit=(ProgramUnit) units.next();
                classList.addAll(pUnit.classes);
            }
        }
        catch(IOException e){
            System.err.println(e.getMessage());
            System.exit(1);
        }
           
       
        //System.out.println("traceDir: " + traceDir + "\nthFile: " +
        //                   thFile + "\nprogName: " + progName);
   
    EdgesNCoveredbyUnAffectedTest thBuilder = new EdgesNCoveredbyUnAffectedTest(typeFlags, tag);
   
    //@SuppressWarnings("unused")
    //long startTime = System.currentTimeMillis();
   
    if (!thBuilder.buildTestHistory(version,suiteNo, classList)) {
        System.err.println("Test history construction failed");
        System.exit(1);
    }
   
    //@SuppressWarnings("unused")
    //long endTime = System.currentTimeMillis();
    //System.out.println("Time elapsed (ms): " + (endTime - startTime));
}

}
