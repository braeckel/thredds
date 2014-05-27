/* Copyright 2012, UCAR/Unidata.
   See the LICENSE file for more information.
*/

package dap4.ce;

import dap4.ce.CEAST;
import dap4.core.data.*;
import dap4.core.dmr.*;
import dap4.core.util.*;
import dap4.servlet.DapSerializer;

import java.util.*;

/**
 * A Constraint is a structure
 * containing a parsed representation
 * of a constraint expression.
 * Its purpose is define a subset of interest of a dataset.
 * <p/>
 * The constraint object is defined with respect to some underlying DMR.
 * It defines a subset view over that DMR in that it specifies
 * a set of declarations (variables, enums, dimensions, groups)
 * to be included.
 * For each such variable, the constraint specifies
 * any overriding of the dimensions of the variables.
 * Additionally, each variable (if appropriate) may have a filter expr.
 * <p/>
 * Thus, there are three 'sub' constraints within a full constraint.
 * 1. Referencing - is a variable from the underlying dataset
 * included in the constraint, directly (by
 * or indirectly: e.g. fields of a structure when only
 * the structure is referenced
 * 2. Projection - the actual values of a variable to be included
 * in the constraint; this is specified by a triple [start:stride:stop]
 * for each dimension of a variable.
 * 3. Selection (aka filters) - A predicate over the scalar fields
 * of a row of a Sequence; if it evaluates to true, then that
 * row matches the constraint.
 * <p/>
 * There are multiple ways to effect a constraint.
 * 1. Generate and test mode: the constraint is asked if a given
 * element matches the constraint. E.g.
 * a. For referencing, one might ask the constraint
 * if a given variable or field is in the constraint
 * b. For a projection filter, one might ask the constraint
 * if a given set of dimension indices match the projection.
 * c. For a selection filter, one might ask the constraint
 * if a given sequence row matches the filter predicate
 * 2. Iteration mode: the constraint provides an iterator
 * that returns the elements matching the constraint. E.g.
 * a. For referencing, the iterator would return all the
 * variables and fields referenced in the constraint.
 * b. For a projection filter, the iterator would return
 * the successive sets of indices of the projection,
 * or it could return the actual matching value.
 * c. For a selection filter, the iterator would return
 * either the row indices or the actual rows of a sequence
 * that matched the filter predicate
 * <p/>
 * 3. Read mode: Sometimes, it may be more efficient to let the
 * DataVariable object handle the constraint more directly. E.g.
 * a. For example, if the data variable was backed by a netcdf
 * file, then passing in the complete projection might be more
 * efficient than pulling values 1 by 1.
 * b. Similarly, if the sequence object had an associated btree,
 * then it would be more efficient to allow the sequence object
 * to evaluate the filter using the btree. Note that this
 * requires analysis of the filter expression to see if the
 * btree is usable.
 * <p/>
 * Ideally, we would allow all three modes, but for now, only
 * generate-and-test and iteration are implemented, and only a subset
 * of those. Specifically, iteration is provided for referencing,
 * projection, and selection (filters). Generate-and-test is provided for
 * referencing and selection. It is not provided for projection
 * (for now) because it essentially requires the inverse of iteration
 * and that is fairly tricky.
 */

public class CEConstraint implements Constraint
{
    //////////////////////////////////////////////////
    // Constants

    // Mnemonics
    static final public boolean EXPAND = true;

    static final String LBRACE = "{";
    static final String RBRACE = "}";


    //////////////////////////////////////////////////
    // Type Decls

    static protected class Universal extends CEConstraint
    {
        public Universal(DapDataset dmr)
        {
            super(dmr);
        }

        @Override
        public void addRedef(DapDimension dim, Slice slice)
        {
        }

        @Override
        public void addVariable(DapVariable var, List<Slice> slices)
        {
        }

        @Override
        public void addAttribute(DapNode node, DapAttribute attr)
        {
        }


        @Override
        public DapDimension getRedefDim(DapDimension orig)
        {
            return null;
        }

        @Override
        public List<Slice> getConstrainedSlices(DapVariable var)
        {
            try {
                return DapUtil.dimsetSlices(var.getDimensions());
            } catch (DapException de) {
                return null;
            }
        }


        public boolean
        references(DapNode node)
        {
            switch (node.getSort()) {
            case DIMENSION:
            case ENUMERATION:
            case ATOMICVARIABLE:
            case GRID:
            case SEQUENCE:
            case STRUCTURE:
            case GROUP:
            case DATASET:
                return true;
            default:
                break;
            }
            return false;
        }
    }

    static protected class Segment
    {
        CEConstraint parent;
        DapVariable var;
        List<Slice> slices; // projections slices for this variable
        List<DapDimension> dimset; // dimensions for the variable; including
        // redefs and anonymous derived from slices
        CEAST filter;

        Segment(DapVariable var)
        {
            this.var = var;
            this.slices = null; // added later
            this.filter = null; // added later
            this.dimset = null; // added later
        }

        void setDimset(List<DapDimension> dimset)
        {
            this.dimset = dimset;
        }

        void setSlices(List<Slice> slices)
        {
            this.slices = slices;
        }

        void setFilter(CEAST filter)
        {
            this.filter = filter;
        }

        public String toString()
        {
            StringBuilder buf = new StringBuilder();
            buf.append(var.getFQN());
            if (slices != null)
                for (int i = 0; i < slices.size(); i++) {
                    buf.append(slices.get(i).toString());
                }
            if (this.filter != null) {
                buf.append("|");
                buf.append(filter.toString());
            }
            return buf.toString();
        }
    }

    static protected class FilterIterator implements Iterator<DataRecord>
    {
        protected DapSequence seq;
        protected DataSequence data;
        protected long nrecords;
        protected CEAST filter;

        protected int recno;
        protected DataRecord current;

        public FilterIterator(DapSequence seq, DataSequence data, CEAST filter)
        {
            this.filter = filter;
            this.seq = seq;
            this.data = data;
            this.nrecords = data.getRecordCount();
            this.recno = 0; // actually recno of next record to read
            this.current = null;
        }

        // Iterator interface
        public boolean hasNext()
        {
            if (recno < nrecords)
                return false;
            try {
                // look for next matching record starting at recno
                if (filter == null) {
                    this.current = data.readRecord(this.recno);
                    this.recno++;
                    return true;
                } else for (; recno < nrecords; recno++) {
                    this.current = data.readRecord(this.recno);
                    if (matches(this.seq, this.current, filter))
                        return true;
                }
            } catch (DapException de) {
                return false;
            }
            this.current = null;
            return false;
        }

        public DataRecord next()
        {
            if (this.recno >= nrecords || this.current == null)
                throw new NoSuchElementException();
            return this.current;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        /**
         * Evaluate a filter with respect to a Sequence record.
         * Assumes the filter has been canonicalized so that
         * the lhs is a variable.
         *
         * @param seq    the template
         * @param record the record to evaluate
         * @throws DapException
         * @parem expr the filter
         * @returns true if the filter evaluates to true for this record
         */
        protected boolean
        matches(DapSequence seq, DataRecord record, CEAST expr)
                throws DapException
        {
            if (expr.sort == CEAST.Sort.EXPR) {
                switch (expr.lhs.sort) {
                case EXPR:
                    if (expr.op == CEAST.Operator.AND) {
                        boolean lhstrue = matches(seq, record, expr.lhs);
                        boolean rhstrue = matches(seq, record, expr.rhs);
                        return lhstrue && rhstrue;
                    } else {
                        Object lvalue = null;
                        Object rvalue = null;
                        assert (expr.lhs.sort == CEAST.Sort.SEGMENT);
                        lvalue = eval(seq, record, expr.lhs.name);
                        if (expr.rhs.sort == CEAST.Sort.SEGMENT)
                            rvalue = eval(seq, record, expr.rhs.name);
                        else
                            rvalue = expr.rhs.value;
                        int comparison = compare(lvalue, rvalue);
                        switch (expr.op) {
                        case LT:
                            return (comparison < 0);
                        case LE:
                            return (comparison <= 0);
                        case GT:
                            return (comparison > 0);
                        case GE:
                            return (comparison >= 0);
                        case EQ:
                            return (comparison == 0);
                        case NEQ:
                            return (comparison != 0);
                        case REQ:
                            return lvalue.toString().matches(rvalue.toString());
                        default:
                            assert false; // should never happen
                        }
                    }
                }
            }
            return false;
        }

        protected Object
        eval(DapSequence seq, DataRecord record, String field)
                throws DapException
        {
            DapVariable dapv = seq.findByName(field);
            if (dapv == null)
                throw new DapException("Unknown variable in filter: " + field);
            if (dapv.getSort() != DapSort.ATOMICVARIABLE)
                throw new DapException("Non-atomic variable in filter: " + field);
            if (dapv.getRank() > 0)
                throw new DapException("Non-scalar variable in filter: " + field);
            DataAtomic da = (DataAtomic) (record.readfield(field));
            return da.read(0);
        }

        protected int
        compare(Object lvalue, Object rvalue)
                throws DapException
        {
            if (lvalue instanceof String && rvalue instanceof String)
                return ((String) lvalue).compareTo((String) rvalue);
            if (lvalue instanceof Boolean && rvalue instanceof Boolean)
                return compare((Boolean) lvalue ? 1 : 0, (Boolean) rvalue ? 1 : 0);
            if (lvalue instanceof Double || lvalue instanceof Float
                    || rvalue instanceof Double || rvalue instanceof Float) {
                double d1 = ((Number) lvalue).doubleValue();
                double d2 = ((Number) lvalue).doubleValue();
                return Double.compare(d1, d2);
            } else {
                long l1 = ((Number) lvalue).longValue();
                long l2 = ((Number) rvalue).longValue();
                return Long.compare(l1, l2);
            }
        }
    }

    static protected class ReferenceIterator implements Iterator<DapNode>
    {

        //////////////////////////////////////////////////
        // Instance Variables

        protected CEConstraint ce;
        protected DapSort sort;

        List<DapNode> list = new ArrayList<>();

        /**
         * @param ce the constraint over which to iterate
         * @throws DapException
         */
        public ReferenceIterator(CEConstraint ce)
                throws DapException
        {
            this.ce = ce;
            this.sort = sort;
            list.addAll(ce.dimrefs);
            list.addAll(ce.enums);
            list.addAll(ce.variables);
        }

        //////////////////////////////////////////////////
        // Iterator Interface

        public boolean hasnext()
        {
        }

        public DapNode next()
        {
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

    }

    //////////////////////////////////////////////////
    // class variables and methods

    static public CEConstraint
    getUniversal(DapDataset dmr)
    {
        return new Universal(dmr);
    }

    //////////////////////////////////////////////////
    // Instance variables

    // Information given to us by the compiler
    protected DapDataset dmr = null; // Underlying DMR

    /**
     * "Map" of variables (at all levels) to be included
     * Maps variables -> associated slices
     * and is modified by computdimensions().
     * Note that we keep the original insertion order
     */

    protected List<Segment> segments = new ArrayList<>();

    /**
     * Also keep a raw list of variables
     */
    protected List<DapVariable> variables = new ArrayList<>();

    // Track redefs
    protected Map<DapDimension, Slice> redefslice = new HashMap<DapDimension, Slice>();

    // Hold any extra attributes
    protected Map<DapNode, List<DapAttribute>> attributes = new HashMap<DapNode, List<DapAttribute>>();
    // Computed information

    // Map original dimension to the redef
    protected Map<DapDimension, DapDimension> redef = new HashMap<DapDimension, DapDimension>();

    // list of all referenced original dimensions
    protected List<DapDimension> dimrefs = new ArrayList<>();

    // List of enumeration decls to be included
    protected List<DapEnum> enums = new ArrayList<>();

    // List of group decls to be included
    protected List<DapGroup> groups = new ArrayList<>();

    // List of referenced shared dimensions (including redefs)
    protected List<DapDimension> refdims = new ArrayList<>();

    protected boolean finished = false;

    //////////////////////////////////////////////////
    // Constructor(s)

    public CEConstraint()
    {
    }

    public CEConstraint(DapDataset dmr)
    {
        this.dmr = dmr;
    }

    //////////////////////////////////////////////////
    // Accessors

    public DapDataset
    getDMR()
    {
        return this.dmr;
    }

    public DapDimension getRedefDim(DapDimension orig)
    {
        return redef.get(orig);
    }

    protected List<Slice> getConstrainedSlices(DapVariable var)
    {
        Segment seg = findSegment(var);
        if (seg == null)
            return null;
        return seg.slices;
    }

    public void addRedef(DapDimension dim, Slice slice)
    {
        this.redefslice.put(dim, slice);
    }

    public void addVariable(DapVariable var, List<Slice> slices)
    {
        if (findVariable(var) < 0) {
	    Segment segment = new Segment(var);
            segment.setSlices(slices);
            this.segments.add(segment);
            this.variables.add(var);
	}
    }

    public void addAttribute(DapNode node, DapAttribute attr)
    {
        List<DapAttribute> attrs = this.attributes.get(node);
        if (attrs == null) {
            attrs = new ArrayList<DapAttribute>();
            this.attributes.put(node, attrs);
        }
        attrs.add(attr);
    }

    public void setFilter(DapVariable var, CEAST filter)
    {
        Segment seg = findSegment(var);
        if (seg != null)
            seg.filter = filter;
    }

    //////////////////////////////////////////////////
    // Standard

    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (!seg.var.isTopLevel())
                continue;
            if (!first) buf.append(";");
            first = false;
            dumpvar(seg, buf, false);
        }
        return buf.toString();
    }

    //////////////////////////////////////////////////
    // API

    /**
     * Finish creating this Constraint.
     *
     * @param expand expand structures in the underlying view
     * @throws DapException
     * @returns this - fluent interface
     */
    public CEConstraint
    finish(boolean expand)
            throws DapException
    {
        if (!finished) {
            finished = true;
            expandCompoundTypes();
            // order is important
            computeenums();
            computedimensions();
            computegroups();
        }
        return this;
    }

    /**
     * Convert the view to a constraint string suitable
     * for use in a URL, except not URL encoded.
     *
     * @return constraint string
     */
    public String toConstraintString()
    {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (!seg.var.isTopLevel())
                continue;
            if (!first) buf.append(";");
            first = false;
            dumpvar(seg, buf, true);
        }
        return buf.toString();
    }

    /**
     * Recursive helper for tostring/toConstraintString
     *
     * @param seg
     * @param buf
     * @param forconstraint
     */
    protected void
    dumpvar(Segment seg, StringBuilder buf, boolean forconstraint)
    {
        if (seg.var.isTopLevel())
            buf.append(seg.var.getFQN());
        else
            buf.append(seg.var.getShortName());
        // Add any slices
        List<Slice> slices = seg.slices;
        List<DapDimension> dimset = seg.var.getDimensions();
        if (slices != null)
            assert dimset.size() == slices.size();
        for (int i = 0; i < dimset.size(); i++) {
            Slice slice = slices.get(i);
            DapDimension dim = dimset.get(i);
            if (!slice.isConstrained() && slice.isWhole())
                buf.append("[]");
            else
                buf.append(forconstraint ? slice.toConstraintString() : slice.toString());
        }

        // if the var is atomic, then we are done
        if (seg.var.getSort() == DapSort.ATOMICVARIABLE)
            return;
        // If structure and all fields are in the view, then done
        if (seg.var.getSort() == DapSort.STRUCTURE
                && isWholeStructure((DapStructure) seg.var))
            return;
        // Need to insert {...} and recurse
        buf.append(LBRACE);
        DapStructure struct = (DapStructure) seg.var;
        boolean first = true;
        for (DapVariable field : struct.getFields()) {
            if (!first) buf.append(";");
            first = false;
            Segment fseg = findSegment(field);
            dumpvar(fseg, buf, forconstraint);
        }
        buf.append(RBRACE);
    }


    //////////////////////////////////////////////////
    // Reference processing

    /**
     * Reference X match
     *
     * @param node to test
     * @return true if node is referenced by this constraint
     */

    public boolean
    references(DapNode node)
    {
        boolean isref = false;
        switch (node.getSort()) {
        case DIMENSION:
            DapDimension dim = this.redef.get((DapDimension) node);
            if (dim == null) dim = (DapDimension) node;
            isref = this.dimrefs.contains(dim);
            break;
        case ENUMERATION:
            isref = (this.enums.contains((DapEnum) node));
            break;
        case ATOMICVARIABLE:
        case GRID:
        case SEQUENCE:
        case STRUCTURE:
            isref = (findVariable((DapVariable) node) >= 0);
            break;
        case GROUP:
        case DATASET:
            isref = (this.groups.contains((DapGroup) node));
            break;
        default:
            break;
        }
        return isref;
    }

    /**
     * Reference X Iterator
     * Iterate over the variables and return
     * those that are referenced. The order of
     * return is preorder.
     * Inputs:
     * 1.  the variable whose slices are to be iterated.
     *
     * @param dataset
     * @return ReferenceIterator
     * @throws DapException if could not create.
     */

    public Odometer
    referenceIterator(DapDataset dataset)
            throws DapException
    {
        // Create the appropriate odometer for
        // the slices of the variable
        List<Slice> slices = DapUtil.dimsetSlices(var.getDimensions());
        Odometer odom = new Odometer(slices);
        return odom;
    }

    //////////////////////////////////////////////////
    // Projection processing

    /**
     * Projection X match
     * This is actually rather difficult because it requires
     * sort of the inverse of an odometer. For this reason,
     * It's implementation is deferred.
     */

    /**
     * Projection X Iterator
     * This basically returns an odometer that
     * will iterate over the appropriate values.
     *
     * @param var over whose dimensions to iterate
     * @throws DapException
     */

    public Odometer
    projectionIterator(DapVariable var)
            throws DapException
    {
        return new Odometer(getConstrainedSlices(var),
    }

    //////////////////////////////////////////////////
    // Selection (Filter) processing

    /**
     * Selection X match
     */

    /**
     * Selection X Iterator
     * Filter evaluation using an iterator.
     * The iterator evaluates records from a sequence one-by-one
     * and returns the next one that matches the filter.
     * In order to evaluate a record, we need as input:
     * 1.  the DapSequence from which the free
     * variables in the filter are taken.
     * 2.  the DataRecord to evaluate
     *
     * @param dapseq
     * @param dataseq
     */

    public FilterIterator
    filterIterator(DapSequence dapseq, DataSequence dataseq)
            throws DapException
    {
        // Locate the filter for this sequence
        Segment seg = findSegment(dapseq);
        return new FilterIterator(dapseq, dataseq, seg.filter);
    }


    //////////////////////////////////////////////////
    // Utilities

    /* Search the set of variables */
    protected int findVariable(DapVariable var)
    {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i) == var)
                return i;
        }
        return -1;
    }

    protected Segment findSegment(DapVariable var)
    {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).var == var)
                return segments.get(i);
        }
        return null;
    }

    /**
     * See if all the fields in this structure are part of the
     * view; this must be done recursively.
     */
    protected boolean
    isWholeStructure(DapStructure struct)
    {
        for (DapVariable field : struct.getFields()) {
            if (findVariable(field) < 0)
                return false;
            if (field.getSort() == DapSort.STRUCTURE) {
                // recurse
                if (!isWholeStructure((DapStructure) field))
                    return false;
            }
        }
        return true;
    }

    /**
     * Locate each unexpanded Structure|Sequence and:
     * 1. check that none of its fields is referenced => do not expand
     * 2. add all of its fields as leaves
     * Note that #2 may end up adding additional leaf structs &/or seqs
     *
     * @throws DapException
     */
    protected void
    expandCompoundTypes()
    {
        // Create a queue of unprocessed leaf compounds
        Queue<DapVariable> queue = new ArrayDeque<DapVariable>();
        for (int i = 0; i < variables.size(); i++) {
            DapVariable var = variables.get(i);
            if (!var.isTopLevel())
                continue;
            // prime the queue
            if (var.getSort() == DapSort.STRUCTURE || var.getSort() == DapSort.SEQUENCE) {
                DapStructure struct = (DapStructure) var; // remember Sequence subclass Structure
                if (expansionCount(struct) == 0)
                    queue.add(var);
            }
        }
        // Process the queue in prefix order
        while (queue.size() > 0) {
            DapVariable vvstruct = queue.remove();
            DapStructure dstruct = (DapStructure) vvstruct;
            for (DapVariable field : dstruct.getFields()) {
                if (findVariable(field) < 0) {
                    // Add field as leaf
                    this.segments.add(new Segment(field));
                    this.variables.add(field);
                }
                if (field.getSort() == DapSort.STRUCTURE || field.getSort() == DapSort.SEQUENCE) {
                    if (expansionCount((DapStructure) field) == 0)
                        queue.add(field);
                }
            }
        }
    }

    /**
     * Count the number of fields of a structure that
     * already in this view.
     *
     * @param struct the dapstructure to check
     * @return # of fields in this view
     * @throws DapException
     */

    protected int
    expansionCount(DapStructure struct)
    {
        int count = 0;
        for (DapVariable field : struct.getFields()) {
            if (findVariable(field) >= 0) count++;
        }
        return count;
    }

    //////////////////////////////////////////////////
    // Utilities for computing inferred information

    /**
     * Compute dimension related information
     * using slicing and redef info.
     * In effect, this is where projection constraints
     * are applied
     * <p/>
     * Assume that the constraint compiler has given us the following info:
     * <ol>
     * <li> A list of the variables to include.
     * <li> A pair (DapDimension,Slice) for each redef
     * <li> For each variable in #1, a list of slices
     * taken from the constraint expression
     * </ol>
     * <p/>
     * Two products will be produced.
     * <ol>
     * <li> The variables map will be modified so that the
     * slices properly reflect any original or redef dimensions.
     * <li> A set, dimrefs, of all referenced original dimensions.
     * </ol>
     * <p/>
     * The processing is as follows
     * <ol>
     * <li> For each redef create a new redef dimension
     * <li> For each variable:
     * <ol>
     * <li> if the variable is scalar, do nothing.
     * <li> if the variable has no associated slices, then make its
     * new dimensions be the original dimensions.
     * <li> otherwise, walk the slices and create new dimensions
     * from them; use redefs where indicated
     * <li>
     * </ol>
     * </ol>
     */
    protected void
    computedimensions()
            throws DapException
    {
        // Build the redefmap
        for (DapDimension key : redefslice.keySet()) {
            Slice slice = redefslice.get(key);
            DapDimension newdim = (DapDimension) key.clone();
            newdim.setSize(slice.getCount());
            redef.put(key, newdim);
        }

        // Process each variable
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (seg.var.getRank() == 0)
                continue;
            List<Slice> slices = seg.slices;
            List<DapDimension> orig = seg.var.getDimensions();
            List<DapDimension> newdims = new ArrayList<>();
            // If the slice list is short then pad it with
            // default slices
            if (slices == null)
                slices = new ArrayList<Slice>();
            while (slices.size() < orig.size()) // pad
            {
                slices.add(new Slice().setConstrained(false));
            }
            assert (slices != null && slices.size() == orig.size());
            for (int j = 0; j < slices.size(); j++) {
                Slice slice = slices.get(j);
                DapDimension dim0 = orig.get(j);
                DapDimension newdim = redef.get(dim0);
                if (newdim == null)
		    newdim = dim0;
                if (slice.incomplete())  // fill in the undefined last value
                    slice.complete(newdim);
                Slice newslice = null;
                if(slice.isConstrained()) {
		    // Construct an anonymous dimension for this slice
		    newdim = new DapDimension(slice.getCount());
		} else { // replace with a new slice from the dim
                    newslice = new Slice().fill(newdim);
                    if (newslice != null) {
                        // track set of referenced non-anonymous dimensions
                        if (!dimrefs.contains(dim0)) dimrefs.add(dim0);
                        slices.set(j, newslice);
                    }
                }
		// record the dimension per variable
		newdims.add(newdim);
            }
	    seg.setDimset(newdims);
        }
    }

    /**
     * Walk all the included variables and accumulate
     * the referenced enums
     */
    protected void computeenums()
    {
        for (int i = 0; i < variables.size(); i++) {
            DapVariable var = variables.get(i);
            if (var.getSort() != DapSort.ATOMICVARIABLE)
                continue;
            DapType daptype = var.getBaseType();
            if (!daptype.isEnumType())
                continue;
            if (!this.enums.contains((DapEnum) daptype))
                this.enums.add((DapEnum) daptype);
        }
    }

    /**
     * Walk all the included declarations
     * and accumulate the set of referenced groups
     */
    protected void computegroups()
    {
        // 1. variables
        for (int i = 0; i < variables.size(); i++) {
            DapVariable var = variables.get(i);
            List<DapGroup> path = var.getGroupPath();
            for (DapGroup group : path) {
                if (!this.groups.contains(group))
                    this.groups.add(group);
            }
        }
        // 2. Dimensions
        for (DapDimension dim : this.dimrefs) {
            if (!dim.isShared())
                continue;
            List<DapGroup> path = dim.getGroupPath();
            for (DapGroup group : path) {
                if (!this.groups.contains(group))
                    this.groups.add(group);
            }
        }
        // 2. enumerations
        for (DapEnum en : this.enums) {
            List<DapGroup> path = en.getGroupPath();
            for (DapGroup group : path) {
                if (!this.groups.contains(group))
                    this.groups.add(group);
            }
        }
    }

}
