public class A {

	public static void main(String args[]){
		
		String s = args[0];
		System.out.println(s.substring(s.lastIndexOf("/")+1, s.length()-4));
	}

}