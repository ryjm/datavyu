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

import javax.swing.*;
import java.awt.*;

/**
 * The dialog for the scripting console. Renders output from scripts and other
 * things which dumps things to the console. Implemented as a singleton. Only
 * A single console is used in Datavyu.
 */
public final class ConsoleV extends DatavyuDialog {

    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(ConsoleV.class);

    /**
     * The instance of the console.
     */
    private static ConsoleV instance;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JTextArea console;
    private javax.swing.JScrollPane jScrollPane1;

    /**
     * Constructor.
     *
     * @param parent The parent of this dialog.
     * @param modal  Is the scripting console modal or not?
     */
    public ConsoleV(final java.awt.Frame parent,
                    final boolean modal) {
        super(parent, modal);
        initComponents();
        setName(this.getClass().getSimpleName());
        console.setFont(new Font("Monospaced", Font.PLAIN, 16));
    }

    /**
     * @return The single instance of the console viewer.
     */
    public static ConsoleV getInstance() {
        if (instance == null) {
            JFrame mainFrame = Datavyu.getApplication().getMainFrame();
            instance = new ConsoleV(mainFrame, false);
        }

        return instance;
    }

    /**
     * @return The text area that makes up the console.
     */
    public JTextArea getConsole() {
        return this.console;
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        console = new javax.swing.JTextArea();
        closeButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(org.datavyu.Datavyu.class).getContext().getResourceMap(ConsoleV.class);
        setTitle(resourceMap.getString("Form.title"));
        setName("Form");

        jScrollPane1.setName("jScrollPane1");

        console.setColumns(20);
        console.setRows(5);
        console.setMinimumSize(new java.awt.Dimension(200, 150));
        console.setName("console");
        jScrollPane1.setViewportView(console);

        closeButton.setText(resourceMap.getString("closeButton.text"));
        closeButton.setName("closeButton");
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1, GroupLayout.Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                                        .addComponent(closeButton, GroupLayout.Alignment.TRAILING))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jScrollPane1, GroupLayout.DEFAULT_SIZE, 249, Short.MAX_VALUE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(closeButton)
                                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * The action to invoke when the user clicks on the close button.
     *
     * @param evt The event that triggered this action.
     */
    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        try {
            getInstance().dispose();
            getInstance().finalize();
            instance = null;

            // Whoops, unable to destroy dialog correctly.
        } catch (Throwable e) {
            LOGGER.error("Unable to release window NewVariableV.", e);
        }
    }//GEN-LAST:event_closeButtonActionPerformed
    // End of variables declaration//GEN-END:variables
}
