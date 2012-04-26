package sofya.apps.dejavu;
import static sofya.base.Utility.isParsableInt;
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
import sofya.base.Utility.IntegerPtr;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.EmptyFileException;
import sofya.base.exceptions.MethodNotFoundException;
import sofya.ed.structural.CoverageTrace;
import sofya.ed.structural.TraceHandler;
import sofya.graphs.Edge;
import sofya.graphs.Graph;
import sofya.graphs.GraphCache;
import sofya.graphs.Node;
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFGCacheFactory;
import sofya.graphs.cfg.CFHandler;
import sofya.tools.TestHistoryBuilder;
import sofya.apps.dejavu.EdgesNCoveredbyUnAffectedTest;
//import sofya.apps.dejavu.EdgesCoveredByAffectedTest.NumericStringComparator;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;

@SuppressWarnings({ "unchecked", "unchecked" })
public class TestCasesGenerator {
	private static final BufferedReader stdin = new BufferedReader(
            new InputStreamReader(
                System.in));

	private PrintStream stderr = System.err;
	private String tag=null;
	private int typeFlags = 0x00000000;
	private CFHandler cfHandler;
	
	Vector<EdgeInfor> OrderedGoalset=new Vector<EdgeInfor>();
	Map<Object, Edge[]> uncoveredforNCFG=new THashMap();
	Map<Object, ArrayList<PredicateInfor>> cvpredicates=new THashMap();
	Vector MethodCallinfor=new Vector();
	
	public TestCasesGenerator(int typeFlags,String tag){
		 this.typeFlags=typeFlags;
		 this.tag=tag;
	}

	public boolean generateTestCases(List<String> classList, String newUncoverFile, String HitPredFile, String MethodCall) {
		if ((newUncoverFile == null) ||(classList == null)||(HitPredFile==null)||(MethodCall==null)) {
			throw new IllegalArgumentException("Required parameter is null");
	}
		Map<Object, Edge[]> Goalset=new THashMap();
		Map<Object, CFG> NCFG=new THashMap();
		
		readMethodcallfile(MethodCall);
		
		readHitpredfile(HitPredFile);
		
	    Goalset=readUncoveredEdgefile(newUncoverFile);
	    
	    //Set<Entry<Object,Edge[]>> goal=Goalset.entrySet();
	    GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
	    typeFlags |= BlockType.MASK_BASIC;
	    cfHandler = new CFHandler(procCFGs);
	    cfHandler.setClearOnLoad(true);
	
	    CFG procCFG = null;
	    String className;
	
	    int size = MethodCallinfor.size();
	    
	    Iterator iterator = MethodCallinfor.iterator();
	
	    for (int i = size; i-- > 0; ) {
	    	String whole=(String)iterator.next();
	    	String methodname=whole.substring(whole.indexOf(':')+2);
	        className = whole.substring(0, whole.indexOf(':'));
	        
	        
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
	            if(procCFG.getMethodName().equalsIgnoreCase(methodname)){
	            	Edge[] alluedges=new Edge[0];
	                NCFG.put(procCFG.getMethodName(), procCFG);
	                //if this method is an old method in this class
	                alluedges=Goalset.get(procCFG.getMethodName());//get all the uncovered edges
	                if(alluedges.length==0);
	                else{
	                	Vector hitpred=new Vector();
	    	            ArrayList<PredicateInfor> hitpredinfor=cvpredicates.get(procCFG.getMethodName());
	    	            Iterator il=hitpredinfor.iterator();
	    	            while(il.hasNext()){
	    	            	hitpred.add(((PredicateInfor)il.next()).NodeID);
	    	            }
	    	            Vector<Edge> edgehitpre=new Vector<Edge>();
	    	            Vector<Edge> edgenothitpre=new Vector<Edge>();
	    	            
	    	            for(int j=0;j<alluedges.length;j++){
	    	            	int pre=alluedges[j].getPredNodeID();
	    	            	if(hitpred.contains(pre)){
	    	            		edgehitpre.add(alluedges[j]);
	    	            	}
	    	            	else{
	    	            		edgenothitpre.add(alluedges[j]);
	    	            	}
	    	            }
	    	            if(!edgehitpre.isEmpty()){
	    	            	Edge willcover=edgehitpre.firstElement();
	    	            	Iterator il1=hitpredinfor.iterator();
	    	            	ArrayList existTC=new ArrayList();
	    	            	while(il1.hasNext()){
	    	            		PredicateInfor tem=(PredicateInfor)il1.next();
	    	            		if(tem.NodeID==willcover.getPredNodeID()){
	    	            			existTC=tem.HitPredInfor;
	    	            			break;
	    	            		}
	    	            	}
	    	            	String testcase=existTC.get(1).toString();
	    	            	//instrument program again
	    	            	System.out.println("Please instrument program and run test case："+testcase);
	    	            	/*run the test case, get the constraint.   	           
	    	            	 * delete all the predicates after the edge's prenode,
	    	            	 * and negate the prenode's condition
	    	            	 */
	    	            	System.out.println("Delete the predicates after predicate");
	    	            }
	                }
	            }//if it is the method we want
	           
	        }
	    }
	
	    return true;       
	           
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
   /* int size = classList.size();
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
            Edge[] alluedges=new Edge[0];
            NCFG.put(procCFG.getMethodName(), procCFG);
            //if this method is an old method in this class
            alluedges=Goalset.get(procCFG.getMethodName());//get all the uncovered edges
            if(alluedges.length==0);
            else{
	            Vector hitpred=new Vector();
	            ArrayList<PredicateInfor> hitpredinfor=cvpredicates.get(procCFG.getMethodName());
	            Iterator il=hitpredinfor.iterator();
	            while(il.hasNext()){
	            	hitpred.add(((PredicateInfor)il.next()).NodeID);
	            }
	            Vector<Edge> edgehitpre=new Vector<Edge>();
	            Vector<Edge> edgenothitpre=new Vector<Edge>();
	            
	            for(int j=0;j<alluedges.length;j++){
	            	int pre=alluedges[j].getPredNodeID();
	            	if(hitpred.contains(pre)){
	            		edgehitpre.add(alluedges[j]);
	            	}
	            	else{
	            		edgenothitpre.add(alluedges[j]);
	            	}
	            }
	            while(!edgehitpre.isEmpty()){
	            	Edge willcover=edgehitpre.firstElement();
	            	//do concolic testing for it.
	            	Iterator il1=hitpredinfor.iterator();
	            	ArrayList existTC=new ArrayList();
	            	while(il1.hasNext()){
	            		PredicateInfor tem=(PredicateInfor)il1.next();
	            		if(tem.NodeID==willcover.getPredNodeID()){
	            			existTC=tem.HitPredInfor;
	            			break;
	            		}
	            	}
	            	String testcase=existTC.get(1).toString();
	            	run the test case, get the constraint.   	           
	            	 * delete all the predicates after the edge's prenode,
	            	 * and negate the prenode's condition
	            	 
	            	}
            }//end of else
            
        }//end of while of all the CFGs in the new version of one class.
        
    }//end of For of all classes in the new version.
  return true;  */
	}
	private void readMethodcallfile(String MethodCall) {
		String filename_1=MethodCall;
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
				if(readline.startsWith("0"));
				else{
					String method=readline.substring(readline.indexOf("0:<")+3, readline.indexOf('>'));
					MethodCallinfor.add(method);
					
				}
			}//while
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
	    
		
	}
	
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
									String trace=predtraces.substring(0, predtraces.indexOf(","));
									traces.add(trace);
									j=predtraces.indexOf(",",j)+1;
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
	/*read the Goalset from the thFile
	 * put them into a Map Goalset
	 */
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
	    Iterator ill=MethodCallinfor.iterator();
	    while(ill.hasNext()){
	    	String name=(String)ill.next();
	    	Edge[] a=uncovered.get(name);
	    	for(int i=0;i<a.length;i++){
	    		EdgeInfor edge=new EdgeInfor();
	    		edge.classname=name.substring(0, name.indexOf(':'));
	    		edge.methodname=name;
	    		edge.prenode=a[i].getPredNodeID();
	    		edge.sucnode=a[i].getSuccNodeID();
	    		OrderedGoalset.add(edge);
	    	}
	    	
	    }
		return uncovered;
	}
	
	public static void main(String[] argv) {
	    String tag="concolic2",thFile1=null, thFile2=null,thFile3=null;
	    int typeFlags = 0x00000000;
	    int argIndex = 1;
	    List<String> classList = new ArrayList<String>();
	        // if no arguments given, prompt for test dir name
	        if (argv.length < argIndex ) {
	            System.out.print("Enter test file name: ");
	            try{
	            thFile1 = stdin.readLine();
	            }catch(IOException e){
	            	System.err.println(e.getMessage());
	            	System.exit(1);
	            }
	        }
	        else{
	        	thFile1=argv[0];
	        	thFile2=argv[1];
	        	thFile3=argv[2];
	        	
	        }
	        List<ProgramUnit> unitList=new ArrayList<ProgramUnit>();
	        try{
	        	Handler.readProgFile("concolic2.prog", "concolic2", unitList);
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
	    
	        TestCasesGenerator thBuilder = new TestCasesGenerator(typeFlags, tag);
	    
	    //@SuppressWarnings("unused")
	    //long startTime = System.currentTimeMillis();
	    
	    if (!thBuilder.generateTestCases(classList, thFile1, thFile2,thFile3)) {
	        System.err.println("Test history construction failed");
	        System.exit(1);
	    }
	    
	    //@SuppressWarnings("unused")
	    //long endTime = System.currentTimeMillis();
	    //System.out.println("Time elapsed (ms): " + (endTime - startTime));
	}
}