/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/


package dap4.servlet;

import dap4.core.dmr.*;
import dap4.core.util.DapException;
import dap4.dap4shared.Dap4Util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

public class RandomValue extends Value
{
    //////////////////////////////////////////////////
    // Constants

    static final long SEED = 37L;

    //////////////////////////////////////////////////
    // Instance variables

    Random random = new Random(SEED);

    //////////////////////////////////////////////////
    // Constructor(s)

    public RandomValue()
    {
    }

    //////////////////////////////////////////////////
    // Value Interface

    public ValueSource source()
    {
        return ValueSource.RANDOM;
    }

    public Object
    nextValue(DapType basetype)
        throws DapException
    {
        AtomicType atomtype = basetype.getAtomicType();
        switch (atomtype) {
        case Int8:
        case UInt8:
        case Int16:
        case UInt16:
        case Int32:
        case UInt32:
        case Int64:
        case UInt64:
            return nextLong(basetype);

        case Float32:
            return new Float(random.nextFloat());
        case Float64:
            return new Double(random.nextDouble());

        case Char:
            return nextString(random, 1, 32, 127).charAt(0);

        case String:
            return nextString(random, MAXSTRINGSIZE, 32, 127);

        case URL:
            return nextURL();

        case Opaque:
            int length = 2 + (random.nextInt(MAXOPAQUESIZE) * 2);
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            return ByteBuffer.wrap(bytes);

        case Enum:
            return nextEnum(((DapEnum) basetype));

        default:
            throw new DapException("Unexpected type: " + basetype);
        }
    }

    public long
    nextCount(long min, long max)
        throws DapException
    {
        return longBetween(DapType.INT32, min, max);
    }


    public long
    nextLong(DapType basetype)
        throws DapException
    {
        switch (basetype.getAtomicType()) {
        case Int8:
            return longBetween(basetype, -128L, 127L);
        case UInt8:
            return longBetween(basetype, 0, 255);
        case Int16:
            return longBetween(basetype, Short.MIN_VALUE, Short.MAX_VALUE);
        case UInt16:
            return longBetween(basetype, 0x0, 0xFFFFL);
        case Int32:
            return longBetween(basetype, Integer.MIN_VALUE, Integer.MAX_VALUE);
        case UInt32:
            return longBetween(basetype, 0x0, 0xFFFFFFFFL);
        case Int64:
            return longBetween(basetype, Long.MIN_VALUE, Long.MAX_VALUE);
        case UInt64:
            return longBetween(basetype, 0x0, 0xFFFFFFFFFFFFFFFFL);
        default:
            throw new DapException("Unexpected type: " + basetype);
        }
    }

    public long
    longBetween(DapType basetype, long min, long max)
        throws DapException
    {
        AtomicType atomtype = basetype.getAtomicType();
        if(!atomtype.isIntegerType())
            throw new DapException("longBetween: wrong sort:" + basetype);
        boolean unsigned = atomtype.isUnsigned();
        long range = (max - min);    // generate a number between 0..range-1)
        int rangebits = bitsfor(range);
        BigInteger bmin = BigInteger.valueOf(min);
        BigInteger bmax = BigInteger.valueOf(max);
        long typebits = 8 * Dap4Util.daptypeSize(atomtype);
        BigInteger intrand = new BigInteger(rangebits,random);
        if(intrand.compareTo(bmin) < 0 || intrand.compareTo(bmax) > 0)
                throw new DapException("Cannot generate value");
        return intrand.longValue();
    }

    Object
    nextEnum(DapEnum en)
    {
        long l;
        AtomicType basetype = en.getBaseType().getAtomicType();

        // Collect the enum const values as BigIntegers
        List<String> ecnames = en.getNames();
        BigInteger[] econsts = new BigInteger[ecnames.size()];
        for(int i = 0;i < econsts.length;i++) {
            l = en.lookup(ecnames.get(i));
            econsts[i] = BigInteger.valueOf(l);
            if(basetype == AtomicType.UInt64)
                econsts[i] = econsts[i].and(MASK);
        }

        int index = random.nextInt(econsts.length);
        l = econsts[index].longValue();
        Object val = null;
        switch (basetype) {
        case Int8:
            val = new Byte((byte) l);
            break;
        case UInt8:
            val = new Byte((byte) (l & 0xFFL));
            break;
        case Int16:
            val = new Short((short) l);
            break;
        case UInt16:
            val = new Short((short) (l & 0xFFFFL));
            break;
        case Int32:
            val = new Integer((int) l);
            break;
        case UInt32:
            val = new Integer((int) (l & 0xFFFFFFFFL));
            break;
        case Int64:
            val = new Long(l);
            break;
        case UInt64:
            val = new Long(l);
            break;
        }
        return val;
    }

    String
    nextString(Random random, int maxlength, int min, int max)
    {
        int length = random.nextInt(maxlength);
        if(length == 0) length = 1;
        StringBuilder buf = new StringBuilder();
        for(int i = 0;i < length;i++) {
            int c = random.nextInt((max - min)) + min;
            buf.append((char) c);
        }
        return buf.toString();
    }

    static final String[] protocols = new String[]{"http", "https"};
    static final String legal =
        "abcdefghijklmnoqqrstuvwxyz"
            + "ABCDEFGHIJKLMNOQQRSTUVWXYZ"
            + "0123456789"
            + "_";

    String
    nextURL()
    {
        StringBuilder url = new StringBuilder();
        url.append(protocols[random.nextInt(protocols.length)]);
        url.append("://");
        for(int i = 0;i < HOSTNSEG;i++) {
            if(i > 0) url.append(".");
            for(int j = 0;j < MAXSEGSIZE;j++) {
                int c;
                do {
                    c = random.nextInt('z');
                } while(legal.indexOf(c) < 0);
                url.append((char) c);
            }
        }
        if(random.nextBoolean())
            url.append(String.format(":%d", random.nextInt(5000) + 1));
        for(int i = 0;i < PATHNSEG;i++) {
            url.append("/");
            for(int j = 0;j < MAXSEGSIZE;j++) {
                int c;
                do {
                    c = random.nextInt('z');
                } while(legal.indexOf(c) < 0);
                url.append((char) c);
            }
        }
        return url.toString();
    }

    protected int
    bitsfor(long l)
    {
        assert l >= 0;
        BigInteger bi = BigInteger.valueOf(l);
        return bi.bitLength();
    }

}
