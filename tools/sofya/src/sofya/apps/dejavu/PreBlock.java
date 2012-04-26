package sofya.apps.dejavu;

public class PreBlock {
	String pre;
	int preorder;
	String pretype;
	int BID;
	PreBlock(String p, int b,int pre, String type){
		this.preorder=pre;
		this.pretype=type;
		this.pre=p;
		this.BID=b;
	}
}
