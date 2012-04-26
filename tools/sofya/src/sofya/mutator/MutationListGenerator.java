package sofya.mutator;

import java.io.IOException;

public class MutationListGenerator {
    private static void printUsage() {
        System.err.println("Usage:\n java sofya.mutator.MutationListGenerator " +
            "MutationTableFileName (.mut file)");
        System.exit(1);
    }
    
    public static void main(String[] argv) throws MutationException {
        if (argv.length < 1) {
            printUsage();
        }
        MutationIterator mutants = null;
        //System.out.println("Count | ID:Type:ClassName:FieldName:OrigAccFlags:dfltMutFlag:Variant;Variant;...");
        //System.out.println("--------------------------------------------------------------------------------");
        int index = 0; for ( ; index < argv.length; index++) {
        	String fname = argv[index];
        	try {
        		mutants = MutationHandler.readMutationFile(fname);
        	}
        	catch (IOException ioe) {
        		throw new MutationException("Cannot open mutant table file\n"+fname, ioe);
        	}
        	//int count = mutants.count();
        	//System.out.print("Count: "+count+"\n");
        	while(mutants.hasNext()) {
        		Mutation mut = mutants.next();
        		System.out.println(mut.print());
        		//System.out.print(mut.getType()+", ");
        		//System.out.print(mut.getID().asInt()+", ");
        		//mut.getDefaultVariant().
        		//System.out.print(mut.getClass().getName()+", ");
        		//System.out.print(mut.getDefaultVariant().toString()+", ");
        		//MutationID mutid = mut.getID();
        		//String muttype = mut.getType();
        		//Variant[] mutvaries = mut.getVariants();
        		//System.out.print(mut.toString());
        	}
            //System.out.println("--------------------------------------------------------------------------------");
        }
    }
}
