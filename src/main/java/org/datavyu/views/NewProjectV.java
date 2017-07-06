/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.views;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.NewProjectC;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;

import java.awt.*;


/**
 * The dialog for users to create a new project.
 */
public final class NewProjectV extends DatavyuDialog {

    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(NewProjectV.class);

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField nameField;
    private javax.swing.JButton okButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Creates new form NewDatabaseView.
     *
     * @param parent The parent of this JDialog.
     * @param modal  Is this dialog modal or not?
     */
    public NewProjectV(final Frame parent, final boolean modal) {
        super(parent, modal);
        LOGGER.info("newProj - show");
        initComponents();

        // Need to set a unique name so that we save and restore session data
        // i.e. window size, position, etc.
        setName(this.getClass().getSimpleName());
        getRootPane().setDefaultButton(okButton);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        nameField = new javax.swing.JTextField();
        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(org.datavyu.Datavyu.class).getContext().getResourceMap(NewProjectV.class);
        setTitle(resourceMap.getString("Form.title")); // NOI18N
        setName("Form"); // NOI18N
        setResizable(false);

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setToolTipText(resourceMap.getString("jLabel1.toolTipText")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        nameField.setName("nameField"); // NOI18N

        cancelButton.setText(resourceMap.getString("cancelButton.text")); // NOI18N
        cancelButton.setName("cancelButton"); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        okButton.setText(resourceMap.getString("okButton.text")); // NOI18N
        okButton.setName("okButton"); // NOI18N
        okButton.setPreferredSize(new java.awt.Dimension(65, 23));
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .add(30, 30, 30)
                        .add(nameField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 274, Short.MAX_VALUE))
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(okButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(nameField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(27, 27, 27)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(cancelButton)
                    .add(okButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * The action to invoke when a user clicks on the CANCEL button.
     *
     * @param evt The event that triggered this action
     */
    private void cancelButtonActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    } // GEN-LAST:event_cancelButtonActionPerformed

    /**
     * The action to invoke when a user clicks on the OK button.
     *
     * @param evt The event that triggered this action.
     */
    private void okButtonActionPerformed(final java.awt.event.ActionEvent evt) { // GEN-FIRST:event_okButtonActionPerformed
        LOGGER.info("create new project");
        ResourceMap r = Application.getInstance(Datavyu.class).getContext().getResourceMap(NewProjectV.class);
        DatavyuView s = (DatavyuView) Datavyu.getView();

        // clear the contents of the existing spreadsheet.
        Datavyu.getProjectController().setLastCreatedCell(null);

        if (!isValidProjectName(getProjectName())) {
            Datavyu.getApplication().showWarningDialog(r.getString("Error.invalidName"));
            dispose();
            new NewProjectC();
        } else {

            s.createNewSpreadsheet(getProjectName());

            // The DB we just created doesn't really have any unsaved changes.
            Datavyu.getProjectController().getDataStore().markAsUnchanged();
            dispose();
        }

//        Datavyu.getApplication().resetApp();

        // BugzID:2411 - Show data controller after creating a new project.
//        Datavyu.getApplication().show(Datavyu.getDataController());
    } // GEN-LAST:event_okButtonActionPerformed

    private boolean isValidProjectName(final String name) {
        if (name == null) {
            return false;
        }

        if (name.length() == 0) {
            return false;
        }

        return true;
    }

    /**
     * @return The new name of the database as specified by the user.
     */
    public String getProjectName() {
        return nameField.getText();
    }

}
