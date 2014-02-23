package dap4.test;

import dap4.servlet.CDMDSP;
import dap4.test.util.UnitTestCommon;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class TestNc4Iosp extends UnitTestCommon
{
    static protected final boolean DEBUG = false;

    static protected final boolean NCDUMP = true;

    static protected final boolean HDF5 = true; // false => NC4Iosp

    static protected final Mode mode = Mode.BOTH;

    //////////////////////////////////////////////////
    // Constants

    static protected String DATADIR = "d4tests/src/test/data"; // relative to opuls root
    static protected String TESTDATADIR = DATADIR + "/resources/";
    static protected String BASELINEDIR = DATADIR + "/resources/TestIosp/baseline";
    static protected String TESTINPUTDIR = DATADIR + "/resources/testfiles";


    static protected final BigInteger MASK = new BigInteger("FFFFFFFFFFFFFFFF", 16);

    //////////////////////////////////////////////////
    // Type Declarations

    static protected class Nc4IospTest
    {
        static String root = null;
        String title;
        String dataset;
        String testinputpath;
        String baselinepath;

        Nc4IospTest(String dataset)
        {
            this.title = dataset;
            this.dataset = dataset;
            this.testinputpath
                = root + "/" + TESTINPUTDIR + "/" + dataset;
            this.baselinepath
                = root + "/" + BASELINEDIR + "/" + dataset + ".nc4";
        }

        public String toString()
        {
            return dataset;
        }
    }

    static protected enum Mode
    {
        DMR, DATA, BOTH;
    }

    //////////////////////////////////////////////////
    // Instance variables

    // System properties

    protected boolean prop_diff = true;
    protected boolean prop_baseline = false;
    protected boolean prop_visual = false;
    protected boolean prop_debug = DEBUG;
    protected boolean prop_generate = true;

    // Misc variables
    protected boolean isbigendian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    // Test cases

    protected List<Nc4IospTest> alltestcases = new ArrayList<Nc4IospTest>();

    protected List<Nc4IospTest> chosentests = new ArrayList<Nc4IospTest>();

    protected String datasetpath = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public TestNc4Iosp()
        throws Exception
    {
        this("TestServlet");
    }

    public TestNc4Iosp(String name)
        throws Exception
    {
        this(name, null);
    }

    public TestNc4Iosp(String name, String[] argv)
        throws Exception
    {
        super(name);
        this.dap4root = super.threddsroot + "/dap4";
        setSystemProperties();
        if(!HDF5) {
            CDMDSP.loadNc4Iosp();  // Load Nc4Iosp
        }
        Nc4IospTest.root = dap4root;
        File f = new File(dap4root + "/" + BASELINEDIR);
        if(!f.exists()) f.mkdir();
        this.datasetpath = this.dap4root + "/" + DATADIR;
        defineAllTestcases();
        chooseTestcases();
    }

    //////////////////////////////////////////////////
    // Define test cases

    void
    chooseTestcases()
    {
        if(false) {
            chosentests = locate("test_vlen4.nc");
            //chosentests.add(new Nc4IospTest("test_test.nc"));
        } else {
            for(Nc4IospTest tc : alltestcases)
                chosentests.add(tc);
        }
    }

    void defineAllTestcases()
    {
        this.alltestcases.add(new Nc4IospTest("test_one_var.nc"));
        this.alltestcases.add(new Nc4IospTest("test_one_vararray.nc"));
        this.alltestcases.add(new Nc4IospTest("test_atomic_types.nc"));
        this.alltestcases.add(new Nc4IospTest("test_atomic_array.nc"));
        this.alltestcases.add(new Nc4IospTest("test_enum.nc"));
        this.alltestcases.add(new Nc4IospTest("test_enum_array.nc"));
        this.alltestcases.add(new Nc4IospTest("test_struct_type.nc"));
        this.alltestcases.add(new Nc4IospTest("test_struct_array.nc"));
        this.alltestcases.add(new Nc4IospTest("test_struct_nested.nc"));
        this.alltestcases.add(new Nc4IospTest("test_vlen1.nc"));
        this.alltestcases.add(new Nc4IospTest("test_vlen2.nc"));
        this.alltestcases.add(new Nc4IospTest("test_vlen3.nc"));
        this.alltestcases.add(new Nc4IospTest("test_vlen4.nc"));
        this.alltestcases.add(new Nc4IospTest("test_vlen5.nc"));
    }


    //////////////////////////////////////////////////
    // Junit test methods

    public void testNc4Iosp()
        throws Exception
    {
            boolean allpass = true;
            for(Nc4IospTest testcase : chosentests) {
                boolean ok = doOneTest(testcase);
                if(!ok)
                    allpass = false;
            }
            assertTrue("At least one test failed", allpass);
    }

    //////////////////////////////////////////////////
    // Primary test method
    boolean
    doOneTest(Nc4IospTest testcase)
        throws Exception
    {
        boolean pass = true;

        System.out.println("Testcase: " + testcase.testinputpath);

        NetcdfDataset ncfile = openDataset(testcase.testinputpath);

        String metadata = null;
        String data = null;
        if(mode == Mode.DMR || mode == Mode.BOTH) {
            metadata = (NCDUMP ? ncdumpmetadata(ncfile) : null);
            if(prop_visual)
                visual("Meta Data: ", metadata);
        }
        if(mode == Mode.DATA || mode == Mode.BOTH) {
            data = (NCDUMP ? ncdumpdata(ncfile) : null);
            if(prop_visual)
                visual("Data: ", data);
        }

        String baselinefile = String.format("%s", testcase.baselinepath);
        if(prop_baseline) {
            if(mode == Mode.DMR || mode == Mode.BOTH)
                writefile(baselinefile + ".dmr", metadata);
            if(mode == Mode.DATA || mode == Mode.BOTH)
                writefile(baselinefile + ".dap", data);
        } else if(prop_diff) { //compare with baseline
            String baselinecontent = null;
            if(mode == Mode.DMR || mode == Mode.BOTH) {
                // Read the baseline file(s)
                System.out.println("DMR Comparison:");
                try {
                    baselinecontent = readfile(baselinefile + ".dmr");
                    pass = pass && compare(baselinecontent, metadata);
                } catch (IOException ioe) {
                    System.err.println("baselinefile" + ".dmr: " + ioe.getMessage());
                    pass = false;
                }
                System.out.println(pass ? "Pass" : "Fail");
            }
            if(mode == Mode.DATA || mode == Mode.BOTH) {
                System.out.println("DATA Comparison:");
                try {
                    baselinecontent = readfile(baselinefile + ".dap");
                    pass = pass && compare(baselinecontent, data);
                } catch (IOException ioe) {
                    System.err.println("baselinefile" + ".dap: " + ioe.getMessage());
                    pass = false;
                }
                System.out.println(pass ? "Pass" : "Fail");
            }
        }
        return pass;
    }

    //////////////////////////////////////////////////
    // Utility methods

    boolean
    report(String msg)
    {
        System.err.println(msg);
        prop_generate = false;
        return false;
    }

    /**
     * Try to get the system properties
     */
    void setSystemProperties()
    {
        if(System.getProperty("nodiff") != null)
            prop_diff = false;
        String value = System.getProperty("baseline");
        if(value != null) prop_baseline = true;
        value = System.getProperty("nogenerate");
        if(value != null) prop_generate = false;
        value = System.getProperty("debug");
        if(value != null) prop_debug = true;
        if(System.getProperty("visual") != null)
            prop_visual = true;
        if(prop_baseline && prop_diff)
            prop_diff = false;
    }

    // Locate the test cases with given prefix
    List<Nc4IospTest>
    locate(String prefix)
    {
        List<Nc4IospTest> results = new ArrayList<Nc4IospTest>();
        for(Nc4IospTest ct : this.alltestcases) {
            if(ct.dataset.startsWith(prefix))
                results.add(ct);
        }
        return results;
    }
    //////////////////////////////////////////////////
    // Stand alone

    static public void
    main(String[] argv)
    {
        try {
            new TestNc4Iosp().testNc4Iosp();
        } catch (Exception e) {
            System.err.println("*** FAIL");
            e.printStackTrace();
            System.exit(1);
        }
        System.err.println("*** PASS");
        System.exit(0);
    }// main

    //////////////////////////////////////////////////
    // Dump methods

    String ncdumpmetadata(NetcdfDataset ncfile)
    {
        boolean ok = false;
        String metadata = null;
        StringWriter sw = new StringWriter();

        // Print the meta-databuffer using these args to NcdumpW
        ok = false;
        try {
            ok = ucar.nc2.NCdumpW.print(ncfile, "-unsigned", sw, null);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            ok = false;
        }
        try {
            sw.close();
        } catch (IOException e) {
        }
        ;
        if(!ok) {
            System.err.println("NcdumpW failed");
            System.exit(1);
        }
        return shortenFileName(sw.toString(),ncfile.getLocation());
    }

    String ncdumpdata(NetcdfDataset ncfile)
    {
        boolean ok = false;
        StringWriter sw = new StringWriter();

        // Dump the databuffer
        sw = new StringWriter();
        ok = false;
        try {
            ok = ucar.nc2.NCdumpW.print(ncfile, "-vall -unsigned", sw, null);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            ok = false;
        }
        try {
            sw.close();
        } catch (IOException e) {
        }
        ;
        if(!ok) {
            System.err.println("NcdumpW failed");
            System.exit(1);
        }
        return shortenFileName(sw.toString(),ncfile.getLocation());
    }


}
