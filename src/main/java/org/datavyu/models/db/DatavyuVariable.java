/*
 * Copyright (c) 2011 Datavyu Foundation, http://datavyu.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.datavyu.models.db;

import java.util.*;

/**
 * Maps a variable object to a mongo powered datastore.
 */
public final class DatavyuVariable implements Variable {
    // All the listeners for variables in teh datastore.
    static Map<UUID, List<VariableListener>> allListeners =
            new HashMap<UUID, List<VariableListener>>();
    final private UUID variableId = UUID.randomUUID();
    private List<Cell> cells = new ArrayList<Cell>();
    private Argument type = null;
    private Boolean selected;
    private Boolean highlighted;
    private Boolean hidden;
    private String name;

    private static CellCompartor cellCompartor = new CellCompartor();

    /**
     * @param variableId The ID of the variable we want the listeners for.
     *
     * @return The list of listeners for the specified variableId.
     */
    private static List<VariableListener> getListeners(UUID variableId) {
        List<VariableListener> result = allListeners.get(variableId);
        if (result == null) {
            result = new ArrayList<VariableListener>();
            allListeners.put(variableId, result);
        }

        return result;
    }

    /**
     * Removes all the listeners for all the variables.
     */
    public static void clearListeners() {
        allListeners.clear();
    }

    /**
     * Default constructor.
     */
    public DatavyuVariable() {
    }

    /**
     * Constructor.
     *
     * @param name The name to use for the variable being constructed.
     * @param type The type to use for the variable being constructed.
     */
    public DatavyuVariable(String name, Argument type) throws UserWarningException {
        this.setName(name);
        this.setVariableType(type);
        this.setHidden(false);
        this.setSelected(true);

        DatavyuDatastore.markDBAsChanged();
    }

    public void addCell(Cell cell) {
        if (cell.getValue().getArgument() == this.getVariableType()) {
            cells.add(cell);
        }
    }



    /**
     * @return The internal ID (mongo id) for this variable.
     */
    public UUID getID() {
        return variableId;
    }

    @Override
    public Cell createCell() {
        Cell c = new DatavyuCell(this, this.getVariableType());

        cells.add(c);

        for(VariableListener vl : getListeners(getID()) ) {
            vl.cellInserted(c);
        }

        DatavyuDatastore.markDBAsChanged();
        return c;
    }

    @Override
    public void removeCell(final Cell cell) {
        cells.remove(cell);

        DatavyuDatastore.markDBAsChanged();

        for(VariableListener vl : getListeners(getID()) ) {
            vl.cellRemoved(cell);
        }

    }

    @Override
    public List<Cell> getCells() {
        return cells;
    }

    @Override
    public Cell getCellTemporally(final int index) {
        Collections.sort(cells, cellCompartor);
        return cells.get(index);
    }

    @Override
    public Argument getVariableType() {
        return type;
    }

    @Override
    public void setVariableType(final Argument newType) {
        type = newType;
    }

    @Override
    public List<Cell> getCellsTemporally() {
        Collections.sort(cells, cellCompartor);
        return cells;
    }

    @Override
    public boolean contains(final Cell c) {
        return cells.contains(c);
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setHidden(final boolean hidden) {
        this.hidden = hidden;

        for(VariableListener vl : getListeners(getID()) ) {
            vl.visibilityChanged(hidden);
        }
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String newName) throws UserWarningException {
        // Pre-conditions, the newName must have at least one character.
        if (newName.length() < 1) {
            throw new UserWarningException("Unable to add variable, a name must be supplied.");
        }

        // Pre-conditions, check to make sure newName doesn't contain invalid chars.
        if (newName.contains("(") || newName.contains(")") || newName.contains("<") || newName.contains(">") || newName.contains(",") || newName.contains("\"")) {
            throw new UserWarningException("Unable to add variable, name must not contain any: ') ( > < , \"'");
        }

        this.name = newName;

        for(VariableListener vl : getListeners(getID()) ) {
            vl.nameChanged(newName);
        }
    }

    @Override
    public Argument addArgument(final Argument.Type type) {
        Argument arg = getVariableType();
        Argument child = arg.addChildArgument(type);

        for(Cell cell : getCells()) {
            cell.addMatrixValue(child);
        }

        this.setVariableType(arg);
        return arg.childArguments.get(arg.childArguments.size()-1);
    }

    @Override
    public void moveArgument(final int old_index, final int new_index) {
        Argument arg = getVariableType();

        // Test to see if this is out of bounds
        if(new_index > arg.childArguments.size() - 1 || new_index < 0) {
            return;
        }

        Argument moved_arg = arg.childArguments.get(old_index);
        arg.childArguments.remove(moved_arg);
        arg.childArguments.add(new_index, moved_arg);

        // Move in all cells
        for(Cell cell : getCells()) {
            cell.moveMatrixValue(old_index, new_index);
        }
        this.setVariableType(arg);
    }

    @Override
    public void moveArgument(final String name, final int new_index) {
        int old_index = getArgumentIndex(name);
        moveArgument(old_index, new_index);
    }

    @Override
    public void removeArgument(final String name) {
        Argument arg = getVariableType();
        int arg_index = getArgumentIndex(name);
        arg.childArguments.remove(arg_index);

        // Now send this change to the cells
        for(Cell cell : getCells()) {
            cell.removeMatrixValue(arg_index);
        }

        this.setVariableType(arg);
    }

    @Override
    public int getArgumentIndex(final String name) {
        Argument arg = getVariableType();
        for(int i = 0; i < arg.childArguments.size(); i++) {
            if(arg.childArguments.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addListener(final VariableListener listener) {
        getListeners(getID()).add(listener);
    }

    @Override
    public void removeListener(final VariableListener listener) {
        getListeners(getID()).remove(listener);
    }
}