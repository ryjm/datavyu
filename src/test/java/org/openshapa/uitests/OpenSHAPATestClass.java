/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */

package org.openshapa.uitests;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import java.util.concurrent.TimeUnit;

import javax.swing.JDialog;

import junitx.util.PrivateAccessor;

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JFileChooserFixture;
import org.fest.swing.fixture.JOptionPaneFixture;
import org.fest.swing.fixture.OpenSHAPAFrameFixture;
import org.fest.swing.launcher.ApplicationLauncher;
import org.fest.swing.timing.Timeout;
import org.fest.swing.util.Platform;

import org.openshapa.Configuration;
import org.openshapa.OpenSHAPA;

import org.openshapa.util.ConfigProperties;

import org.openshapa.views.NewProjectV;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;


/**
 * GUI Test class for OpenSHAPA. All OpenSHAPA Fest tests must extend this
 * class.
 */
public class OpenSHAPATestClass {

    static {

        try {

            ConfigProperties p = (ConfigProperties) PrivateAccessor.getField(
                    Configuration.getInstance(), "properties");
            p.setCanSendLogs(false);
        } catch (Exception e) {
            System.err.println("Unable to overide sending usage logs");
        }
    }

    /** Main Frame fixture for use by all tests. */
    protected OpenSHAPAFrameFixture mainFrameFixture;

    /** Constructor nulls the mainFrame Fixture. */
    public OpenSHAPATestClass() {
        mainFrameFixture = null;
    }

    /**
     * Starts openSHAPA.
     */
    @BeforeSuite protected final void startApplication() {
        System.err.println("Starting Application.");

        OpenSHAPAFrameFixture fixture;

        // Launch OpenSHAPA, this happens once per test class.
        ApplicationLauncher.application(OpenSHAPA.class).start();
        fixture = new OpenSHAPAFrameFixture("mainFrame");
        fixture.robot.waitForIdle();
        fixture.requireVisible();
        fixture.maximize();
        fixture.moveToFront();

        OpenSHAPAInstance.setFixture(fixture);
        // ScreenshotOnFailureListener sofl = new ScreenshotOnFailureListener();
    }

    /**
     * Restarts the application between tests. Achieves this by using File->New
     */
    @AfterMethod protected final void restartApplication() {
        System.err.println("restarting Application.");

        //OpenSHAPA.getApplication().resetApp();
        OpenSHAPA.getApplication().closeOpenedWindows();

        mainFrameFixture = OpenSHAPAInstance.getFixture();

        //Try and close any filechoosers that are open
        try {
            JFileChooserFixture jfcf = mainFrameFixture.fileChooser();
            jfcf.cancel();
        } catch (Exception e) {
            //Do nothing
        }

        // Create a new project, this is for the discard changes dialog.
        if (Platform.isOSX()) {
            mainFrameFixture.pressAndReleaseKey(KeyPressInfo.keyCode(
                    KeyEvent.VK_N).modifiers(InputEvent.META_MASK));
        } else {
            mainFrameFixture.menuItemWithPath("File", "New").click();
        }

        try {
            JOptionPaneFixture warning = mainFrameFixture.optionPane();
            warning.requireTitle("Unsaved changes");
            warning.buttonWithText("OK").click();
        } catch (Exception e) {
            // Do nothing
        }

        // Get New Database dialog
        DialogFixture newDatabaseDialog = mainFrameFixture.dialog(
                new GenericTypeMatcher<JDialog>(JDialog.class) {
                    @Override protected boolean isMatching(
                        final JDialog dialog) {
                        return dialog.getClass().equals(NewProjectV.class);
                    }
                }, Timeout.timeout(5, TimeUnit.SECONDS));

        newDatabaseDialog.textBox("nameField").enterText("n");

        newDatabaseDialog.button("okButton").click();
    }

    /** Releases application after all tests in suite are finished. */
    @AfterSuite public final void endApplication() {
        mainFrameFixture.cleanUp();
    }

    /** Gets the OpenSHAPA Instance for each test. */
    @BeforeClass public final void newTestClass() {
        System.err.println("Class starting");

        if (mainFrameFixture == null) {
            mainFrameFixture = OpenSHAPAInstance.getFixture();
        }
    }
}