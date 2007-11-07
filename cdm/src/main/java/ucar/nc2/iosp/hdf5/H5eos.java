/*
 * Copyright 1997-2007 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2.iosp.hdf5;

import ucar.nc2.NetcdfFile;
import ucar.nc2.Group;
import ucar.nc2.Variable;
import ucar.nc2.Dimension;
import ucar.ma2.Array;
import ucar.ma2.ArrayChar;

import java.io.IOException;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;

/**
 * @author caron
 * @since Jul 23, 2007
 */
public class H5eos {

  static void parse(NetcdfFile ncfile) throws IOException {
    Group rootg = ncfile.getRootGroup();

    Group eosInfo = rootg.findGroup("HDFEOS_INFORMATION");
    Variable structMetadata = eosInfo.findVariable("StructMetadata.0");
    if (structMetadata == null) return;

    // read entire array
    Array A = structMetadata.read();

    ArrayChar ca = (ArrayChar) A;
    String sval = ca.getString();

    ODLparser2 parser = new ODLparser2();
    Element root = parser.parseFromString(sval);

    // LOOK could use XPath
    Element swathStructure = root.getChild("SwathStructure");
    Element swath1 = swathStructure.getChild("SWATH_1");
    String swathName = swath1.getChild("SwathName").getText();

    // global Dimensions
    Element d = swath1.getChild("Dimension");
    List<Element> dims = (List<Element>) d.getChildren();
    for (Element elem : dims) {
      String name = elem.getChild("DimensionName").getText();
      String sizeS = elem.getChild("Size").getText();
      int length = Integer.parseInt(sizeS);
      Dimension dim = new Dimension(name, length);
      rootg.addDimension(dim);
    }

    Group eos = rootg.findGroup("HDFEOS").findGroup("SWATHS");

        // Geolocation Variables
    Group gloc = eos.findGroup(swathName).findGroup("Geolocation_Fields");

    Element floc = swath1.getChild("GeoField");
    List<Element> varsLoc = (List<Element>) floc.getChildren();
    for (Element elem : varsLoc) {
      String varname = elem.getChild("GeoFieldName").getText();
      Variable v = gloc.findVariable( varname);
      assert v != null : varname;

      StringBuffer sbuff = new StringBuffer();
      Element dimList = elem.getChild("DimList");
      List<Element> values = (List<Element>) dimList.getChildren("value");
      for (Element value : values) {
        sbuff.append( value.getText());
        sbuff.append( " ");
      }
      v.setDimensions( sbuff.toString()); // livin dangerously
    }

    // Variable Dimensions
    Group g = eos.findGroup(swathName).findGroup("Data_Fields");

    Element f = swath1.getChild("DataField");
    List<Element> vars = (List<Element>) f.getChildren();
    for (Element elem : vars) {
      String varname = elem.getChild("DataFieldName").getText();
      Variable v = g.findVariable( varname);
      assert v != null : varname;

      StringBuffer sbuff = new StringBuffer();
      Element dimList = elem.getChild("DimList");
      List<Element> values = (List<Element>) dimList.getChildren("value");
      for (Element value : values) {
        sbuff.append( value.getText());
        sbuff.append( " ");
      }
      v.setDimensions( sbuff.toString()); // livin dangerously
    }
  }

}
