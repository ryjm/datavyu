package org.datavyu.plugins.mplayer;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.plugins.StreamViewerDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CountDownLatch;


public class MPlayerDataViewerDialog extends StreamViewerDialog {

    private static Logger logger = LogManager.getLogger(MPlayerDataViewerDialog.class);

    /**
     * Data viewer offset.
     */
    private long offset;
    /**
     * Data to visualize.
     */
    private File data;
    /**
     * Boolean to keep track of whether or not we are isPlaying
     */
    private boolean playing;


    /**
     * The last jog position, making sure we are only calling jog once
     * VLC has issues when trying to go to the same spot multiple times
     */
    private JDialog dialog = new JDialog();
    private MPlayerApplication javafxapp;
    private boolean assumedFPS = false;


    public MPlayerDataViewerDialog(final Frame parent, final boolean modal) {
        super(parent, modal);
        javafxapp = new MPlayerApplication(null);
    }

    public static void runAndWait(final Runnable action) {
        if (action == null)
            throw new NullPointerException("action");

        // run synchronously on JavaFX thread
        if (Platform.isFxApplicationThread()) {
            logger.info("Javax thread running action.");
            action.run();
            return;
        }

        // queue on JavaFX thread and wait for completion
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.info("Running action " + (Platform.isFxApplicationThread() ? " as JavaFx thread." : "."));
                    action.run();
                    logger.info("Action ran successful.");
                } catch (Exception e) {
                    logger.error("Exception occurred when running action.", e);
                }
            }
        });
    }

    @Override
    protected void setPlayerVolume(float volume) {
        javafxapp.setVolume(volume);
    }

    private void launchEdtTaskNow(Runnable edtTask) {
        if (SwingUtilities.isEventDispatchThread()) {
            edtTask.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(edtTask);
            } catch (Exception e) {
                logger.error("Failed edit task now. Error: ", e);
            }
        }
    }

    private void launchEdtTaskLater(Runnable edtTask) {
        if (SwingUtilities.isEventDispatchThread()) {
            edtTask.run();
        } else {
            try {
                SwingUtilities.invokeLater(edtTask);
            } catch (Exception e) {
                logger.error("Failed edit task later. Error: ", e);
            }
        }
    }

    @Override
    public JDialog getParentJDialog() {
        return dialog;
    }

    @Override
    public float getFramesPerSecond() {
        return javafxapp.getFrameRate();
    }

    public void setFramesPerSecond(float framesPerSecond) {
        assumedFPS = false;
    }

    @Override
    public float getDetectedFrameRate() {
        return getFramesPerSecond();
    }

    @Override
    public long getStartTime() {
        return offset;
    }

    @Override
    public void setStartTime(final long offset) {
        this.offset = offset;
    }

    @Override
    public void setViewerVisible(final boolean isVisible) {
        javafxapp.setVisible(isVisible);
        this.isVisible = isVisible;
    }

    @Override
    public File getSourceFile() {
        return data;
    }

    @Override
    public void setSourceFile(final File sourceFile) {

        logger.info("Set source file: " + sourceFile.getAbsolutePath());

        final CountDownLatch latch = new CountDownLatch(1);
        data = sourceFile;
        Platform.setImplicitExit(false);

        javafxapp = new MPlayerApplication(sourceFile);

        logger.info("Is event dispatch thread? " + (SwingUtilities.isEventDispatchThread() ? "Yes" : "No") + ".");
        logger.info("Is FX application thread? " + (Platform.isFxApplicationThread() ? "Yes" : "No") + ".");


        runAndWait(new Runnable() {
            @Override
            public void run() {
                javafxapp.start(new Stage());
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (Exception e) {
            logger.error("Await latch failed. Error: ", e);
        }

        while (!javafxapp.isInit()) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.error("Waited for thread. Error: ", e);
            }
        }

        logger.info("Finished setting source: " + sourceFile);
        logger.info("Duration is: " + javafxapp.getDuration());

        dialog.setVisible(false); // Hide our fake dialog box

        // TODO Add in function to guess frame rate
    }


    /**
     * Scales the video to the desired ratio.
     *
     * @param scale The new ratio to scale to, where 1.0 = original size, 2.0 = 200% zoom, etc.
     */
    @Override
    protected void resizeVideo(final float scale) {
        javafxapp.setScale(scale);

        notifyChange();
    }

    @Override
    protected void setPlayerSourceFile(File playerSourceFile) {

    }

    @Override
    protected Dimension getOriginalVideoSize() {
        return null;
    }

    @Override
    protected float getPlayerFramesPerSecond() {
        return getFramesPerSecond();
    }

    @Override
    public long getDuration() {
        return javafxapp.getDuration();
    }

    @Override
    public long getCurrentTime() {
        return javafxapp.getCurrentTime();
    }

    @Override
    public void seek(final long position) {
        javafxapp.seek(position);
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    @Override
    public void stop() {
        playing = false;
        javafxapp.pause();
    }

    @Override
    public void setPlaybackSpeed(final float rate) {
        javafxapp.setRate(rate);
    }

    @Override
    public void start() {
        playing = true;
        javafxapp.play();
    }

    @Override
    protected void cleanUp() {

    }

    @Override
    public void unsetSourceFile() {
        stop();
        javafxapp.setVisible(false);
        javafxapp.closeAndDestroy();
    }

    public boolean isAssumedFramesPerSecond() {
        return assumedFPS;
    }

    @Override
    public boolean isStepEnabled() {
        return false;
    }

    @Override
    public void step() {
        // Nothing to do here
    }
}
