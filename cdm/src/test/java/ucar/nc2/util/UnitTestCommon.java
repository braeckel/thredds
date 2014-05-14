/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.util;

import junit.framework.TestCase;
import org.apache.http.*;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import ucar.unidata.test.Diff;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class UnitTestCommon extends TestCase
{
    static boolean DEBUG = false;

    // Hold the primary server names here so we do not
    // have to search all over
    static final public String REMOTESERVER = "remotetest.unidata.ucar.edu";

    /**
     * Temporary data directory (for writing temporary data).
     */
    static public String TEMPROOT = "target/test/tmp/"; // relative to module root

    // Look for these to verify we have found the thredds root
    static final String[] SUBROOTS = new String[]{"httpclient", "cdm", "tds", "opendap"};

    static public final String threddsRoot = locateThreddsRoot();

    // Walk around the directory structure to locate
    // the path to a given directory.

    static String locateThreddsRoot()
    {
        // Walk up the user.dir path looking for a node that has
        // all the directories in SUBROOTS.

        String path = System.getProperty("user.dir");

        // clean up the path
        path = path.replace('\\', '/'); // only use forward slash
        assert (path != null);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);

        File prefix = new File(path);
        for (; prefix != null; prefix = prefix.getParentFile()) {//walk up the tree
            int found = 0;
            String[] subdirs = prefix.list();
            for (String dirname : subdirs) {
                for (String want : SUBROOTS) {
                    if (dirname.equals(want)) {
                        found++;
                        break;
                    }
                }
            }
            if (found == SUBROOTS.length) try {// Assume this is it
                String root = prefix.getCanonicalPath();
                // clean up the root path
                root = root.replace('\\', '/'); // only use forward slash
                return root;
            } catch (IOException ioe) {
            }
        }
        return null;
    }


    //////////////////////////////////////////////////

    public void
    clearDir(File dir, boolean clearsubdirs)
            throws Exception
    {
        // wipe out the dir contents
        if (!dir.exists()) return;
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (clearsubdirs)
                    clearDir(f, true); // clear subdirs
                else
                    throw new Exception("InnerClass directory encountered: " + f.getAbsolutePath());
            }
            f.delete();
        }
    }

    //////////////////////////////////////////////////
    // Instance data

    String title = "Testing";
    String name = "testcommon";

    public UnitTestCommon()
    {
        this("UnitTest");
    }

    public UnitTestCommon(String name)
    {
        super(name);
        this.name = name;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getTitle()
    {
        return this.title;
    }

    public String compare(String tag, String baseline, String s)
    {
        try {
            // Diff the two print results
            Diff diff = new Diff(tag);
            StringWriter sw = new StringWriter();
            boolean pass = !diff.doDiff(baseline, s, sw);
            return (pass ? null : sw.toString());
        } catch (Exception e) {
            System.err.println("UnitTest: Diff failure: " + e);
            return null;
        }

    }

    // suppress warning message
    public void testFakerooni()
    {
        assert true;
    }

    static public byte[]
    readbinaryfile(InputStream stream)
            throws IOException
    {
        // Extract the stream into a bytebuffer
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] tmp = new byte[1 << 16];
        for (; ; ) {
            int cnt;
            cnt = stream.read(tmp);
            if (cnt <= 0) break;
            bytes.write(tmp, 0, cnt);
        }
        return bytes.toByteArray();
    }
}

