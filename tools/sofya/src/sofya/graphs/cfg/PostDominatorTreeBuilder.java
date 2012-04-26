package sofya.graphs.cfg;

import java.util.Set;
import java.util.TreeSet;

import sofya.graphs.Node;

public class PostDominatorTreeBuilder {
	CFG rcfg;
	TreeSet[] pred;
	int[] semi;
	Node[] vertex;
	int n=0;
	Node[] parent;
	Node[] EVAL;
	int[] ancestor;
	Node[] label;
	public void Builder(){
		
		int noofblocks=rcfg.getBasicBlocks().length;
		Block[] blcks=rcfg.getBasicBlocks();
		semi=new int[noofblocks];
		pred=new TreeSet[noofblocks];
		vertex=new Node[noofblocks];
		parent=new Node[noofblocks];
		
		for(int i=0;i<noofblocks;i++){
			pred[blcks[i].getID()].clear();
			semi[blcks[i].getID()]=0;
		}
		
		DFS(blcks[noofblocks-1].getID());
		
		CalculateEVAL(noofblocks);
		
		
	}
	private void CalculateEVAL(int no) {
		// TODO Auto-generated method stub
		ancestor=new int[no];
		label=new Node[no];
		for(int i=0;i<no;i++){
			ancestor[i]=0;
			label[rcfg.getBlock(i).getID()]=rcfg.getBlock(i);
		}
		
		for(int i=0;i<vertex.length;i++){
			if(ancestor[vertex[i].getID()]==0){
				EVAL[vertex[i].getID()]=vertex[i];
			}
			else {
				Compress(vertex[i]);
				EVAL[vertex[i].getID()]=label[vertex[i].getID()];
			}
			
		}
		
	}
	private void Compress(Node node) {
		// TODO Auto-generated method stub
		if(ancestor[ancestor[node.getID()]]!=0){
			Compress(rcfg.getNode(ancestor[node.getID()]));
			if(semi[label[ancestor[node.getID()]].getID()]<semi[label[node.getID()].getID()]){
				label[node.getID()]=label[ancestor[node.getID()]];
				
			}
			ancestor[node.getID()]=ancestor[ancestor[node.getID()]];
		}
	}
	private void DFS(int id) {
		// TODO Auto-generated method stub
		semi[id]=n;
		n++;
		vertex[n]=rcfg.getNode(id);
		//initiallize variables for steps 2,3,4;
		Node[] suc=vertex[n].getSuccessors();
		for(int i=0;i<suc.length;i++){
			if(semi[suc[i].getID()]==0){
				parent[suc[i].getID()]=rcfg.getNode(id);
			}
			pred[suc[i].getID()].add(vertex[n]);
		}
	}
}
