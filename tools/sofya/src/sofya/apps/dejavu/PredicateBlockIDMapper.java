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
import sofya.graphs.cfg.Block;
import sofya.graphs.cfg.CFG;
import sofya.graphs.cfg.CFGCacheFactory;
import sofya.graphs.cfg.CFHandler;
import sofya.tools.TestHistoryBuilder;
import sofya.apps.dejavu.EdgesNCoveredbyUnAffectedTest;
import sofya.tools.th.TestHistory;
import sofya.tools.th.TestHistoryHandler;

public class PredicateBlockIDMapper {
private static final BufferedReader stdin = new BufferedReader(
            new InputStreamReader(
                System.in));
//private InputParser parser;
private GraphTraverser traverser;
private PrintStream stderr = System.err;
private String tag=null;
private int typeFlags = 0x00000000;
private CFHandler cfHandler;
private static final boolean CHECK_NUMBERING = true;
private static final boolean DEBUG = false;
//private TestMapper testmapper;
Map<String, Vector> preinfor=new THashMap();
Map<String, Vector<PreBlock>> preblockinfor=new THashMap();
public PredicateBlockIDMapper(int typeFlags,String tag){
this.typeFlags=typeFlags;
this.tag=tag;
 
}
public boolean buildNTestHistory(String thFile, List<String> classList, String preblockFile) {
if ((thFile==null)|| (classList == null)) {
        throw new IllegalArgumentException("Required parameter is null");
    }
readPreInfor(thFile);
GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
   TraceHandler trHandler = new TraceHandler();
   typeFlags |= BlockType.MASK_BASIC;//this should be an input, change later
   TestHistoryHandler thHandler = new TestHistoryHandler(typeFlags);
   Map<Object, Object> nameSigMap = new THashMap();
   cfHandler = new CFHandler(procCFGs);
   //Map<Object, Object> coveredEdges = new THashMap();
   
   traverser=new GraphTraverser(new BytecodeNodeComparer(), new CFEdgeSelector());
   cfHandler.setClearOnLoad(true);
   
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
        int ifcounter=0;
        Vector<PreBlock> SavePreM=new Vector<PreBlock>();
       
           procCFG = (CFG) procs.next();
           MethodSignature mSig = procCFG.getSignature();
           if (mSig == null) {
               stderr.println("Cannot operate on legacy control flow " +
                   "files");
               return false;
           }
           
           // For now, traces still don't use signatures
           //String mName = procCFG.getMethodName();
           /*if(!mName.substring(mName.indexOf('('), mName.indexOf(')')).equals("(")){
            String inside=mName.substring(mName.indexOf('(')+1, mName.indexOf(')'));
            inside=inside.replace(",_", ",");
            Vector<String> pa=new Vector<String>();
            for(int in=0;in<inside.length();in++){
            if(inside.substring(in).contains(",")){
            String sub=inside.substring(in, inside.indexOf(",",in));
            //sub=sub.replaceFirst("_", " ");
            sub=sub.substring(0, sub.indexOf("_"));
            pa.add(sub);
            in=inside.indexOf(",",in);
            }
            else{
            String sub=inside.substring(in);
            sub=sub.substring(0, sub.indexOf("_"));
            //sub=sub.replaceFirst("_", " ");
            pa.add(sub);
            in=inside.length();
            }
            }
            Iterator il=pa.iterator();
            String reinside="";
            while(il.hasNext()){
            reinside=reinside+(String)il.next()+",";
            }
            reinside=reinside.substring(0, reinside.lastIndexOf(','));
            mName=mName.substring(0, mName.indexOf('('))+"("+reinside+")";
           }*/
          // nameSigMap.put(mName, mSig);
           Vector<String> pre=preinfor.get(mSig.toString());
           Block[] blo=procCFG.getBasicBlocks();
           for(int b=0;b<blo.length;b++){
            //System.out.println(blo[b].getSubType());
            if(blo[b].getSubType().toString().equals("if")){
            ifcounter++;
            //System.out.println(mSig+":if:"+blo[b]);
            PreBlock p=new PreBlock(pre.remove(0),blo[b].getID(),ifcounter,"if");
            SavePreM.add(p);
            }
           }
           preblockinfor.put(mSig.toString(), SavePreM);

       }
   }
   try{
    writePreInfor(preblockFile);//end of For
   }catch(IOException e){
    System.out.println(e.getMessage());
   }
   return true;
}
private void writePreInfor(String preblockFile) throws IOException{
// TODO Auto-generated method stub
PrintWriter pw = new PrintWriter(
               new BufferedWriter(
               new OutputStreamWriter(
                   openOutputFile(preblockFile, false))), true);
// TODO Auto-generated method stub


// Edges covered information
       pw.println("0 Predicated information in each method");
       pw.println("0 File: " + preblockFile + "  Created: " +
          new Date(System.currentTimeMillis()));
       pw.println("0 Version: " + ProjectDescription.versionString);

       Set<Entry<String, Vector<PreBlock>>> hp;
       //String[] methodNames = getMethodList();
       hp=preblockinfor.entrySet();
       Iterator il=hp.iterator();
       while(il.hasNext()){
        Entry<String, Vector<PreBlock>> l;
l=(Entry)il.next();
pw.println("1 "+l.getKey());
//System.out.println("1 "+l.getKey());
Vector<PreBlock> pb = l.getValue();
Iterator il1=pb.iterator();
while(il1.hasNext()){
PreBlock pre=(PreBlock)il1.next();
pw.println("2 "+pre.BID+"$"+pre.preorder+"$"+pre.pre);
//System.out.println("2 "+pre.BID+"$"+pre.preorder+"$"+pre.pre);
}
       
       }
       pw.close();
        if (pw.checkError()) {
        throw new IOException("Error writing predicate information file");
        }
}
private void readPreInfor(String thFile1) {
// TODO Auto-generated method stub
String filename_1=thFile1;
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
String methodname=readline.substring(3, plus-1);
methodname=methodname.replace(": ", ".");
methodname=methodname.replace(' ', '_');
String preSet=readline.substring(plus+1);
if(preSet.equalsIgnoreCase("null")){
preinfor.put(methodname, null);
}
else{
Vector<String> pres=new Vector<String>();
for(int i=0;i<preSet.length();i++){
String pre=preSet.substring(i, preSet.indexOf('#',i));
pres.add(pre);
i=preSet.indexOf('#',i);
}//for
preinfor.put(methodname, pres);

}//else
}//else
}//while
}catch(Exception e){
System.out.println(e.getMessage());
}//try
   
}
public static final FileOutputStream openOutputFile(
            String fileName, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileOutputStream(fileName, append);
    }
public static void main(String[] argv) {
   String tag=null, thFile=null, newFile="prdblkmap";
   int typeFlags = 0x00000000;
   int argIndex = 1;
   int version=0;
   List<String> classList = new ArrayList<String>();
       // if no arguments given, prompt for test dir name
       if (argv.length < argIndex ) {
           System.out.print("Enter version name: ");
           
       }
       else{
        version =Integer.parseInt(argv[0]);
        tag="concolic"+version;
        thFile="/home/zxu/Documents/jtcas/versions.alt/versions.orig/v"+version+"/preinfor";
        newFile="/home/zxu/Documents/jtcas/versions.alt/versions.orig/v"+version+"/"+newFile;
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
   
       PredicateBlockIDMapper thBuilder = new PredicateBlockIDMapper(typeFlags, tag);
   
   //@SuppressWarnings("unused")
   //long startTime = System.currentTimeMillis();
       long startTime = System.currentTimeMillis();
   if (!thBuilder.buildNTestHistory(thFile,classList, newFile)) {
       System.err.println("Predicate-BlockID construction failed");
       System.exit(1);
   }
   long endTime = System.currentTimeMillis();
	System.out.println("Time elapsed (ms): " + (endTime - startTime));
   //@SuppressWarnings("unused")
   //long endTime = System.currentTimeMillis();
   //System.out.println("Time elapsed (ms): " + (endTime - startTime));
}

}