package au.com.nicta.openshapa.views.discrete;

import au.com.nicta.openshapa.db.DataCell;
import au.com.nicta.openshapa.db.Matrix;
import au.com.nicta.openshapa.db.NominalDataValue;
import au.com.nicta.openshapa.db.SystemErrorException;
import java.awt.event.KeyEvent;
import org.apache.log4j.Logger;

/**
 * This class is the view representation of a NominalDataValue as stored within
 * the database.
 *
 * @author cfreeman
 */
public final class NominalDataValueView extends DataValueView {

    /** The logger for NominalDataValueView. */
    private static Logger logger = Logger.getLogger(NominalDataValueView.class);

    /**
     * Constructor.
     *
     * @param cellSelection The parent selection for spreadsheet cells.
     * @param cell The parent datacell for the nominal data value that this view
     * represents.
     * @param matrix The parent matrix for the nominal data value that this view
     * represents.
     * @param matrixIndex The index of the NominalDataValue within the above
     * parent matrix that this view represents.
     * @param editable Is the dataValueView editable by the user? True if the
     * value is permitted to be altered by the user. False otherwise.
     */
    NominalDataValueView(final Selector cellSelection,
                         final DataCell cell,
                         final Matrix matrix,
                         final int matrixIndex,
                         final boolean editable) {
        super(cellSelection, cell, matrix, matrixIndex, editable);
    }

    /**
     * The action to invoke when a key is typed.
     *
     * @param e The KeyEvent that triggered this action.
     */
    public void keyTyped(KeyEvent e) {
        NominalDataValue ndv = (NominalDataValue) getValue();

        // The backspace key removes digits from behind the caret.
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
                   && e.getKeyChar() == '\u0008') {
            this.removeBehindCaret();
            e.consume();

        // The delete key removes digits ahead of the caret.
        } else if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_UNKNOWN
                   && e.getKeyChar() == '\u007F') {
            this.removeAheadOfCaret();
            e.consume();

        // Just a regular vanilla keystroke - insert it into text field.
        } else if (!e.isMetaDown() && !e.isControlDown()
                   && (e.getKeyChar() != ')' && e.getKeyChar() != '('
                       && e.getKeyChar() != '<' && e.getKeyChar() != '>'
                       && e.getKeyChar() != '|' && e.getKeyChar() != ','
                       && e.getKeyChar() != ';')) {
            this.removeSelectedText();
            StringBuffer currentValue = new StringBuffer(getText());
            currentValue.insert(getCaretPosition(), e.getKeyChar());
            advanceCaret(); // Advance caret over the top of the new char.
            storeCaretPosition();
            this.setText(currentValue.toString());
            restoreCaretPosition();
            e.consume();

        
        } else {
            e.consume();
        }

        // Push the character changes into the database.
        try {
            ndv.setItsValue(this.getText());
        } catch (SystemErrorException se) {
            logger.error("Unable to edit text string", se);
        }
        updateDatabase();
    }
}
