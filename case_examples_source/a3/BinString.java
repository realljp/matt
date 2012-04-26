// A Class that adds up a string based on the ASCII values of its
// characters and then returns the binary representation of the sum.
public class BinString {
  public  BinString() {}

   public String convert(String s) {
      return binarise(sum(s));
   }

   public int sum(String s) {
      if(s=="") return 0;
      if(s.length()==1) return ((int)(s.charAt(0)));
      return ((int)(s.charAt(0)))+sum(s.substring(1));
   }

   public String binarise(int x) {
      if(x==0) return "";
      if(x%2==1) return "1"+binarise(x/2); 
      return "0"+binarise(x/2); 
   }
}       
