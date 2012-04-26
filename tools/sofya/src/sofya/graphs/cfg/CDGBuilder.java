package sofya.graphs.cfg;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import sofya.base.Handler;
import sofya.base.MethodSignature;
import sofya.base.ProgramUnit;
import sofya.base.exceptions.BadFileFormatException;
import sofya.base.exceptions.EmptyFileException;
import sofya.graphs.Edge;
import sofya.graphs.GraphCache;
import sofya.graphs.Node;

public class CDGBuilder {
	
	private CFHandler cfHandler;
	private PrintStream stderr = System.err;
	public CFG cfg;
	public CDGBuilder(){
		
	}
	private boolean builderRCFG(List<String> classList, String tag) {
		// TODO Auto-generated method stub
		String dir="/home/zxu/Documents/one_tcas";
		int size = classList.size();
		String className=null;
		
		CFG procCFG=null;
		GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
		cfHandler = new CFHandler(procCFGs);
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
	            
	            CFG acfg=new CFG();
	            
	            String methodname=procCFG.getMethodName();
	            if(!methodname.contains("main")){
	            	continue;
	            	
	            }
	            MethodSignature mSig = procCFG.getSignature();
	            
	            if (mSig == null) {
	                stderr.println("Cannot operate on legacy control flow " +
	                    "files"); 
	                return false;
	            }
	            
	   

	            Block[] blocks=procCFG.getBasicBlocks();
	           // printpredicate(blocks);
	            Edge[] edges=new Edge[0];
	            
	            edges=procCFG.getEdges(edges);
	            
	            for(int j=0;j<blocks.length;j++){
	            	//System.out.println(blocks[j].getSubType());
	            	//System.out.println(blocks[j].getType());
	            	//System.out.println(blocks[j].getPredecessorCount());
	            	if(blocks[j].getType().toString().equals("exit")&&
	            			(blocks[j].getSubType().toString().equals("sumthrow")||blocks[j].getSubType().toString().equals("throw"))){
	            		continue;
	            	}
	            	acfg.addBlock(blocks[j]);
	            }
	            int finalid=acfg.getBasicBlocks()[acfg.getBasicBlocks().length-1].getID();
	            Block start=new Block();
	            start.setID(finalid+1);
	            acfg.addBlock(start);
	            
	            
	            for(int k=0;k<edges.length;k++){
	            	
	            	if(edges[k].getLabel()!=null&&edges[k].getLabel().equals("<any>")){
	            		
	            		continue;
	            		
	            	}
	            	acfg.addEdge(edges[k]);
	            	
	            }
	            
	            Edge[] aedges=new Edge[0];
	            aedges=acfg.getEdges(aedges);
	            
	            int edgefinalid=aedges[aedges.length-1].getID();
	            Edge one= new Edge();
	            one.setID(edgefinalid+1);
	            one.setPredNodeID(finalid+1);
	            one.setSuccNodeID(1);
	            one.setLabel("T");
	            acfg.addEdge(one);
	            
	            Edge two=new Edge();
	            two.setID(edgefinalid+2);
	            two.setPredNodeID(finalid+1);
	            two.setSuccNodeID(finalid);
	            two.setLabel("F");
	            acfg.addEdge(two);
	            
	            
	            CFG rcfg=new CFG();
	            Block[] blockacfg=acfg.getBasicBlocks();
	            for(int bi=0;bi<blockacfg.length;bi++){
	            	rcfg.addBlock(blockacfg[bi]);
	            }
	            
	            Edge[] at=new Edge[0];
	            at=acfg.getEdges(at);
	
	            for(int ei=0;ei<at.length;ei++){
	                Edge a=at[ei];
	                Edge newa=new Edge();
	            	int pre=at[ei].getSuccNodeID();
	            	int succ=at[ei].getPredNodeID();
	            	newa.setPredNodeID(pre);
	            	newa.setSuccNodeID(succ);
	            	rcfg.addEdge(newa);
	            }

	            DominatorTreeBuilder dbuilder=new DominatorTreeBuilder();
	            dbuilder.acfg=acfg;
	            dbuilder.rcfg=rcfg;
	            dbuilder.builder();
	            CDG cdg=createCDG(dbuilder.abl, acfg);
	           
	            /*try{
	            writeCDG(cdg,methodname, dir);
	            }catch(IOException e){
	            	System.out.println(e.getMessage());
	            }*/
	        }
	    }
	   return true; 
	}
	private void printpredicate(Block[] blocks) {
		// TODO Auto-generated method stub
		for(int i=0;i<blocks.length;i++){
			if(blocks[i].getLabel().toString().equals("if")||blocks[i].getSubType().toString().equals("if")||blocks[i].getType().toString().equals("if")){
				System.out.println(blocks[i].getID());
			}
		}
	}
	private void writeCDG(CDG cdg, String methodname, String dir) throws IOException{
		// TODO Auto-generated method stub
		String file=dir+"/CDG";
		PrintWriter pw = new PrintWriter(
	            new BufferedWriter(
	            new OutputStreamWriter(
	                openOutputFile(file, false))), true);
		Edge[] cdgedges=new Edge[0];
		cdgedges=cdg.getEdges(cdgedges);
		pw.println("0 "+methodname);
		pw.println("0 control dependency graph");
		for(int i=0;i<cdgedges.length;i++){
			Edge a = cdgedges[i];
			pw.println("1 "+a.getPredNodeID()+" "+a.getSuccNodeID()+" "+a.getLabel());
			
		}
		pw.close();
		if (pw.checkError()) {
		throw new IOException("Error writing .ys file");
		}
	}
	public static final FileOutputStream openOutputFile(
            String fileName, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileOutputStream(fileName, append);
    }
	private CDG createCDG(Vector<Vector> abl, CFG acfg) {
		// TODO Auto-generated method stub
		CDG cdg=new CDG();
		
		ArrayList<Edge> edges=new ArrayList<Edge>();
		
		Node[] nodes=acfg.getBasicBlocks();
		int exitnode=0;
		for(int i=0;i<nodes.length;i++){
			//System.out.println(acfg.getBlock(nodes[i].getID()).getLabel().toString());
			if(!acfg.getBlock(nodes[i].getID()).getLabel().toString().equals("X")){
				
				cdg.addNode(nodes[i]);
			}
			else{
				exitnode=nodes[i].getID();
			}
		}
		
		Iterator il=abl.iterator();
		while(il.hasNext()){
			Vector oneabl=(Vector)il.next();
			int begin=4;
			int A=(Integer)oneabl.elementAt(0);
			int B=(Integer)oneabl.elementAt(1);
			int L=(Integer)oneabl.elementAt(2);
			if(A==exitnode){
				continue;
			}
			//if the L is the exit node, continue;
			String label="";
			if((Integer)oneabl.elementAt(3)==10000){
				label="T";
			}
			else{
				label="F";
			}
			Edge a =new Edge();
			a.setPredNodeID(A);
			a.setSuccNodeID(B);
			a.setLabel(label);
			cdg.addEdge(a);
			//edges.add(a);
			if(Integer.parseInt(oneabl.lastElement().toString())==-99999){
				Edge c =new Edge();
				c.setPredNodeID(A);
				c.setSuccNodeID(A);
				c.setLabel(label);
				cdg.addEdge(c);
			
				for(int i=begin;i<oneabl.size()-1;i++){
					Edge b =new Edge();
					b.setSuccNodeID((Integer)oneabl.elementAt(i));
					b.setPredNodeID(A);
					b.setLabel(label);
					cdg.addEdge(b);
					//edges.add(a);
				}
			}
			else{
				for(int i=begin;i<oneabl.size();i++){
					Edge b =new Edge();
					b.setSuccNodeID((Integer)oneabl.elementAt(i));
					b.setPredNodeID(A);
					b.setLabel(label);
					cdg.addEdge(b);
					//edges.add(a);
				}
			}
			
		}
		return cdg;
	}
	public static void main(String[] argv) {
		 String tag=null,prog=null;
		 tag=argv[0];
		 prog=argv[1];
		    int typeFlags = 0x00000000;
		    List<String> classList = new ArrayList<String>();
		    List<ProgramUnit> unitList=new ArrayList<ProgramUnit>();
	        try{
	        	Handler.readProgFile(prog, tag, unitList);
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
	        CDGBuilder rcfg=new CDGBuilder();
	       boolean suc=rcfg.builderRCFG(classList,tag);
	        
	}
	public CDG returnCDG(List<String> classList, String tag) {
		// TODO Auto-generated method stub
		
		int size = classList.size();
		String className=null;
		
		CFG procCFG=null;
		GraphCache<CFG> procCFGs = CFGCacheFactory.createCache();
		cfHandler = new CFHandler(procCFGs);
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
	            return null;
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
	            return null;
	        }

	        Iterator procs = procCFGs.iterator(className);
	        while (procs.hasNext()) {
	        	
	            procCFG = (CFG) procs.next();
	            
	            CFG acfg=new CFG();
	            
	            String methodname=procCFG.getMethodName();
	            if(!methodname.contains("main")){
	            	continue;
	            }
	            MethodSignature mSig = procCFG.getSignature();
	            
	            if (mSig == null) {
	                stderr.println("Cannot operate on legacy control flow " +
	                    "files"); 
	                return null;
	            }
	            
	   
	            cfg=procCFG;
	            Block[] blocks=procCFG.getBasicBlocks();
	            
	            Edge[] edges=new Edge[0];
	            
	            edges=procCFG.getEdges(edges);
	            
	            for(int j=0;j<blocks.length;j++){
	            	//System.out.println(blocks[j].getSubType());
	            	//System.out.println(blocks[j].getType());
	            	//System.out.println(blocks[j].getPredecessorCount());
	            	if(blocks[j].getType().toString().equals("exit")&&
	            			(blocks[j].getSubType().toString().equals("sumthrow")||blocks[j].getSubType().toString().equals("throw"))){
	            		continue;
	            	}
	            	acfg.addBlock(blocks[j]);
	            }
	            int finalid=acfg.getBasicBlocks()[acfg.getBasicBlocks().length-1].getID();
	            Block start=new Block();
	            start.setID(finalid+1);
	            acfg.addBlock(start);
	            
	            
	            for(int k=0;k<edges.length;k++){
	            	
	            	if(edges[k].getLabel()!=null&&edges[k].getLabel().equals("<any>")){
	            		
	            		continue;
	            		
	            	}
	            	acfg.addEdge(edges[k]);
	            	
	            }
	            
	            Edge[] aedges=new Edge[0];
	            aedges=acfg.getEdges(aedges);
	            
	            int edgefinalid=aedges[aedges.length-1].getID();
	            Edge one= new Edge();
	            one.setID(edgefinalid+1);
	            one.setPredNodeID(finalid+1);
	            one.setSuccNodeID(1);
	            one.setLabel("T");
	            acfg.addEdge(one);
	            
	            Edge two=new Edge();
	            two.setID(edgefinalid+2);
	            two.setPredNodeID(finalid+1);
	            two.setSuccNodeID(finalid);
	            two.setLabel("F");
	            acfg.addEdge(two);
	            
	            
	            CFG rcfg=new CFG();
	            Block[] blockacfg=acfg.getBasicBlocks();
	            for(int bi=0;bi<blockacfg.length;bi++){
	            	rcfg.addBlock(blockacfg[bi]);
	            }
	            
	            Edge[] at=new Edge[0];
	            at=acfg.getEdges(at);
	
	            for(int ei=0;ei<at.length;ei++){
	                Edge a=at[ei];
	                Edge newa=new Edge();
	            	int pre=at[ei].getSuccNodeID();
	            	int succ=at[ei].getPredNodeID();
	            	newa.setPredNodeID(pre);
	            	newa.setSuccNodeID(succ);
	            	rcfg.addEdge(newa);
	            }

	            DominatorTreeBuilder dbuilder=new DominatorTreeBuilder();
	            dbuilder.acfg=acfg;
	            dbuilder.rcfg=rcfg;
	            dbuilder.builder();
	            CDG cdg=createCDG(dbuilder.abl, acfg);
	           return cdg;
	            /*try{
	            writeCDG(cdg,methodname, dir);
	            }catch(IOException e){
	            	System.out.println(e.getMessage());
	            }*/
	        }
	    }
	   return null; 
	}

}
