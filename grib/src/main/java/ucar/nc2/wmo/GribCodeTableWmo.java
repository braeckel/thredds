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
package ucar.nc2.wmo;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import ucar.grib.grib2.ParameterTable;
import ucar.nc2.iosp.grid.GridParameter;
import ucar.nc2.iosp.netcdf3.N3iosp;
import ucar.unidata.util.StringUtil2;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Read and process WMO grib code tables., in their standard XML format
 *
 * @author caron
 * @since Jul 31, 2010
 */

public class GribCodeTableWmo implements Comparable<GribCodeTableWmo> {
  static private org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GribCodeTableWmo.class);

  public enum Version {
    GRIB2_5_2_0, GRIB2_6_0_1;

    String getResourceName() {
      return "/resources/grib/wmo/" + this.name() + "_CodeFlag_E.xml";
    }

    String[] getElemNames() {
      if (this == GRIB2_5_2_0) {
        return new String[]{"ForExport_CodeFlag_E", "TableTitle_E", "TableSubTitle_E", "Meaning_E", "AsciiUnit_x002F_Description_E"};

      } else if (this == GRIB2_6_0_1) {
        return new String[]{"Exp_codeflag_E", "Title_E", "SubTitle_E", "MeaningParameterDescription_E", "AsciiUnitComments_E"};
      }
      return null;
    }
  }

  public static TableEntry getParameterEntry(int discipline, int category, int value) {
    return getTableEntry(getId(discipline, category), value);
  }

  public static String getParameterName(int discipline, int category, int value) {
    return getTableValue(getId(discipline, category), value);
  }

  private static String getId(int discipline, int category) {
    return "4.2." + discipline + "." + category;
  }

  public static String getTableValue(String tableId, int value) {
    if (wmoTables == null)
      try {
        getWmoStandard();
      } catch (IOException e) {
        throw new IllegalStateException("cant open wmo tables");
      }

    GribCodeTableWmo table = wmoTables.map.get(tableId);
    if (table == null) return null;
    GribCodeTableWmo.TableEntry entry = table.get(value);
    if (entry == null) return null;
    return entry.meaning;
  }

  public static TableEntry getTableEntry(String tableId, int value) {
    if (wmoTables == null)
      try {
        getWmoStandard();
      } catch (IOException e) {
        throw new IllegalStateException("cant open wmo tables");
      }

    GribCodeTableWmo table = wmoTables.map.get(tableId);
    if (table == null) return null;
    return table.get(value);
  }

/////////////////////////////////////////

  private static GribTables wmoTables = null;
  private static final Version wmoTable = Version.GRIB2_6_0_1;

  static public GribTables getWmoStandard() throws IOException {
    if (wmoTables == null)
      wmoTables = readGribCodes(wmoTable);
    return wmoTables;
  }

  static public class GribTables {
    public String name;
    public List<GribCodeTableWmo> list;
    public Map<String, GribCodeTableWmo> map;  // key is table.getTableId()

    private GribTables(String name, List<GribCodeTableWmo> list, Map<String, GribCodeTableWmo> map) {
      this.name = name;
      this.list = list;
      this.map = map;
    }
  }

  /*
  GRIB2_5_2_0:

  <ForExport_CodeFlag_E>
   <No>645</No>
   <TableTitle_E>Code table 4.2 - Parameter number by product discipline and parameter category</TableTitle_E>
   <TableSubTitle_E>Product discipline 0 - Meteorological products, parameter category 19: physical atmospheric</TableSubTitle_E>
   <CodeFlag>13</CodeFlag>
   <Meaning_E>Contrail intensity</Meaning_E>
   <AsciiUnit_x002F_Description_E>(Code table 4.210)</AsciiUnit_x002F_Description_E>
   <Status>Operational</Status>
  </ForExport_CodeFlag_E>

  GRIB2_6_0_1:

  <Exp_codeflag_E>
   <No>678</No>
   <Title_E>Code table 4.2 - Parameter number by product discipline and parameter category</Title_E>
   <SubTitle_E>Product discipline 2 - Land surface products, parameter category 0: vegetation/biomass</SubTitle_E>
   <CodeFlag>1</CodeFlag>
   <MeaningParameterDescription_E>Surface roughness</MeaningParameterDescription_E>
   <AsciiUnitComments_E>m</AsciiUnitComments_E>
   <Status>Operational</Status>
  </Exp_codeflag_E>
  */

  static private GribTables readGribCodes(Version version) throws IOException {
    InputStream ios = null;
    try {
      Class c = GribCodeTableWmo.class;
      ios = c.getResourceAsStream(version.getResourceName());
      if (ios == null) {
        System.out.printf("cant open %s%n", version.getResourceName());
        return null;
      }

      org.jdom.Document doc;
      try {
        SAXBuilder builder = new SAXBuilder();
        doc = builder.build(ios);
      } catch (JDOMException e) {
        throw new IOException(e.getMessage());
      }
      Element root = doc.getRootElement();

      Map<String, GribCodeTableWmo> map = new HashMap<String, GribCodeTableWmo>();
      String[] elems = version.getElemNames();

      List<Element> featList = root.getChildren(elems[0]); // main element
      for (Element elem : featList) {
        String line = elem.getChildTextNormalize("No");
        String tableName = elem.getChildTextNormalize(elems[1]); // 1 = table name
        String code = elem.getChildTextNormalize("CodeFlag");
        String meaning = elem.getChildTextNormalize(elems[3]); // 3 = meaning

        GribCodeTableWmo ct = map.get(tableName);
        if (ct == null) {
          ct = new GribCodeTableWmo(tableName);
          map.put(tableName, ct);
        }

        Element unitElem = elem.getChild(elems[4]); // 4 = units
        String unit = (unitElem == null) ? null : unitElem.getTextNormalize();

        Element statusElem = elem.getChild("Status");
        String status = (statusElem == null) ? null : statusElem.getTextNormalize();

        Element subtableElem = elem.getChild(elems[2]); // 2 = subtable name
        if (subtableElem != null) {
          String subTableName = subtableElem.getTextNormalize();
          GribCodeTableWmo cst = map.get(subTableName);
          if (cst == null) {
            cst = new GribCodeTableWmo(tableName, subTableName);
            map.put(subTableName, cst);
          }
          cst.add(line, code, meaning, unit, status);

        } else {
          ct.add(line, code, meaning, unit, status);
        }

      }

      ios.close();

      List<GribCodeTableWmo> tlist = new ArrayList<GribCodeTableWmo>(map.values());
      Collections.sort(tlist);
      for (GribCodeTableWmo gt : tlist)
        Collections.sort(gt.entries);

      Map<String, GribCodeTableWmo> map2 = new HashMap<String, GribCodeTableWmo>(2 * tlist.size());
      for (GribCodeTableWmo ct : tlist) {
        map2.put(ct.getTableId(), ct);
      }

      return new GribTables(version.getResourceName(), tlist, map2);

    } finally {
      if (ios != null)
        ios.close();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////

  public String tableName;
  public int m1, m2;
  public boolean isParameter;
  public int discipline = -1;
  public int category = -1;

  public List<TableEntry> entries = new ArrayList<TableEntry>();

  GribCodeTableWmo(String name) {
    this.tableName = name;
    String[] s = name.split(" ");
    String id = s[2];
    String[] slist2 = id.split("\\.");
    if (slist2.length == 2) {
      m1 = Integer.parseInt(slist2[0]);
      m2 = Integer.parseInt(slist2[1]);
    } else
      System.out.println("HEY bad= %s%n" + name);
  }

  GribCodeTableWmo(String tableName, String subtableName) {
    String[] s = tableName.split(" ");
    String id = s[2];
    String[] slist2 = id.split("\\.");
    if (slist2.length == 2) {
      m1 = Integer.parseInt(slist2[0]);
      m2 = Integer.parseInt(slist2[1]);
    } else
      System.out.println("HEY bad= %s%n" + tableName);

    this.tableName = subtableName;
    String[] slist = subtableName.split("[ :]+");
    try {
      for (int i = 0; i < slist.length; i++) {
        if (slist[i].equalsIgnoreCase("discipline"))
          discipline = Integer.parseInt(slist[i + 1]);
        if (slist[i].equalsIgnoreCase("category"))
          category = Integer.parseInt(slist[i + 1]);
      }
    } catch (Exception e) {
    }

    isParameter = (discipline >= 0) && (category >= 0);
  }

  private void add(String line, String code, String meaning, String unit, String status) {
    entries.add(new TableEntry(line, code, meaning, unit, status));
  }

  TableEntry get(int value) {
    for (TableEntry p : entries) {
      if (p.start == value) return p;
    }
    return null;
  }

  @Override
  public int compareTo(GribCodeTableWmo o) {
    if (m1 != o.m1) return m1 - o.m1;
    if (m2 != o.m2) return m2 - o.m2;
    if (discipline != o.discipline) return discipline - o.discipline;
    return category - o.category;
  }

  public String getTableId() {
    return isParameter ? m1 + "." + m2 + "." + discipline + "." + category : m1 + "." + m2;
  }

  public String getTableName() {
    return tableName;
  }

  @Override
  public String toString() {
    return "GribCodeTable{" +
            "name='" + tableName + '\'' +
            ", m1=" + m1 +
            ", m2=" + m2 +
            ", isParameter=" + isParameter +
            ", discipline=" + discipline +
            ", category=" + category +
            '}';
  }

  // remove () for the following:
  private static int[] badones = new int[]{
          0, 1, 51,
          0, 6, 25,
          0, 19, 22,
          0, 19, 25,
          0, 191, 0,
          1, 0, 0,
          1, 0, 1,
          1, 1, 0,
          1, 1, 1,
          1, 1, 2,
          2, 0, 0,
          2, 0, 31,
          10, 191, 0};

  private boolean remove(TableEntry entry) {
    for (int i = 0; i < badones.length; i += 3)
      if (discipline == badones[i] && category == badones[i + 1] && entry.number == badones[i + 2])
        return true;

    return false;
  }

  // truncate the following:
  private static int[] truncOnes = new int[]{
          10, 0, 46, (int) 'E',
          10, 0, 47, (int) 'E',
          10, 0, 48, (int) 'E',
  };

  private int truncateAtChar(TableEntry entry) {
    for (int i = 0; i < truncOnes.length; i += 4)
      if (discipline == truncOnes[i] && category == truncOnes[i + 1] && entry.number == truncOnes[i + 2])
        return truncOnes[i + 3];

    return -1;
  }

  public class TableEntry implements Comparable<TableEntry> {
    public int start, stop, line;
    public int number = -1;
    public String code, meaning, name, unit, status;

    TableEntry(String line, String code, String meaning, String unit, String status) {
      this.line = Integer.parseInt(line);
      this.code = code;
      this.meaning = meaning;
      this.status = status;

      try {
        int pos = code.indexOf('-');
        if (pos > 0) {
          start = Integer.parseInt(code.substring(0, pos));
          String stops = code.substring(pos + 1);
          stop = Integer.parseInt(stops);
        } else {
          start = Integer.parseInt(code);
          stop = start;
          number = start;
        }
      } catch (Exception e) {
        start = -1;
        stop = 0;
      }

      if (isParameter) {
        // some need the () comment removed - must be hand specified (!)
        if (remove(this)) {
          int pos1 = meaning.indexOf('(');
          int pos2 = meaning.indexOf(')');
          if ((pos1 > 0) && (pos2 > 0))
            meaning = meaning.substring(0, pos1).trim(); // assume () is at the end od string
        }
        int atChar;
        if ((atChar = truncateAtChar(this)) > 0) {
          int pos = meaning.indexOf(atChar);
          if (pos > 0)
            meaning = meaning.substring(0, pos).trim(); // truncate at pos
          else
            System.out.printf("bugger no %s in %s%n", (char) atChar, meaning);
        }

        // meaning = StringUtil.replace(meaning, '-', "_");
        meaning = StringUtil2.replace(meaning, '/', "-");
        meaning = StringUtil2.replace(meaning, '.', "p");
        meaning = StringUtil2.remove(meaning, '(');
        meaning = StringUtil2.remove(meaning, ')');
        this.name = N3iosp.createValidNetcdf3ObjectName(meaning);

        // massage units
        if (unit != null) {
          if (unit.equalsIgnoreCase("Proportion") || unit.equalsIgnoreCase("Numeric") || unit.equalsIgnoreCase("-"))
            unit = "";
          else {
            if (unit.startsWith("/")) unit = "1" + unit;
            unit = unit.trim();
            unit = StringUtil2.replace(unit, ' ', ".");
          }

        }
        this.unit = unit;
      }
    }

    @Override
    public int compareTo(TableEntry o) {
      return start - o.start;
    }

    @Override
    public String toString() {
      return "TableEntry{" + getTableId() +
              ", code='" + code + '\'' +
              ", start=" + start +
              ", stop=" + stop +
              ", number=" + number +
              ", meaning='" + meaning + '\'' +
              ", name='" + name + '\'' +
              ", unit='" + unit + '\'' +
              ", status='" + status + '\'' +
              '}';
    }

    public String getId() {
      return getTableId() + "." + code;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TableEntry that = (TableEntry) o;

      if (number != that.number) return false;
      if (start != that.start) return false;
      if (stop != that.stop) return false;
      if (code != null ? !code.equals(that.code) : that.code != null) return false;
      if (meaning != null ? !meaning.equals(that.meaning) : that.meaning != null) return false;
      if (name != null ? !name.equals(that.name) : that.name != null) return false;
      // if (status != null ? !status.equals(that.status) : that.status != null) return false;
      if (unit != null ? !unit.equals(that.unit) : that.unit != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start;
      result = 31 * result + stop;
      result = 31 * result + line;
      result = 31 * result + number;
      result = 31 * result + (code != null ? code.hashCode() : 0);
      result = 31 * result + (meaning != null ? meaning.hashCode() : 0);
      result = 31 * result + (name != null ? name.hashCode() : 0);
      result = 31 * result + (unit != null ? unit.hashCode() : 0);
      result = 31 * result + (status != null ? status.hashCode() : 0);
      return result;
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // debug

  public static void showDiff(GribTables gt1, GribTables gt2, boolean showDiff) throws IOException {
    int total = 0;
    int nsame = 0;
    int nsameIgn = 0;
    int ndiff = 0;
    int unknown = 0;

    System.out.printf("DIFFERENCES between %s and %s%n", gt1.name, gt2.name);
    for (GribCodeTableWmo gc1 : gt1.list) {

      GribCodeTableWmo gc2 = gt2.map.get(gc1.tableName);
      if (gc2 == null) {
        System.out.printf("1 table %s not found in %s%n", gc1.getTableId(), gt2.name);
        continue;
      }

      for (TableEntry p1 : gc1.entries) {
        TableEntry p2 = gc2.get(p1.start);
        if (p2 == null) {
          System.out.printf("2 code %s not found in %s%n", p1.getId(), gt2.name);
          continue;
        }

        if (showDiff && !p1.equals(p2)) {
          System.out.printf("3 %s not equal%n  %s%n%n", p1, p2);
        }
      }
    }
    System.out.printf("Total=%d same=%d sameIgn=%d dif=%d unknown=%d%n", total, nsame, nsameIgn, ndiff, unknown);
  }


  public static void showDiffFromCurrent(List<GribCodeTableWmo> tlist) throws IOException {
    int total = 0;
    int nsame = 0;
    int nsameIgn = 0;
    int ndiff = 0;
    int unknown = 0;

    System.out.printf("DIFFERENCES with current parameter table (now,org) %n");
    for (GribCodeTableWmo gt : tlist) {
      if (!gt.isParameter) continue;
      for (TableEntry p : gt.entries) {
        if (p.meaning.equalsIgnoreCase("Missing")) continue;
        if (p.start != p.stop) continue;

        GridParameter gp = ParameterTable.getParameter(gt.discipline, gt.category, p.start);
        String paramOrg = gp.getDescription();
        if (paramOrg.startsWith("Unknown")) {
          unknown++;
          continue;
        }

        String paramOrgM = munge(paramOrg);
        String paramM = munge(p.name);
        boolean same = paramOrgM.equals(paramM);
        if (same) nsame++;
        boolean sameIgnore = paramOrgM.equalsIgnoreCase(paramM);
        if (sameIgnore) nsameIgn++;
        else ndiff++;
        total++;
        String state = same ? "  " : (sameIgnore ? "* " : "**");
        if (!same && !sameIgnore)
          System.out.printf("%s%d %d %d%n %s%n %s%n", state, gt.discipline, gt.category, p.start, p.name, paramOrg);
      }
    }
    System.out.printf("Total=%d same=%d sameIgn=%d dif=%d unknown=%d%n", total, nsame, nsameIgn, ndiff, unknown);
  }

  static String munge(String org) {
    String result = StringUtil2.remove(org, "_");
    result = StringUtil2.remove(result, "-");
    return result;
  }

  public static void showTable(List<GribCodeTableWmo> tlist) throws IOException {

    for (GribCodeTableWmo gt : tlist) {
      System.out.printf("%d.%d (%d,%d) %s %n", gt.m1, gt.m2, gt.discipline, gt.category, gt.tableName);
      for (TableEntry p : gt.entries) {
        System.out.printf("  %s (%d-%d) = %s %n", p.code, p.start, p.stop, p.meaning);
      }
    }
  }


  public static void main(String arg[]) throws IOException {
    //GribTables gt52 = readGribCodes(Version.GRIB2_5_2_0);
    //showTable(gt52.list);
    //showDiffFromCurrent(gt52.list);

    GribTables gt61 = readGribCodes(Version.GRIB2_6_0_1);
    //showTable(gt61.list);
    showDiffFromCurrent(gt61.list);

    //showDiff(gt52, gt61, true);
    //System.out.printf("%n");
    //showDiff(gt61, gt52, false);
  }
}