import junit.framework.*;


public class BinStringTest extends TestCase {
   private BinString binString;

   public BinStringTest(String name) {
      super(name);
   }

   protected void setUp() { 
      binString = new BinString();
   }

   public void testSumFunction() { 
      int expected = 0;
      assertEquals(expected, binString.sum(""));
      expected = 100;
      assertEquals(expected, binString.sum("d"));
      expected = 265;
      assertEquals(expected, binString.sum("Add"));
   }

   public void testBinariseFunction() { 
      String expected = "101";
      assertEquals(expected, binString.binarise(5));
      expected = "11111100";
      assertEquals(expected, binString.binarise(252));
   }

   public void testTotalConversion() { 
     String expected = "1000001";
     assertEquals(expected, binString.convert("A"));
   }
}