package sofya.graphs.cfg;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import sofya.graphs.Edge;
import sofya.graphs.Node;
/*Since we add the start node at the end, so we need to begin from the send-to-last one which
 * is the exit node.
 * 
 */
public class DominatorTreeBuilder {
	CFG rcfg;
	CFG acfg;
	HashSet<Node>[] Dominator;
	HashSet<Node>[] Out;
	HashSet<Node>[] In;
	HashSet<Edge> pdedges=new HashSet<Edge>();
	HashSet<Edge> S=new HashSet<Edge>();
	Vector<Vector> abl=new Vector<Vector>();
	
	//Vector<Edge> edges=new Vector<Edge>();
	public void builder(){
		
		Node[] nodes=rcfg.getBasicBlocks();

		Out=new HashSet[nodes.length+1];
		In=new HashSet[nodes.length+1];
		Dominator=new HashSet[nodes.length+1];
		/*Start from the second-to-last node which is exit node in original cfg
		 * 
		 */
		for(int i=nodes.length-2;i>=0;i--){
			if(i==nodes.length-2){
				HashSet<Node> vnode=new HashSet<Node>();
				vnode.add(nodes[i]);
				int id=nodes[i].getID();
				Out[id]=new HashSet();
				Out[id].addAll(vnode);
				
			}
			else{
				int id=nodes[i].getID();
				Out[id]=new HashSet();
				
				HashSet<Node> vnode=new HashSet<Node>();
				vnode=returnallNodes(nodes);
				
				Out[id].addAll(vnode);
			}
				
		}
		//the following is for the start node.
		int id=nodes[nodes.length-1].getID();
		Out[id]=new HashSet();
		HashSet<Node> vnode=new HashSet<Node>();
		vnode=returnallNodes(nodes);
		Out[id].addAll(vnode);
		
		
		/*Start from the second-to-last node which is exit node in original cfg
		 * calculate the Dominator
		 *
		 */
		HashSet<Node> in=new HashSet<Node>();
		in.add(nodes[nodes.length-2]);
		Dominator[nodes[nodes.length-2].getID()]=in;
		boolean ifchange=true;
		
		while(ifchange){
			ifchange=false;
			
			for(int i=nodes.length-3;i>=0;i--){
				HashSet<Node> oldout=new HashSet<Node>(Out[nodes[i].getID()]);
				Node[] pres=nodes[i].getPredecessors();
				pres=refinpre(pres,nodes[i]);
				
				in=new HashSet<Node>();
	
				for(int j=0;j<pres.length;j++){
					if(j==0){
						in.addAll(    Out[pres[j].getID()]);
					}
					else{
						in.retainAll(Out[pres[j].getID()]);
					}
				}
				In[nodes[i].getID()]=in;
				Dominator[nodes[i].getID()]= in;
				HashSet<Node> newin=new HashSet<Node>(in);
				newin.add(nodes[i]);
				HashSet<Node> newout=new HashSet<Node>();
				Out[nodes[i].getID()]=newin;
				if(!oldout.equals(newin)){
					ifchange=true;
				}
				//Dominator[nodes[i].getID()]= in;
			}
		}
		
		
		//for the start node
		Node[] pres=nodes[nodes.length-1].getPredecessors();
		pres=refinpre(pres,nodes[nodes.length-1]);

		in=new HashSet<Node>();

		for(int j=0;j<pres.length;j++){
			if(j==0){
				in.addAll(Out[pres[j].getID()]);
			}
			else{
				in.retainAll(Out[pres[j].getID()]);
			}
		}
		In[nodes[nodes.length-1].getID()]=in;
		Dominator[nodes[nodes.length-1].getID()]= in;
		HashSet<Node> newin=new HashSet<Node>(in);
		newin.add(nodes[nodes.length-1]);
		Out[nodes[nodes.length-1].getID()]=newin;
		
		int startid=nodes[nodes.length-2].getID();
		Dominator[startid].remove(nodes[nodes.length-2]);
		
		PostDominatorTreeBuilder();
		try{
			printoutIPD();
		}catch(IOException e){
			System.out.println(e.getStackTrace());
		}
		FindSetS();
		CommonAncestorL();
		MarkNodes();
	}

	private void printoutIPD() throws IOException{
		// TODO Auto-generated method stub
		String fileName="/home/zxu/Documents/one_tcas_v1/IPD";
		PrintWriter pw = new PrintWriter(
                new BufferedWriter(
                new OutputStreamWriter(
                    openOutputFile(fileName, false))), true);
		
		Node[] nodes=acfg.getBasicBlocks();
		int[] ipds=new int[nodes.length+1];
		for(int i=0;i<nodes.length;i++){
			Node onenode=nodes[i];
			int nodeid=onenode.getID();
			int ipd=acfg.getHighestNodeId();
			Iterator il=pdedges.iterator();
			while(il.hasNext()){
				Edge edge=(Edge)il.next();
				if(edge.getSuccNodeID()==nodeid){
					if(ipd>edge.getPredNodeID()){
						ipd=edge.getPredNodeID();
					}
				}
			}
			ipds[nodeid]=ipd;
		}
		
		for(int i=1;i<ipds.length;i++){
			pw.println(i+"."+ipds[i]);
		}
		
		
	}
	public static final FileOutputStream openOutputFile(
            String fileName, boolean append) throws IOException {
        if ((fileName == null) || (fileName.length() == 0)) {
            throw new FileNotFoundException("File name is empty or null");
        }
        return new FileOutputStream(fileName, append);
    }
	private Node[] refinpre(Node[] pres, Node node) {
		// TODO Auto-generated method stub
		ArrayList<Node> presnew=new ArrayList<Node>();
		for(int i=0;i<pres.length;i++){
			int pre=pres[i].getID();
			int suc=node.getID();
			if(pre==suc){
				continue;
			}else{
				if(findedge(pre,suc)){
					presnew.add(pres[i]);
				}
			}
		}
		Node[] p=new Node[0];
		return presnew.toArray(p);
	}

	private boolean findedge(int pre, int suc) {
		// TODO Auto-generated method stub
		Edge[] edges=new Edge[0];
		edges=rcfg.getEdges(edges);
		for(int i=0;i<edges.length;i++){
			if(edges[i].getPredNodeID()==pre&&edges[i].getSuccNodeID()==suc){
				return true;
			}
		}
		
		return false;
	}

	private void MarkNodes() {
		// TODO Auto-generated method stub
		Iterator il=abl.iterator();
		while(il.hasNext()){
			Vector pair=(Vector)il.next();
			int A=(Integer)pair.elementAt(0);
			int B=(Integer)pair.elementAt(1);
			int L=(Integer)pair.elementAt(2);
			
			int eachone;
			eachone=FindPredecessor(B);
			while(eachone!=L){
				pair.add(eachone);//add this node into a, b, l
				eachone=FindPredecessor(eachone);
				
			}
			if(eachone==A){
				pair.add(-99999);
			}
		}
	}

	private void CommonAncestorL() {
		// TODO Auto-generated method stub
		//A's ancestor is B's ancestor?
		Iterator il=S.iterator();
		while(il.hasNext()){
			
			Edge se=(Edge)il.next();
			int A=se.getPredNodeID();
			int B=se.getSuccNodeID();
			String label=se.getLabel();
			
			if(isAncestor(A,B)){
				//System.out.println("Edge:"+A+","+B+":"+A);
				Vector v=new Vector();
				v.add(A);
				v.add(B);
				v.add(A);
				if(label.equals("T")){
					v.add(10000);
				}
				else{
					v.add(-10000);
				}
				abl.add(v);
			}
			
			else{
				int f=FindPredecessor(A);		
				
				while(true){	
					if(isAncestor(f,B)){
						//System.out.println("Edge:"+A+","+B+":"+f);
						Vector v=new Vector();
						v.add(A);
						v.add(B);
						v.add(f);

						if(label.endsWith("T")){
							v.add(10000);
						}
						else{
							v.add(-10000);
						}
						abl.add(v);
						break;
					}
					else{
						f=FindPredecessor(f);
					}
				}
			}
		}
	}

	private int FindPredecessor(int a) {
		// TODO Auto-generated method stub
		Iterator il=pdedges.iterator();
		while(il.hasNext()){
			Edge e=(Edge)il.next();
			if(e.getSuccNodeID()==a){
				return e.getPredNodeID();
			}
		}
		return -1;
	}

	private HashSet<Node> returnallNodes(Node[] nodes ) {
		// TODO Auto-generated method stub
		HashSet<Node> vnode=new HashSet<Node>();
		for(int j=nodes.length-1;j>=0;j--){
			vnode.add(nodes[j]);
		}
		return vnode;
	}

	private void FindSetS() {
		// TODO Auto-generated method stub
		Edge[] cfgedges=new Edge[0];
		cfgedges=acfg.getEdges(cfgedges);
		for(int i=0;i<cfgedges.length;i++){
			Edge e=cfgedges[i];
			int A=e.getPredNodeID();
			int B=e.getSuccNodeID();
			//in PDT B is not ancestor of A
			if(!isAncestor(B,A)){
				S.add(e);
			}
		}

		
	}

	private boolean isAncestor(int b, int a) {
		// TODO Auto-generated method stub
		
		Iterator il=pdedges.iterator();
		while(il.hasNext()){
			Edge edge =(Edge)il.next();
			if(edge.getSuccNodeID()==a){
				if(edge.getPredNodeID()==b){
					return true;
				}
				else{
					return isAncestor(b, edge.getPredNodeID());
				}
			}
		}
		return false;
	}

	private void PostDominatorTreeBuilder() {
		Node[] nodes=rcfg.getBasicBlocks();
		LinkedList<Node> q=new LinkedList<Node>();
		q.add(nodes[nodes.length-2]);
		while(!q.isEmpty()){
			Node m=q.poll();
			for(int i=0;i<nodes.length;i++){
				Node n=nodes[i];
				if(!Dominator[n.getID()].isEmpty()){
					HashSet hs=Dominator[n.getID()];
					if(hs.contains(m)){
						hs.remove(m);
						if(hs.isEmpty()){
							Edge a=new Edge();
							a.setPredNodeID(m.getID());
							a.setSuccNodeID(n.getID());
							q.add(n);
							pdedges.add(a);
						}
					}
				}
			}
		}
		
	}
}
