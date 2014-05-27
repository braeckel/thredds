/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.test;

import dap4.ce.CEConstraint;
import dap4.ce.CECompiler;
import dap4.ce.parser.CEParser;
import dap4.core.dmr.DapDataset;
import dap4.core.dmr.DapFactoryDMR;
import dap4.core.dmr.parser.Dap4Parser;
import dap4.test.util.DapTestCommon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestParserCE extends DapTestCommon
{

    //////////////////////////////////////////////////
    // Constants
    static final boolean DMRPARSEDEBUG = false;
    static final boolean CEPARSEDEBUG = false;
    static final String TESTCASEDIR = "d4tests/src/test/data/resources/TestParsers"; // relative to dap4 root

    //////////////////////////////////////////////////
    // Type decls
    static class TestSet
    {
        static public String rootdir = null;

        public String baseline;
        public String dmr;
        public String constraint;
        public String[] debug = null;
        public int id = 0;
        public String dataset = null;

        public TestSet(int id, String dataset, String ces, String cedmr)
            throws IOException
        {
            this.id = id;
            this.dataset = dataset;
            this.baseline = makepath(this.dataset + "_" + id + ".dmp", "baseline");
            this.dmr = cedmr;
            this.constraint = ces;
        }

        public TestSet setdebug(String[] debug)
        {
            this.debug = debug;
            return this;
        }

        public TestSet setdebug(String debug)
        {
            return setdebug(new String[]{debug});
        }

        String makepath(String file, String parent)
        {
            return getDAP4Root() + "/" + TESTCASEDIR + "/" + parent + "/" + file;
        }

    }

    //////////////////////////////////////////////////
    // Instance methods

    // All test cases
    List<TestSet> alltestsets = new ArrayList<>();
    List<TestSet> chosentests = new ArrayList<>();

    DapDataset dmr = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public TestParserCE()
    {
        this("TestParserCE");
    }

    public TestParserCE(String name)
    {
        super(name);
        setSystemProperties();
        try {
            defineTestCases();
            chooseTestcases();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    //////////////////////////////////////////////////
    // Misc. methods

    protected void
    chooseTestcases()
    {
        if(true) {
            chosentests = locate("/seq|i32<0");
        } else {
            for(TestSet tc : alltestsets)
                chosentests.add(tc);
        }
    }

    // Locate the test cases
    List<TestSet>
    locate(String ce)
    {
        List<TestSet> results = new ArrayList<>();
        for(TestSet ct : this.alltestsets) {
            if(ct.constraint.equals(ce))
                results.add(ct);
        }
        return results;
    }

    protected void
    defineTestCases()
        throws IOException
    {
        TestSet.rootdir = getDAP4Root();
        for(String ce : CE1) {
            TestSet set = new TestSet(1, "ce1", ce, CE1_DMR); // take the constraints from this.txt
            //set = set.setdebug("b[10:16]");
            alltestsets.add(set);
        }
    }

    //////////////////////////////////////////////////
    // Junit test method

    public void testParserCE()
        throws Exception
    {
        for(TestSet testset : chosentests) {
            if(!doOneTest(testset)) {
                assertTrue(false);
                System.exit(1);
            }
        }
    }

    boolean
    doOneTest(TestSet testset)
        throws Exception
    {
        boolean pass = true;

        System.out.println("Test Set: " + testset.constraint);

        // Create the DMR tree
        System.out.println("Parsing DMR");
        Dap4Parser pushparser = new Dap4Parser(new DapFactoryDMR());
        if(DMRPARSEDEBUG)
            pushparser.setDebugLevel(1);
        boolean parseok = pushparser.parse(testset.dmr);
        if(parseok)
            dmr = pushparser.getDMR();
        if(dmr == null)
            parseok = false;
        if(!parseok)
            throw new Exception("DMR Parse failed");
        System.out.flush();
        System.err.flush();

        // Iterate over the constraints
        String results = "";
        CEConstraint ceroot = null;
        System.out.println("constraint: " + testset.constraint);
        System.out.flush();
        CEParser ceparser = null;
        try {
            ceparser = new CEParser(dmr);
            if(CEPARSEDEBUG)
                ceparser.setDebugLevel(1);
            parseok = ceparser.parse(testset.constraint);
            CECompiler compiler = new CECompiler();
            ceroot = compiler.compile(dmr, ceparser.getConstraint());
        } catch (Exception e) {
            e.printStackTrace();
            parseok = false;
        }
        if(ceroot == null)
            parseok = false;
        if(!parseok)
            throw new Exception("CE Parse failed");

        // Dump the parsed CE for comparison purposes
        String cedump = ceroot.toConstraintString();
        if(prop_visual)
            visual(testset.baseline + " |" + testset.constraint + "|", cedump);
        results += (cedump + "\n");
        if(prop_baseline) {
            writefile(testset.baseline, results);
        } else if(prop_diff) { //compare with baseline
            // Read the baseline file
            String baselinecontent = readfile(testset.baseline);
            pass = compare(baselinecontent, results);
        }

        return pass;
    }

    //////////////////////////////////////////////////
    // Standalone main procecedure

    static public void
    main(String[] argv)
        throws Exception
    {
        new TestParserCE("TestParserCE").testParserCE();
    }// main


    ////////////////////////////////////
    // Data for the tests

    // Constraint tests for CE1_DMR
    String[] CE1 = new String[]{
        "/a[1]",
        "/b[10:16]",
        "/c[8:2:15]",
        "/a[1];/b[10:16];/c[8:2:15]",
        "/d[1][0:2:2];/a[1];/e[1][0];/f[0][1]",
        "/s[0:3][0:2].x;/s[0:3][0:2].y",
        "s|i1<0",
    };

    String CE1_DMR =
        "<Dataset"
            + "         name=\"ce1\""
            + "         dapVersion=\"4.0\""
            + "         dmrVersion=\"1.0\""
            + "         ns=\"http://xml.opendap.org/ns/DAP/4.0#\">"
            + "  <Dimension name=\"d10\" size=\"10\"/>"
            + "  <Dimension name=\"d17\" size=\"17\"/>"
            + "  <Int32 name=\"a\">"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Int32 name=\"b\">"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Int32 name=\"c\">"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Int32 name=\"d\">"
            + "    <Dim name=\"/d10\"/>"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Int32 name=\"e\">"
            + "    <Dim name=\"/d10\"/>"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Int32 name=\"f\">"
            + "    <Dim name=\"/d10\"/>"
            + "    <Dim name=\"/d17\"/>"
            + "  </Int32>"
            + "  <Structure name=\"s\">"
            + "      <Int32 name=\"x\"/>"
            + "      <Int32 name=\"y\"/>"
            + "    <Dim name=\"/d10\"/>"
            + "    <Dim name=\"/d10\"/>"
            + "  </Structure>"
            + "  <Sequence name=\"s\">"
            + "    <Int32 name=\"i1\"/>"
            + "    <Int16 name=\"sh1\"/>"
            + "  </Sequence>"
            + "</Dataset>";

}





