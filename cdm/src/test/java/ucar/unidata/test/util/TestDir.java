package ucar.unidata.test.util;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Manage the test data directories and servers
 *
 * @author caron
 * @since 3/23/12
 * Modified 5/15/14 to add remote test server paths
 * 
 * This singleton class computes and stores a variety of constants.
 * <p>
 * <table>
 * <tr><th colspan="3">-D Property Names
 * <tr><th>Static Variable<th>Property Name(s)<th>Description
 * <tr><td>testdataDirPropName<td>unidata.testdata.path
 *     <td>Property name for the path to the Unidata test data directory,
 *         e.g unidata.testdata.path=//shemp/data/testdata2/
 *         the real directory is at shemp:/data/testdata2
 * <tr><td>threddsPropFileName<td>thredds.properties
 *     <td>Filename of the user property file read from the "user.home" directory
 *         if the "unidata.testdata.path" and "unidata.upc.share.path" are not
 *         available as system properties.
 * <tr><td>remoteTestServerPropName<td>remotetest
 *     <td>Property name for the hostname of the remote test server.
 * </table>
 * <p>
 * <table>
 * <tr><th colspan="4">Computed Paths
 * <tr><th>Static Variable<th>Property Name(s) (-d)<th>Default Value<th>Description
 * <tr><td>cdmUnitTestDir<td>NA<td>NA
 *     <td>New test data directory. Do not put temporary files in here.
 *         Migrate all test data here eventually.
 * <tr><td>cdmLocalTestDataDir<td>NA<td>../cdm/src/test/data
 *     <td>Level 1 test data directory (distributed with code and MAY be used in Unidata nightly testing).
 * <tr><td>temporaryLocalTestDataDir<td>NA<td>target/test/tmp
 *     <td>Temporary data directory (for writing temporary data).
 * <tr><td>remoteTestServer<td>remotetestserver<td>remotetest.unidata.ucar.edu
 *     <td>The hostname of the test server for doing remote tests
 * </table>
 *
 */
public class TestDir {

  /**
  * New test data directory. do not put temporary files in here. migrate all test data here eventually
   * Unidata "//fileserver/data/testdata2/cdmUnitTest" directory.
   */
  public static String cdmUnitTestDir = null;

  /**
   * Level 1 test data directory (distributed with code and MAY be used in Unidata nightly testing).
   */
  public static String cdmLocalTestDataDir = "../cdm/src/test/data/";

  /**
   * Temporary data directory (for writing temporary data).
   */
  public static String temporaryLocalDataDir = "target/test/tmp/";

  //////////////////////////////////////////////////////////////////////
  /** Property name for the path to the Unidata test data directory,
   * e.g unidata.testdata2.path=//shemp/data/testdata2/
   * the real directory is at shemp:/data/testdata2
   */
  private static String testdataDirPropName ="unidata.testdata.path";

  /** Filename of the user property file read from the "user.home" directory
   * if the "unidata.testdata2.path" and "unidata.upc.share.path" are not
   * available as system properties. */
  private static String threddsPropFileName = "thredds.properties";

  // Remote Test server

  private static String remoteTestServerPropName = "remotetest";

  static public String remoteTestServer = "remotetest.unidata.ucar.edu";

  //////////////////////////////////////////////////

  // Determine how Unidata "/upc/share" directory is mounted
  // on local machine by reading system or THREDDS property.
  static {
    // Check for system property
    String testdataDirPath = System.getProperty( testdataDirPropName );

    if (testdataDirPath == null ) {
      // Get user property.
      File userHomeDirFile = new File( System.getProperty( "user.home" ) );
      File userThreddsPropsFile = new File( userHomeDirFile, threddsPropFileName );
      if ( userThreddsPropsFile.exists() && userThreddsPropsFile.canRead() ) {
        Properties userThreddsProps = new Properties();
        try {
          userThreddsProps.load( new FileInputStream( userThreddsPropsFile ) );
        }
        catch ( IOException e ) {
          System.out.println( "**Failed loading user THREDDS property file: " + e.getMessage() );
        }
        if ( userThreddsProps != null && ! userThreddsProps.isEmpty() ) {
          if ( testdataDirPath == null )
            testdataDirPath = userThreddsProps.getProperty( testdataDirPropName );
        }
      }
    }

    // Use default paths if needed.
    if ( testdataDirPath == null ) {
      System.out.println( "**No \"unidata.testdata.path\"property, defaulting to \"/share/testdata/\"." );
      testdataDirPath = "/share/testdata/";
    }
    // Make sure paths ends with a slash.
    if ((!testdataDirPath.endsWith( "/")) && !testdataDirPath.endsWith( "\\"))
      testdataDirPath += "/";

    cdmUnitTestDir = testdataDirPath + "cdmUnitTest/";

    File file = new File( cdmUnitTestDir );
    if ( ! file.exists() || !file.isDirectory() ) {
      System.out.println( "**WARN: Non-existence of Level 3 test data directory [" + file.getAbsolutePath() + "]." );
    }

    File tmpDataDir = new File(temporaryLocalDataDir);
    if ( ! tmpDataDir.exists() ) {
      if ( ! tmpDataDir.mkdirs() ) {
        System.out.println( "**ERROR: Could not create temporary data dir <" + tmpDataDir.getAbsolutePath() + ">." );
      }
    }

    String rts = System.getProperty(remoteTestServerPropName);
    if(rts != null && rts.length() > 0)
	    remoteTestServer = rts;
  }

  static public void showMem(String where) {
    Runtime runtime = Runtime.getRuntime();
    System.out.println(where+ " memory free = " + runtime.freeMemory() * .001 * .001 +
        " total= " + runtime.totalMemory() * .001 * .001 +
        " max= " + runtime.maxMemory() * .001 * .001 +
        " MB");
  }

  // from testLocal

  private static boolean dumpFile = false;

  public static NetcdfFile open( String filename) {
    try {
      System.out.println("**** Open "+filename);
      NetcdfFile ncfile = NetcdfFile.open(filename, null);
      if (dumpFile) System.out.println("open "+ncfile);
      return ncfile;

    } catch (java.io.IOException e) {

      try {
        File absf = new File(filename);
        System.out.printf("abs path of %s == %s%n", filename, absf.getCanonicalPath());
      } catch (IOException ioe) {
        e.printStackTrace();
      }
      File pwd = new File (".");
      System.out.printf("pwd = %s%n", pwd.getAbsolutePath());
      System.out.println(" fail = "+e);
      e.printStackTrace();
      assert false;
      return null;
    }
  }

  public static NetcdfFile openFileLocal( String filename) {
    return open( TestDir.cdmLocalTestDataDir +filename);
  }


  ////////////////////////////////////////////////

  public interface Act {
    /**
     * @param filename file to act on
     * @return count
     * @throws IOException  on IO error
     */
    int doAct( String filename) throws IOException;
  }

  public static class FileFilterFromSuffixes implements FileFilter {
    String[] suffixes;
    public FileFilterFromSuffixes(String suffixes) {
      this.suffixes = suffixes.split(" ");
    }

    @Override
    public boolean accept(File file) {
      for (String s: suffixes)
        if (file.getPath().endsWith(s)) return true;
      return false;
    }
  }

  public static class FileFilterNoWant implements FileFilter {
    String[] suffixes;
    public FileFilterNoWant(String suffixes) {
      this.suffixes = suffixes.split(" ");
    }

    @Override
    public boolean accept(File file) {
      for (String s: suffixes)
        if (file.getPath().endsWith(s)) return false;
      return true;
    }
  }

  public static int actOnAll(String dirName, FileFilter ff, Act act) throws IOException {
    return actOnAll( dirName, ff, act, true);
  }

  /**
   * @param dirName recurse into this directory
   * @param ff for files that pass this filter, may be null
   * @param act perform this acction
   * @param recurse recurse into subdirectories
   * @return count
   * @throws IOException on IO error
   */
  public static int actOnAll(String dirName, FileFilter ff, Act act, boolean recurse) throws IOException {
    int count = 0;

    System.out.println("---------------Reading directory "+dirName);
    File allDir = new File( dirName);
    File[] allFiles = allDir.listFiles();
    if (null == allFiles) {
      System.out.println("---------------INVALID "+dirName);
      return count;
    }
    List<File> flist = Arrays.asList(allFiles);
    Collections.sort(flist);

    for (File f : flist) {
      String name = f.getAbsolutePath();
      if (f.isDirectory())
        continue;
      if (((ff == null) || ff.accept(f)) && !name.endsWith(".exclude") ) {
        System.out.println("----acting on file "+name);
        count += act.doAct(name);
      }
    }

    if (!recurse) return count;

    for (File f : allFiles) {
      if (f.isDirectory() && !f.getName().equals("exclude")&& !f.getName().equals("problem"))
        count += actOnAll(f.getAbsolutePath(), ff, act);
    }

    return count;
  }

  ////////////////////////////////////////////////////////////////////////////
  public static int readAllDir(String dirName, FileFilter ff) throws IOException {
    return actOnAll(dirName, ff, new ReadAllVariables());
  }
  
  public static void readAll(String filename) throws IOException {
    ReadAllVariables act = new ReadAllVariables();
    act.doAct(filename);
  }
  
  private static class ReadAllVariables implements Act {

    @Override
    public int doAct(String filename) throws IOException {
      System.out.println("\n------Reading filename "+filename);
      NetcdfFile ncfile = null;
      try {
        ncfile = NetcdfFile.open(filename);

        for (Variable v : ncfile.getVariables()) {
          if (v.getSize() > max_size) {
            Section s = makeSubset(v);
            System.out.println("  Try to read variable " + v.getNameAndDimensions() + " size= " + v.getSize() + " section= " + s);
            v.read(s);
          } else {
            System.out.println("  Try to read variable " + v.getNameAndDimensions() + " size= " + v.getSize());
            v.read();
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        //assert false;

      } finally {
        if (ncfile != null)
          try { ncfile.close(); }
          catch (IOException e) { }
      }

      return 1;
    }
  }

  static int max_size = 1000 * 1000 * 10;
  static Section makeSubset(Variable v) throws InvalidRangeException {
    int[] shape = v.getShape();
    shape[0] = 1;
    Section s = new Section(shape);
    long size = s.computeSize();
    shape[0] = (int) Math.max(1, max_size / size);
    return new Section(shape);
  }

  static public int readAllData( NetcdfFile ncfile) {
    System.out.println("\n------Reading ncfile "+ncfile.getLocation());
    try {

      for (Variable v : ncfile.getVariables()) {
        if (v.getSize() > max_size) {
          Section s = makeSubset(v);
          System.out.println("  Try to read variable " + v.getNameAndDimensions() + " size= " + v.getSize() + " section= " + s);
          v.read(s);
        } else {
          System.out.println("  Try to read variable " + v.getNameAndDimensions() + " size= " + v.getSize());
          v.read();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      assert false;
    }

    return 1;
  }

}
