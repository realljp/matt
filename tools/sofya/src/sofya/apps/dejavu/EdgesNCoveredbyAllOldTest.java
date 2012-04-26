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
import sofya.graphs.Graph;
import sofya.graphs.GraphCache;
import sofya.graphs.Node;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFGCacheFactory;
import sofya.graphs.cfg.CFHandler;
import sofya.tools.TestHistoryBuilder;
import sofya.apps.dejavu.EdgesNCoveredbyUnAffectedTest;
//import sofya.apps.dejavu.EdgesCoveredByAffectedTest.NumericStringComparator;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;
@SuppressWarnings("unchecked")
public class EdgesNCoveredbyAllOldTest {
	 private static final BufferedReader stdin = new BufferedReader(
                                                 new InputStreamReader(
                                                     System.in));
	 //private InputParser parser;
	 private GraphTraverser traverser;
	 private PrintStream stderr = System.err;
	 private String tag=null;
	 private int typeFlags = 0x00000000;
	 private CFHandler cfHandler;
	 //private static final boolean CHECK_NUMBERING = true;
	 private static final boolean DEBUG = false;
	//private TestMapper testmapper;
	 Map<Object, Edge[]> uncoveredforNCFG=new THashMap();
	 Map<Object, ArrayList<PredicateInfor>> cvpredicates=new THashMap();
	 private String Dir="/home/zxu/Documents/jtcas/newoutputs/v";
	 public EdgesNCoveredbyAllOldTest(int typeFlags,String tag){
		 this.typeFlags=typeFlags;
		 this.tag=tag;
		 
	 }

	@SuppressWarnings("unchecked")
	public boolean buildNTestHistory(String version,String suiteNo, List<String> classList) {
		if ((version == null) ||(suiteNo==null)|| (classList == null)) {
        throw new IllegalArgumentException("Required parameter is null");
    }
    Map<Object, Edge[]> UncoveredbyUAT=new THashMap();
    //Map<Object, PredicateInfor> PredhitbyUAT=new THashMap();
    Vector<Integer> TS=new Vector<Integer>();
	String readts=Dir+version+"/"+suiteNo+"/affectedTS";
	
	TS=readAfftesTS(readts);
	
    readHitpredfile("/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/"+"hitpre");
    UncoveredbyUAT=readUncoveredEdgefile("/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/"+"uncovered");
    // Get the list of trace files
    File tDir = new File("/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo);
    
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
    String[] affectedlist=new String[TS.size()];
    Iterator ilts=TS.iterator();
    int ai=0;
    while(ilts.hasNext()){
    	int num=(Integer)ilts.next();
    	affectedlist[ai]=fileList[num-1];
    	ai++;
    }
    fileList=affectedlist;
    GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
    TraceHandler trHandler = new TraceHandler();
    typeFlags |= BlockType.MASK_BASIC;//this should be an input, change later
    TestHistoryHandler thHandler = new TestHistoryHandler(typeFlags);
    Map<Object, Object> nameSigMap = new THashMap();
    cfHandler = new CFHandler(procCFGs);
    //Map<Object, Object> coveredEdges = new THashMap();
    
    traverser=new GraphTraverser(new BytecodeNodeComparer(), new CFEdgeSelector());
    
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
                procCFG.getHighestNodeId(), fileList.length));
        }
    }//end of For
    
    procList = thHandler.getMethodList();
    //TestHistory th;
    CoverageTrace trace;
    
 /*   List<String> irregulars = CHECK_NUMBERING ?
        irregulars = new ArrayList<String>(4) : null;*/
        
    
        // Now iterate over all of the trace files, and for each match hit
        // blocks to test histories for all methods of interest.
    //int testNum = 0;
    int numFiles = fileList.length;
    for (int fileIdx = 0; fileIdx < numFiles; fileIdx++) {
       
        String traceFile =
            "/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo + File.separatorChar + fileList[fileIdx];
        if (DEBUG) {
            System.out.println("INFO: Processing \"" + fileList[fileIdx] +
                "\" as test number ");
        }
        
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
        //System.out.println(fileList[fileIdx]);
        for (int i = 0; i < procList.length; i++) {
	        String mname=procList[i];
	       // System.out.println(mname);
	        
	        //if all edges are covered, we do not need to get the trace for the method
	        Edge[] ucv;
	        ucv=UncoveredbyUAT.get(procList[i]);
	        
	        if(ucv!=null){
		        trace = (CoverageTrace) rekeyedTraces.get(mname);
		        
		         if (trace == null) {
		             // This method wasn't witnessed in the trace
		             continue;
		         }
		         ArrayList<Edge> traveredEdges=new ArrayList<Edge>();
		         
		         try{
		        	 traveredEdges=FindtraveredEdges(trace,cfHandler.getCFG(mname));
		         }catch(MethodNotFoundException e){
		        	 System.out.println(e.getMessage());
		         }
		         
		         if(traveredEdges!=null){
		 			
		 			ArrayList<Edge> saveany=new ArrayList<Edge>();
		 			Iterator il=traveredEdges.iterator();
		 			while(il.hasNext()){
		 				Edge a =(Edge)il.next();
		 				if(a.getLabel()!=null&&a.getLabel().equals("<any>")){
		 					saveany.add(a);
		 				}
		 			}
		 			Iterator ill=saveany.iterator();
		 			while(ill.hasNext()){
		 				Edge a =(Edge)ill.next();
		 				if(traveredEdges.contains(a)){
		 					traveredEdges.remove(a);
		 				}
		 			}
		 			ArrayList<Edge> refinedcovered=Findcovered(traveredEdges);//a trace
		 			refinedcovered.addAll(saveany);
		 			//System.out.println(refinedcovered.toString());
		         try{
		         	FindcoveredPredicates(trace,cfHandler.getCFG(mname), TS.elementAt(fileIdx).toString());
		         }catch(MethodNotFoundException e){
		        	 System.out.println(e.getMessage());
		         }
	        //Edge[] ucv;
	        //ucv=UncoveredbyUAT.get(procList[i]);
		         Edge[] Uncoverededges = getUncoveredEdges(refinedcovered,ucv);
		 		uncoveredforNCFG.put(mname, Uncoverededges);
        /*if(ucv!=null){
        	//System.out.println((Edge[])uncoveredforNCFG.get(mp.name));
        		Edge[] Uncoverededges = getUncoveredEdges(refinedcovered,ucv);
        		uncoveredforNCFG.put(mname, Uncoverededges);
        	}*/
         //Edge[] Uncoverededges = getUncoveredEDges(procList[i],coveredEdges,ucv);
 		//uncoveredforNCFG.put(procList[i], Uncoverededges);
        }
        }
    }//for all methods in ont trace file
    
    }//all trace files
    long endTime = System.currentTimeMillis();
    System.out.println("Time elapsed (ms): " + (endTime - startTime));
    try{
    	writeUncoveredEdges("/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/"+"newuncovered",uncoveredforNCFG);
    }catch(IOException e){
    	System.out.println(e.getMessage());
    }
    try{
    	writeHitPredicated("/home/zxu/Documents/jtcas/newoutputs/v"+version+"/"+suiteNo+"/"+"newprehit");
    }catch(IOException e){
    	System.out.println(e.getMessage());
    }
    return true;
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
/* read the file for the uncovered edges by the unaffected test cases
 * for new version
 */	
public Edge[] getUncoveredEdges(ArrayList<Edge> covered, Edge[] uncovered){
	
	for(int i=0;i<uncovered.length;i++){
		Edge uedge=uncovered[i];
		if(uedge!=null){
			Iterator il=covered.iterator();
			while(il.hasNext()){
				Edge cedge=(Edge)il.next();
				if(uedge.getPredNodeID()==cedge.getPredNodeID()&&uedge.getSuccNodeID()==cedge.getSuccNodeID()){
					uncovered[i]=null;
					break;
				}
			}
		}
	}
	
	ArrayList<Edge> unc=new ArrayList<Edge>();
	for(int j=0;j<uncovered.length;j++){
		if(uncovered[j]!=null){
			unc.add(uncovered[j]);
		}
	}
	Edge[] a;
	a=unc.toArray(new Edge[unc.size()]);
	return a;
	
}
/*read the Goalset from the thFile
 * put them into a Map Goalset
 */
public void readHitpredfile(String thFile){
	String filename_1=thFile;
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
			if(readline.charAt(0)=='0');
			else{
				int plus=readline.indexOf("+");
				String methodname=readline.substring(2, plus);
				String edges=readline.substring(plus+1);
				if(edges.equalsIgnoreCase("null")){
					ArrayList<PredicateInfor> pred=new ArrayList<PredicateInfor>();
					cvpredicates.put(methodname, pred);
				}
				else{
					ArrayList<PredicateInfor> pred=new ArrayList<PredicateInfor>();
					for(int i=0;i<edges.length();i++){
						//for each predicate
						
						String predtraces=edges.substring(edges.indexOf(":", i)+1, edges.indexOf("$", i));
						
						int nodeid=Integer.parseInt(edges.substring(i, edges.indexOf(":",i)));
						i=edges.indexOf("$",i);
						ArrayList<String> traces=new ArrayList<String>();
						if(predtraces.equals("null"));
						else{
							for(int j=0;j<predtraces.length();j++){
								String trace=predtraces.substring(j, predtraces.indexOf(",",j));
								traces.add(trace);
								j=predtraces.indexOf(",",j);
							}
						}
						PredicateInfor onepred=new PredicateInfor(nodeid,traces);
						pred.add(onepred);
						
					}
					cvpredicates.put(methodname, pred);
					
				
				}//else
				
			}//else
			
		}//while
	}catch(Exception e){
		System.out.println(e.getMessage());
	}
    


	
}
@SuppressWarnings("unchecked")
public Map<Object, Edge[]> readUncoveredEdgefile(String thFile){
	String filename_1=thFile;
	File file=null;
	FileReader filereader=null;
	LineNumberReader linereader=null;
	//int linecount=0;
	String readline=null;
	Map<Object, Edge[]> uncovered=new THashMap();
	
	
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
			if(readline.charAt(0)=='0');
			else{
				int plus=readline.indexOf("+");
				String methodname=readline.substring(2, plus);
				String edges=readline.substring(plus+1);
				if(edges.equalsIgnoreCase("null")){
					uncovered.put(methodname, null);
				}
				else{
					ArrayList<Edge> uncedges=new ArrayList<Edge>();
					for(int i=0;i<edges.length();i++){
						Edge edge=new Edge();
						if(edges.substring(i,i+1).equalsIgnoreCase(":")){
							String pre=edges.substring(i+2,edges.indexOf("-", i)-1);
							int pren=Integer.parseInt(pre);
							edge.setPredNodeID(pren);
							String suc=edges.substring(edges.indexOf(">",i)+2, edges.indexOf(",",i));
							int sucn=Integer.parseInt(suc);
							edge.setSuccNodeID(sucn);
							uncedges.add(edge);
							i=edges.indexOf(",",i);
						}//if
					}//for
					
					Edge[] a=new Edge[uncedges.size()];
					a=uncedges.toArray(new Edge[0]);
					uncovered.put(methodname, a);
				
				}//else
				
			}//else
			
		}//while
	}catch(Exception e){
		System.out.println(e.getMessage());
	}
    
	return uncovered;
}

public void FindcoveredPredicates(CoverageTrace trace1, CFG procCFG1, String trfile){
	//int[] blockhit=new int[procCFG1.getHighestNodeId()+1];
	
	 ArrayList predicatehit=cvpredicates.get(procCFG1.getMethodName());
	 if(predicatehit!=null){
		 Iterator il=predicatehit.iterator();
		 while(il.hasNext()){
			 PredicateInfor pred=(PredicateInfor)il.next();
			 if(trace1.query(pred.NodeID)){
				 pred.HitPredInfor.add(trfile);
				 
				 }
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
					//System.out.print("null$");
				}
				else{
				Iterator il2=traces.iterator();
				while(il2.hasNext()){
					pw.print(il2.next().toString());
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
      
        
        //String[] methodNames = getMethodList();
        Iterator il=Coverededges.iterator();
        while(il.hasNext()){
        	String l=il.next().toString();
        	pw.println(l);
        	//System.out.println(l);
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
	/*write the uncovered edges by affected test cases to a file
	 * 
	 */

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
        
        
        //String[] methodNames = getMethodList();
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
        		//System.out.println("null");
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

        pw.close();
         if (pw.checkError()) {
        	 throw new IOException("Error writing edge uncovered file");
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
    String tag=null,version=null,suiteNo=null,trDir=null;
    int typeFlags = 0x00000000;
    int argIndex = 1;
    List<String> classList = new ArrayList<String>();
        // if no arguments given, prompt for test dir name
        if (argv.length < argIndex ) {
            System.out.print("Enter test file name: ");
            try{
            trDir = stdin.readLine();
            }catch(IOException e){
            	System.err.println(e.getMessage());
            	System.exit(1);
            }
        }
        else{
        	version=argv[0];
        	suiteNo=argv[1];
        	tag="concolic"+version;
        }
        List<ProgramUnit> unitList=new ArrayList<ProgramUnit>();
        try{
        	Handler.readProgFile("concolic.prog", "concolic"+version, unitList);
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
    
    EdgesNCoveredbyAllOldTest thBuilder = new EdgesNCoveredbyAllOldTest(typeFlags, tag);
    
    //@SuppressWarnings("unused")
    //long startTime = System.currentTimeMillis();
    
    if (!thBuilder.buildNTestHistory(version,suiteNo,classList)) {
        System.err.println("Test history construction failed");
        System.exit(1);
    }
    
    //@SuppressWarnings("unused")
    //long endTime = System.currentTimeMillis();
    //System.out.println("Time elapsed (ms): " + (endTime - startTime));
}

}

