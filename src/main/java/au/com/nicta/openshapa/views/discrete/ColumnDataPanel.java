package au.com.nicta.openshapa.views.discrete;

import au.com.nicta.openshapa.db.DataCell;
import au.com.nicta.openshapa.db.DataColumn;
import au.com.nicta.openshapa.db.SystemErrorException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import org.apache.log4j.Logger;

/**
 * ColumnDataPanel panel that contains the SpreadsheetCell panels.
 * @author swhitcher
 */
public class ColumnDataPanel extends javax.swing.JPanel {

    private Spreadsheet spreadsheet;

    /** Logger for this class. */
    private static Logger logger = Logger.getLogger(ColumnDataPanel.class);

    /** filler box for use when there are no datacells. */
    private Component filler;

    /** Creates new ColumnDataPanel panel. */
    public ColumnDataPanel(Spreadsheet sheet) {
        initComponents();

        // keep a ref to a Box Layout Manager
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setAlignmentY(TOP_ALIGNMENT);
        this.setBorder(BorderFactory.createLineBorder(Color.black));

        // hold onto a filler box for when there are no datacells
        filler = Box.createRigidArea(new Dimension(200, 0));

        spreadsheet = sheet;
    }

    /**
     * Creates new ColumnDataPanel panel.
     * @param dbColumn Database column to display.
     */
    public ColumnDataPanel(final Spreadsheet sheet, final DataColumn dbColumn) {
        this(sheet);

        updateComponents(dbColumn);
    }

    /**
     * updateComponents. Called when the SpreadsheetCell panels need to be
     * built and added to this Column panel.
     * @param dbColumn Database column to display.
     */
    private void updateComponents(final DataColumn dbColumn) {
        try {
            // TODO: getNumCells should be zero based. likewise getCell
            int numCells = dbColumn.getNumCells();

            // add or remove filler
            if (numCells == 0) {
                add(filler);
            } else {
                remove(filler);
            }

            // traverse and build the cells
            for (int j = 1; j <= numCells; j++) {
                DataCell dc = (DataCell) dbColumn.getDB()
                                    .getCell(dbColumn.getID(), j);

                SpreadsheetCell sc =
                                new SpreadsheetCell(dbColumn.getDB(), dc,
                                                spreadsheet.getCellSelector());
                sc.setSize(200, 50);
                add(sc);
            }
        } catch (SystemErrorException e) {
           logger.error("Failed to populate Spreadsheet.", e);
        }
    }

    /**
     * Brute force rebuild of all cells in the Spreadsheet column.
     * @param dbColumn The column to display.
     */
    public final void rebuildAll(final DataColumn dbColumn) {
        removeAll();
        // would not work without setting the size to something
        // I guess after first building the panel with no cells
        // the size goes to 0 by 0 and never regrows after that
        setSize(200, 1000);
        updateComponents(dbColumn);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setName("Form"); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
