/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.servlet;

import dap4.ce.CEConstraint;
import dap4.core.dmr.*;
import dap4.core.util.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static dap4.servlet.Value.ValueSource;

/**
 * Given a DMR, return:
 * 1. A byte array (byte[]) containing serialized data
 * and (optionally) the DMR
 * <p/>
 * Requirements:
 * 1. repeatability: given the same DMR, return the same byte array
 * <p/>
 * Notes:
 * 1. Two options are provided for generating values:
 * a. use of a random number generator with a fixed seed.
 * b. use of a pre-defined sequence of values with repetition
 * when the sequence is exhausted (not yet implemented).
 * <p/>
 * Additionally, provide two options for generating data from a DMR.
 * 1. Automated generation of the data from the whole DMR.
 * 2. Selective generation by starting at some variable
 * in the DMR. This is useful when one wants more detailed
 * control over e.g. the number of tuples in a sequence.
 */

public class Generator extends DapSerializer
{
    static final boolean PARSEDEBUG = false;
    static final boolean DEBUG = false;

    static final String LBRACE = "{";
    static final String RBRACE = "}";

    static final int DEFALTCACHELIMIT = 4; // max cache size    

    static final ByteOrder DEFAULTORDER = ByteOrder.LITTLE_ENDIAN;

    static final String DATADIR = "d4tests/src/test/data"; // relative to dap4 root
    static final String TESTSRC = DATADIR + "/resources/testfiles";

    static final String SERIALEXT = ".ser";
    static final String SRCEXT = ".dmr" + SERIALEXT;
    static final String DSTEXT = ".dap" + SERIALEXT;

    static final int MAXROWS = 5;

    //////////////////////////////////////////////////
    // static methods
    protected static boolean asciionly = true;

    static public void setASCII(boolean tf)
    {
        asciionly = tf;
    }

    //////////////////////////////////////////////////
    // Instance variables

    protected Value values = null; // Value generator
    protected ByteArrayOutputStream stream = null;
    protected byte[] serialization = null;
    protected ChunkWriter cw = null;
    protected CEConstraint ce = null;
    protected boolean withdmr = true;

    protected int rowcount = 0;

    //////////////////////////////////////////////////
    // Constructor(s)

    public Generator(ValueSource src)
        throws DapException
    {
        super();
        if(src == null)
            src = ValueSource.RANDOM; // default
        switch (src) {
        case FIXED:
        case RANDOM:
        default:
            values = new RandomValue();
            break;
        }
        values.setASCII(asciionly);
        SerialWriter.DEBUG = DEBUG;
    }

    //////////////////////////////////////////////////
    // Accessors

    public int getRowCount()
    {
        return this.rowcount;
    }

    public void setRowCount(int count)
    {
        if(count >= 0)
            this.rowcount = count;
    }

    //////////////////////////////////////////////////
    // Generator

    public void
    generate(DapDataset dmr, CEConstraint ce, ChunkWriter cw)
        throws DapException
    {
        generate(dmr, ce, cw, true);
    }

    public void
    generate(DapDataset dmr, CEConstraint ce, ChunkWriter cw, boolean withdmr)
        throws DapException
    {
        begin(ce, cw, withdmr);
        if(this.withdmr)
            generateDMR(dmr);
        dataset(dmr);
        end();
    }

    public void
    begin(CEConstraint ce, ChunkWriter cw, boolean withdmr)
        throws DapException
    {
        this.dmr = dmr;
        this.cw = cw;
        if(ce == null)
            ce = CEConstraint.getUniversal(dmr);
        this.ce = ce;
        this.order = cw.getOrder();
        this.withdmr = withdmr;
        writer = new SerialWriter(this.cw, this.order);
        this.dmr = dmr;
    }

    public void
    end()
        throws DapException
    {
    }

    public void
    generateDMR(DapDataset dmr)
        throws DapException
    {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            DMRPrint dp = new DMRPrint(pw);
            dp.print(dmr, this.ce);
            pw.close();
            sw.close();
            String tmp = sw.toString();
            this.cw.writeDMR(tmp);
            this.cw.flush();
        } catch (Exception e) {
            throw new DapException(e);
        }
    }

    //////////////////////////////////////////////////
    // Node specific generators

    public void
    dataset(DapDataset dmr)
        throws DapException
    {
        writer.startDataset();
        // Iterate over the variables in order
        for(DapVariable var : this.dmr.getTopVariables()) {
            if(!this.ce.references(var))
                continue;
            variable(var);
        }
        writer.endDataset();
    }

    public void
    variable(DapVariable dapvar)
        throws DapException
    {
        if(dapvar.getSort() == DapSort.ATOMICVARIABLE) {
            atomicVariable((DapAtomicVariable) dapvar);
        } else if(dapvar.getSort() == DapSort.STRUCTURE) {
            structure((DapStructure) dapvar);
        } else if(dapvar.getSort() == DapSort.SEQUENCE) {
            sequence((DapSequence) dapvar);
        } else
            throw new DapException("generate var: not a variable:" + dapvar.getFQN());
        try {
            writer.endVariable();
        } catch (IOException ioe) {
            throw new DapException(ioe);
        }
    }

    public void
    atomicVariable(DapAtomicVariable dapvar)
        throws DapException
    {
        DapType basetype = dapvar.getBaseType();
        Odometer odom = null;
        if(dapvar.getRank() == 0) {//scalar
            odom = Odometer.getScalarOdometer();
        } else {// dimensioned
            // get the slices from the constraint
            List<Slice> slices = ce.getVariableSlices(dapvar);
            // Create an odometer from the slices
            odom = new Odometer(slices, dapvar.getDimensions());
        }
        while(odom.hasNext()) {
            Object value = values.nextValue(basetype);
            try {
                writer.writeObject(basetype, value);
            } catch (IOException ioe) {
                throw new DapException(ioe);
            }
            odom.next();
        }
    }

    public void
    structure(DapStructure struct)
        throws DapException
    {
        List<DapVariable> fields = struct.getFields();
        Odometer odom = null;
        if(struct.getRank() == 0) {//scalar
            odom = Odometer.getScalarOdometer();
        } else {// dimensioned
            List<Slice> slices = ce.getVariableSlices(struct);
            odom = new Odometer(slices, struct.getDimensions());
        }
        while(odom.hasNext()) {
            // generate a value for each field recursively
            for(int i = 0;i < fields.size();i++) {
                DapVariable field = fields.get(i);
                variable(field);
            }
            odom.next();
        }
    }

    public void
    sequence(DapSequence seq)
        throws DapException
    {
        List<DapVariable> fields = seq.getFields();
        Odometer odom = null;
        if(seq.getRank() == 0) {//scalar
            odom = Odometer.getScalarOdometer();
        } else {// dimensioned
            List<Slice> slices = ce.getVariableSlices(seq);
            odom = new Odometer(slices, seq.getDimensions());
        }
        try {
            while(odom.hasNext()) {
                // Decide how many rows for this sequence
                int nrows = (rowcount == 0 ? this.values.nextCount(MAXROWS)
                    : rowcount);
                writer.writeObject(DapType.INT64, (long) nrows);
                for(int i = 0;i < nrows;i++) {
                    for(int j = 0;j < fields.size();j++) {
                        DapVariable field = fields.get(j);
                        variable(field);
                    }
                }
                odom.next();
            }
        } catch (IOException ioe) {
            throw new DapException(ioe);
        }
    }


}
