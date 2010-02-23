package org.openshapa.views;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SimpleTimeZone;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;

import org.jdesktop.application.Action;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;
import org.openshapa.OpenSHAPA;
import org.openshapa.controllers.CreateNewCellC;
import org.openshapa.controllers.SetNewCellStopTimeC;
import org.openshapa.controllers.SetSelectedCellStartTimeC;
import org.openshapa.controllers.SetSelectedCellStopTimeC;
import org.openshapa.event.CarriageEvent;
import org.openshapa.event.MarkerEvent;
import org.openshapa.event.NeedleEvent;
import org.openshapa.event.TracksControllerEvent;
import org.openshapa.event.TracksControllerListener;
import org.openshapa.models.project.Project;
import org.openshapa.util.ClockTimer;
import org.openshapa.util.FloatUtils;
import org.openshapa.util.ClockTimer.ClockListener;
import org.openshapa.views.continuous.DataController;
import org.openshapa.views.continuous.DataViewer;
import org.openshapa.views.continuous.PluginManager;

import com.usermetrix.jclient.UserMetrix;

/**
 * Quicktime video controller.
 */
public final class DataControllerV extends OpenSHAPADialog implements
        ClockListener, TracksControllerListener, DataController {

    // --------------------------------------------------------------------------
    // [static]
    //
    /** The logger for this class. */
    private UserMetrix logger = UserMetrix.getInstance(DataControllerV.class);
    /** One second in milliseconds. */
    private static final long ONE_SECOND = 1000L;
    /** Rate of playback for rewinding. */
    private static final float REWIND_RATE = -32F;
    /** Rate of normal playback. */
    private static final float PLAY_RATE = 1F;
    /** Rate of playback for fast forwarding. */
    private static final float FFORWARD_RATE = 32F;
    /** Sequence of allowable shuttle rates. */
    private static final float[] SHUTTLE_RATES;
    /**
     * The threshold to use while synchronising viewers (augmented by rate).
     */
    private static final long SYNC_THRESH = 50;
    /**
     * How often to synchronise the viewers with the master clock.
     */
    private static final long SYNC_PULSE = 500;

    // Initialize SHUTTLE_RATES
    // values: [ (2^-5), ..., (2^0), ..., (2^5) ]

    /** The max power used for playback rates; i.e. 2^POWER = max. */
    private static final int POWER = 5;

    static {
        SHUTTLE_RATES = new float[2 * POWER + 1];
        float value = 1;
        SHUTTLE_RATES[POWER] = value;
        for (int i = 1; i <= POWER; ++i) {
            value *= 2;
            SHUTTLE_RATES[POWER + i] = value;
            SHUTTLE_RATES[POWER - i] = 1F / value;
        }
    }

    /** Determines whether or not Shift is being held. */
    private boolean shiftMask = false;

    /** Determines whether or not Control is being held. */
    private boolean ctrlMask = false;

    /** The jump multiplier for shift-jogging. */
    private static final int SHIFTJOG = 5;

    /** The jump multiplier for ctrl-shift-jogging. */
    private static final int CTRLSHIFTJOG = 10;

    /**
     * Enumeration of shuttle directions.
     */
    enum ShuttleDirection {
        /** The backwards playrate. */
        BACKWARDS(-1),
        /** Playrate for undefined shuttle speeds. */
        UNDEFINED(0),
        /** The playrate for forwards (normal) playrate. */
        FORWARDS(1);
        /** Stores the shuttle direction. */
        private int parameter;

        /**
         * Sets the shuttle direction.
         * 
         * @param p
         *            The new shuttle direction.
         */
        ShuttleDirection(final int p) {
            parameter = p;
        }

        /** @return The shuttle direction. */
        public int getParameter() {
            return parameter;
        }
    }

    /** Format for representing time. */
    private static final DateFormat CLOCK_FORMAT;

    // initialize standard date format for clock display.
    static {
        CLOCK_FORMAT = new SimpleDateFormat("HH:mm:ss:SSS");
        CLOCK_FORMAT.setTimeZone(new SimpleTimeZone(0, "NO_ZONE"));
    }

    // --------------------------------------------------------------------------
    //
    //
    /** The list of viewers associated with this controller. */
    private Set<DataViewer> viewers;
    /** Stores the highest frame rate for all available viewers. */
    private float currentFPS = 1F;
    /** Shuttle status flag. */
    private ShuttleDirection shuttleDirection = ShuttleDirection.UNDEFINED;
    /** Index of current shuttle rate. */
    private int shuttleRate;
    /** The rate to use when resumed from pause. */
    private float pauseRate;
    /** The time the last sync was performed. */
    private long lastSync;
    /** Clock timer. */
    private ClockTimer clock = new ClockTimer();
    /** The maximum duration out of all data being played. */
    private long maxDuration;
    /** Are we currently faking playback of the viewers? */
    private boolean fakePlayback = false;
    /** The start time of the playback window. */
    private long windowPlayStart;
    /** The end time of the playback window. */
    private long windowPlayEnd;
    /** Is the tracks panel currently shown? */
    private boolean tracksPanelEnabled = false;
    /** The controller for manipulating tracks. */
    private MixerControllerV mixerControllerV;

    // --------------------------------------------------------------------------
    // [initialization]
    //
    /**
     * Constructor. Creates a new DataControllerV.
     * 
     * @param parent
     *            The parent of this form.
     * @param modal
     *            Should the dialog be modal or not?
     */
    public DataControllerV(final java.awt.Frame parent, final boolean modal) {
        super(parent, modal);

        clock.registerListener(this);

        initComponents();
        setName(this.getClass().getSimpleName());
        viewers = new HashSet<DataViewer>();
        pauseRate = 0;
        lastSync = 0;

        maxDuration = 0;

        windowPlayStart = 0;
        windowPlayEnd = 0;

        mixerControllerV = new MixerControllerV();
        tracksPanel.add(mixerControllerV.getTracksPanel());
        mixerControllerV.addTracksControllerListener(this);

        showTracksPanel(false);
    }

    /**
     * Tells the Data Controller if shift is being held or not.
     * 
     * @param shift
     *            True for shift held; false otherwise.
     */
    public void setShiftMask(final boolean shift) {
        shiftMask = shift;
    }

    /**
     * Tells the Data Controller if ctrl is being held or not.
     * 
     * @param ctrl
     *            True for ctrl held; false otherwise.
     */
    public void setCtrlMask(final boolean ctrl) {
        ctrlMask = ctrl;
    }

    // --------------------------------------------------------------------------
    // [interface] org.openshapa.util.ClockTimer.Listener
    //
    /**
     * @param time
     *            Current clock time in milliseconds.
     */
    public void clockStart(final long time) {
        resetSync();
        long playTime = time;
        if (playTime < windowPlayStart) {
            playTime = windowPlayStart;
            clockStep(playTime);
            float currentRate = clock.getRate();
            clock.stop();
            clock.setTime(playTime);
            clock.setRate(currentRate);
            clock.start();
        }
        setCurrentTime(playTime);
    }

    /**
     * Reset the sync.
     */
    private void resetSync() {
        lastSync = 0;
    }

    /**
     * @param time
     *            Current clock time in milliseconds.
     */
    public void clockTick(final long time) {
        try {
            setCurrentTime(time);

            // We are playing back at a rate which is to fast and probably won't
            // allow us to stream all the information at the file. We fake play
            // back by doing a bunch of seekTo's.
            if (fakePlayback) {
                for (DataViewer v : viewers) {
                    v.seekTo(time - v.getOffset());
                }

                // DataViewer is responsible for playing video.
            } else {

                // Synchronise viewers only if we have exceded our pulse time.
                if ((time - lastSync) > (SYNC_PULSE * clock.getRate())) {
                    long thresh =
                            (long) (SYNC_THRESH * Math.abs(clock.getRate()));
                    lastSync = time;

                    for (DataViewer v : viewers) {
                        /*
                         * Use offsets to determine if the video file should
                         * start playing.
                         */
                        if (time >= v.getOffset() && !v.isPlaying()) {
                            v.seekTo(time - v.getOffset());
                            v.play();
                        }

                        /*
                         * Only synchronise the data viewers if we have a
                         * noticable drift.
                         */
                        if (Math.abs(v.getCurrentTime() - time) > thresh) {
                            v.seekTo(time - v.getOffset());
                        }
                    }
                }
            }

            // BugzID:466 - Prevent rewind wrapping the clock past the start
            // point of the view window.
            if (time < windowPlayStart) {
                setCurrentTime(windowPlayStart);
                clock.stop();
                clock.setTime(windowPlayStart);
                clockStop(windowPlayStart);
            }

            // BugzID:756 - don't play video once past the max duration.
            if (time >= windowPlayEnd && clock.getRate() >= 0) {
                setCurrentTime(windowPlayEnd);
                clock.stop();
                clock.setTime(windowPlayEnd);
                clockStop(windowPlayEnd);
                return;
            }
        } catch (Exception e) {
            logger.error("Unable to Sync viewers", e);
        }
    }

    /**
     * @param time
     *            Current clock time in milliseconds.
     */
    public void clockStop(final long time) {
        resetSync();
        setCurrentTime(time);
        for (DataViewer viewer : viewers) {
            viewer.stop();
            viewer.seekTo(time - viewer.getOffset());
        }
    }

    /**
     * @param rate
     *            Current (updated) clock rate.
     */
    public void clockRate(final float rate) {
        resetSync();
        lblSpeed.setText(FloatUtils.doubleToFractionStr(new Double(rate)));

        // If rate is faster than two times - we need to fake playback to give
        // the illusion of 'smooth'. We do this by stopping the dataviewer and
        // doing many seekTo's to grab individual frames.
        if (Math.abs(rate) > 2.0) {
            fakePlayback = true;
            for (DataViewer viewer : viewers) {
                viewer.setPlaybackSpeed(rate);
                viewer.stop();
            }

            // Rate is less than two times - use the data viewer internal code
            // to
            // draw every frame.
        } else {
            fakePlayback = false;
            for (DataViewer viewer : viewers) {
                viewer.setPlaybackSpeed(rate);
                if (!clock.isStopped()) {
                    viewer.play();
                }
            }
        }
    }

    /**
     * @param time
     *            Current clock time in milliseconds.
     */
    public void clockStep(final long time) {
        resetSync();
        setCurrentTime(time);
        for (DataViewer viewer : viewers) {
            viewer.seekTo(time - viewer.getOffset());
        }
    }

    @Override
    public void dispose() {
        mixerControllerV.removeAll();
        super.dispose();
    }

    // --------------------------------------------------------------------------
    //
    //
    /**
     * Set time location for data streams.
     * 
     * @param milliseconds
     *            The millisecond time.
     */
    public void setCurrentTime(final long milliseconds) {
        resetSync();
        timestampLabel.setText(CLOCK_FORMAT.format(milliseconds));
        mixerControllerV.setCurrentTime(milliseconds);
    }

    /**
     * Get the current master clock time for the controller.
     * 
     * @return Time in milliseconds.
     */
    private long getCurrentTime() {
        return clock.getTime();
    }

    /**
     * Remove the specifed viewer from the controller.
     * 
     * @param viewer
     *            The viewer to shutdown.
     * @return True if the controller contained this viewer.
     */
    public boolean shutdown(final DataViewer viewer) {
        boolean result = viewers.remove(viewer);
        if (result) {
            // Recalculate the maximum playback duration.
            maxDuration = 0;
            Iterator<DataViewer> it = viewers.iterator();
            while (it.hasNext()) {
                DataViewer dv = it.next();
                if (dv.getDuration() + dv.getOffset() > maxDuration) {
                    maxDuration = dv.getDuration() + dv.getOffset();
                }
            }
            mixerControllerV.setMaxEnd(maxDuration);

            if (windowPlayEnd > maxDuration) {
                windowPlayEnd = maxDuration;
                mixerControllerV.setPlayRegionEnd(windowPlayEnd);
            }

            if (windowPlayStart > windowPlayEnd) {
                windowPlayStart = 0;
                mixerControllerV.setPlayRegionStart(windowPlayStart);
            }

            long tracksTime = mixerControllerV.getCurrentTime();
            if (tracksTime < windowPlayStart) {
                tracksTime = windowPlayStart;
            }
            if (tracksTime > windowPlayEnd) {
                tracksTime = windowPlayEnd;
            }
            mixerControllerV.setCurrentTime(tracksTime);

            clock.setTime(tracksTime);
            clockStep(tracksTime);

            // Remove the data viewer from the project
            OpenSHAPA.getProject().removeViewerSetting(
                    viewer.getDataFeed().getAbsolutePath());
            // Remove the data viewer from the tracks panel
            mixerControllerV
                    .removeTrack(viewer.getDataFeed().getAbsolutePath());
            OpenSHAPA.getApplication().updateTitle();
        }

        return result;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        gridButtonPanel = new javax.swing.JPanel();
        syncCtrlButton = new javax.swing.JButton();
        syncButton = new javax.swing.JButton();
        setCellOnsetButton = new javax.swing.JButton();
        setCellOffsetButton = new javax.swing.JButton();
        rewindButton = new javax.swing.JButton();
        playButton = new javax.swing.JButton();
        forwardButton = new javax.swing.JButton();
        goBackButton = new javax.swing.JButton();
        shuttleBackButton = new javax.swing.JButton();
        pauseButton = new javax.swing.JButton();
        shuttleForwardButton = new javax.swing.JButton();
        findButton = new javax.swing.JButton();
        jogBackButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        createNewCellSettingOffset = new javax.swing.JButton();
        jogForwardButton = new javax.swing.JButton();
        setNewCellOffsetButton = new javax.swing.JButton();
        goBackTextField = new javax.swing.JTextField();
        findTextField = new javax.swing.JTextField();
        syncVideoButton = new javax.swing.JButton();
        openVideoButton = new javax.swing.JButton();
        timestampLabel = new javax.swing.JLabel();
        topPanel = new javax.swing.JPanel();
        lblSpeed = new javax.swing.JLabel();
        createNewCell = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        findOffsetField = new javax.swing.JTextField();
        showTracksButton = new javax.swing.JButton();
        tracksPanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(org.openshapa.OpenSHAPA.class).getContext().getResourceMap(DataControllerV.class);
        setTitle(resourceMap.getString("title")); // NOI18N
        setName(""); // NOI18N
        setResizable(false);

        gridButtonPanel.setBackground(new java.awt.Color(255, 255, 255));
        gridButtonPanel.setMinimumSize(new java.awt.Dimension(282, 274));
        gridButtonPanel.setPreferredSize(new java.awt.Dimension(280, 295));
        gridButtonPanel.setLayout(new java.awt.GridBagLayout());

        syncCtrlButton.setEnabled(false);
        syncCtrlButton.setFocusPainted(false);
        syncCtrlButton.setMaximumSize(new java.awt.Dimension(45, 45));
        syncCtrlButton.setMinimumSize(new java.awt.Dimension(45, 45));
        syncCtrlButton.setPreferredSize(new java.awt.Dimension(45, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(syncCtrlButton, gridBagConstraints);

        syncButton.setEnabled(false);
        syncButton.setFocusPainted(false);
        syncButton.setMaximumSize(new java.awt.Dimension(45, 45));
        syncButton.setMinimumSize(new java.awt.Dimension(45, 45));
        syncButton.setPreferredSize(new java.awt.Dimension(45, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(syncButton, gridBagConstraints);

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(org.openshapa.OpenSHAPA.class).getContext().getActionMap(DataControllerV.class, this);
        setCellOnsetButton.setAction(actionMap.get("setCellOnsetAction")); // NOI18N
        setCellOnsetButton.setIcon(resourceMap.getIcon("setCellOnsetButton.icon")); // NOI18N
        setCellOnsetButton.setFocusPainted(false);
        setCellOnsetButton.setMaximumSize(new java.awt.Dimension(45, 45));
        setCellOnsetButton.setMinimumSize(new java.awt.Dimension(45, 45));
        setCellOnsetButton.setName("setCellOnsetButton"); // NOI18N
        setCellOnsetButton.setPreferredSize(new java.awt.Dimension(45, 45));
        setCellOnsetButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/set-cell-onset-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(setCellOnsetButton, gridBagConstraints);

        setCellOffsetButton.setAction(actionMap.get("setCellOffsetAction")); // NOI18N
        setCellOffsetButton.setIcon(resourceMap.getIcon("setCellOffsetButton.icon")); // NOI18N
        setCellOffsetButton.setFocusPainted(false);
        setCellOffsetButton.setMaximumSize(new java.awt.Dimension(45, 45));
        setCellOffsetButton.setMinimumSize(new java.awt.Dimension(45, 45));
        setCellOffsetButton.setName("setCellOffsetButton"); // NOI18N
        setCellOffsetButton.setPreferredSize(new java.awt.Dimension(45, 45));
        setCellOffsetButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/set-cell-offset-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 1);
        gridButtonPanel.add(setCellOffsetButton, gridBagConstraints);

        rewindButton.setAction(actionMap.get("rewindAction")); // NOI18N
        rewindButton.setIcon(resourceMap.getIcon("rewindButton.icon")); // NOI18N
        rewindButton.setFocusPainted(false);
        rewindButton.setMaximumSize(new java.awt.Dimension(45, 45));
        rewindButton.setMinimumSize(new java.awt.Dimension(45, 45));
        rewindButton.setName("rewindButton"); // NOI18N
        rewindButton.setPreferredSize(new java.awt.Dimension(45, 45));
        rewindButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/rewind-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(rewindButton, gridBagConstraints);

        playButton.setAction(actionMap.get("playAction")); // NOI18N
        playButton.setIcon(resourceMap.getIcon("playButton.icon")); // NOI18N
        playButton.setFocusPainted(false);
        playButton.setMaximumSize(new java.awt.Dimension(45, 45));
        playButton.setMinimumSize(new java.awt.Dimension(45, 45));
        playButton.setName("playButton"); // NOI18N
        playButton.setPreferredSize(new java.awt.Dimension(45, 45));
        playButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/play-selected.png"))); // NOI18N
        playButton.setRequestFocusEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(playButton, gridBagConstraints);

        forwardButton.setAction(actionMap.get("forwardAction")); // NOI18N
        forwardButton.setIcon(resourceMap.getIcon("forwardButton.icon")); // NOI18N
        forwardButton.setFocusPainted(false);
        forwardButton.setMaximumSize(new java.awt.Dimension(45, 45));
        forwardButton.setMinimumSize(new java.awt.Dimension(45, 45));
        forwardButton.setName("forwardButton"); // NOI18N
        forwardButton.setPreferredSize(new java.awt.Dimension(45, 45));
        forwardButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/fast-forward-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(forwardButton, gridBagConstraints);

        goBackButton.setAction(actionMap.get("goBackAction")); // NOI18N
        goBackButton.setIcon(resourceMap.getIcon("goBackButton.icon")); // NOI18N
        goBackButton.setFocusPainted(false);
        goBackButton.setMaximumSize(new java.awt.Dimension(45, 45));
        goBackButton.setMinimumSize(new java.awt.Dimension(45, 45));
        goBackButton.setName("goBackButton"); // NOI18N
        goBackButton.setPreferredSize(new java.awt.Dimension(45, 45));
        goBackButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/go-back-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(goBackButton, gridBagConstraints);

        shuttleBackButton.setAction(actionMap.get("shuttleBackAction")); // NOI18N
        shuttleBackButton.setIcon(resourceMap.getIcon("shuttleBackButton.icon")); // NOI18N
        shuttleBackButton.setFocusPainted(false);
        shuttleBackButton.setMaximumSize(new java.awt.Dimension(45, 45));
        shuttleBackButton.setMinimumSize(new java.awt.Dimension(45, 45));
        shuttleBackButton.setName("shuttleBackButton"); // NOI18N
        shuttleBackButton.setPreferredSize(new java.awt.Dimension(45, 45));
        shuttleBackButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/shuttle-backward-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(shuttleBackButton, gridBagConstraints);

        pauseButton.setAction(actionMap.get("pauseAction")); // NOI18N
        pauseButton.setIcon(resourceMap.getIcon("pauseButton.icon")); // NOI18N
        pauseButton.setFocusPainted(false);
        pauseButton.setMaximumSize(new java.awt.Dimension(45, 45));
        pauseButton.setMinimumSize(new java.awt.Dimension(45, 45));
        pauseButton.setName("pauseButton"); // NOI18N
        pauseButton.setPreferredSize(new java.awt.Dimension(45, 45));
        pauseButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/pause-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(pauseButton, gridBagConstraints);

        shuttleForwardButton.setAction(actionMap.get("shuttleForwardAction")); // NOI18N
        shuttleForwardButton.setIcon(resourceMap.getIcon("shuttleForwardButton.icon")); // NOI18N
        shuttleForwardButton.setFocusPainted(false);
        shuttleForwardButton.setMaximumSize(new java.awt.Dimension(45, 45));
        shuttleForwardButton.setMinimumSize(new java.awt.Dimension(45, 45));
        shuttleForwardButton.setName("shuttleForwardButton"); // NOI18N
        shuttleForwardButton.setPreferredSize(new java.awt.Dimension(45, 45));
        shuttleForwardButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/shuttle-forward-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(shuttleForwardButton, gridBagConstraints);

        findButton.setAction(actionMap.get("findAction")); // NOI18N
        findButton.setIcon(resourceMap.getIcon("findButton.icon")); // NOI18N
        findButton.setFocusPainted(false);
        findButton.setMaximumSize(new java.awt.Dimension(45, 45));
        findButton.setMinimumSize(new java.awt.Dimension(45, 45));
        findButton.setName("findButton"); // NOI18N
        findButton.setPreferredSize(new java.awt.Dimension(45, 45));
        findButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/find-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(findButton, gridBagConstraints);

        jogBackButton.setAction(actionMap.get("jogBackAction")); // NOI18N
        jogBackButton.setIcon(resourceMap.getIcon("jogBackButton.icon")); // NOI18N
        jogBackButton.setFocusPainted(false);
        jogBackButton.setMaximumSize(new java.awt.Dimension(45, 45));
        jogBackButton.setMinimumSize(new java.awt.Dimension(45, 45));
        jogBackButton.setName("jogBackButton"); // NOI18N
        jogBackButton.setPreferredSize(new java.awt.Dimension(45, 45));
        jogBackButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/jog-backward-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(jogBackButton, gridBagConstraints);

        stopButton.setAction(actionMap.get("stopAction")); // NOI18N
        stopButton.setIcon(resourceMap.getIcon("stopButton.icon")); // NOI18N
        stopButton.setFocusPainted(false);
        stopButton.setMaximumSize(new java.awt.Dimension(45, 45));
        stopButton.setMinimumSize(new java.awt.Dimension(45, 45));
        stopButton.setName("stopButton"); // NOI18N
        stopButton.setPreferredSize(new java.awt.Dimension(45, 45));
        stopButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/stop-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(stopButton, gridBagConstraints);

        createNewCellSettingOffset.setAction(actionMap.get("createNewCellAction")); // NOI18N
        createNewCellSettingOffset.setIcon(resourceMap.getIcon("createNewCellButton.icon")); // NOI18N
        createNewCellSettingOffset.setFocusPainted(false);
        createNewCellSettingOffset.setMaximumSize(new java.awt.Dimension(90, 45));
        createNewCellSettingOffset.setMinimumSize(new java.awt.Dimension(90, 45));
        createNewCellSettingOffset.setName("newCellAndOnsetButton"); // NOI18N
        createNewCellSettingOffset.setPreferredSize(new java.awt.Dimension(90, 45));
        createNewCellSettingOffset.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/create-new-cell-and-set-onset-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(createNewCellSettingOffset, gridBagConstraints);

        jogForwardButton.setAction(actionMap.get("jogForwardAction")); // NOI18N
        jogForwardButton.setIcon(resourceMap.getIcon("jogForwardButton.icon")); // NOI18N
        jogForwardButton.setFocusPainted(false);
        jogForwardButton.setMaximumSize(new java.awt.Dimension(45, 45));
        jogForwardButton.setMinimumSize(new java.awt.Dimension(45, 45));
        jogForwardButton.setName("jogForwardButton"); // NOI18N
        jogForwardButton.setPreferredSize(new java.awt.Dimension(45, 45));
        jogForwardButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/jog-forward-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(jogForwardButton, gridBagConstraints);

        setNewCellOffsetButton.setAction(actionMap.get("setNewCellStopTime")); // NOI18N
        setNewCellOffsetButton.setIcon(resourceMap.getIcon("setNewCellOnsetButton.icon")); // NOI18N
        setNewCellOffsetButton.setFocusPainted(false);
        setNewCellOffsetButton.setMaximumSize(new java.awt.Dimension(45, 45));
        setNewCellOffsetButton.setMinimumSize(new java.awt.Dimension(45, 45));
        setNewCellOffsetButton.setName("newCellOffsetButton"); // NOI18N
        setNewCellOffsetButton.setPreferredSize(new java.awt.Dimension(45, 45));
        setNewCellOffsetButton.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/set-new-cell-offset-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(setNewCellOffsetButton, gridBagConstraints);

        goBackTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        goBackTextField.setText("00:00:05:000");
        goBackTextField.setMaximumSize(new java.awt.Dimension(80, 45));
        goBackTextField.setMinimumSize(new java.awt.Dimension(80, 45));
        goBackTextField.setPreferredSize(new java.awt.Dimension(80, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(goBackTextField, gridBagConstraints);

        findTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        findTextField.setText("00:00:00:000");
        findTextField.setMaximumSize(new java.awt.Dimension(80, 45));
        findTextField.setMinimumSize(new java.awt.Dimension(80, 45));
        findTextField.setName("findOnsetLabel"); // NOI18N
        findTextField.setPreferredSize(new java.awt.Dimension(80, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(findTextField, gridBagConstraints);

        syncVideoButton.setEnabled(false);
        syncVideoButton.setFocusPainted(false);
        syncVideoButton.setMaximumSize(new java.awt.Dimension(80, 45));
        syncVideoButton.setMinimumSize(new java.awt.Dimension(80, 45));
        syncVideoButton.setPreferredSize(new java.awt.Dimension(80, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(syncVideoButton, gridBagConstraints);

        openVideoButton.setText(resourceMap.getString("openVideoButton.text")); // NOI18N
        openVideoButton.setFocusPainted(false);
        openVideoButton.setMaximumSize(new java.awt.Dimension(90, 25));
        openVideoButton.setMinimumSize(new java.awt.Dimension(90, 25));
        openVideoButton.setPreferredSize(new java.awt.Dimension(90, 25));
        openVideoButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openVideoButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(openVideoButton, gridBagConstraints);

        timestampLabel.setFont(new java.awt.Font("Tahoma", 1, 11));
        timestampLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        timestampLabel.setText("00:00:00:000");
        timestampLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        timestampLabel.setName("timestampLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(timestampLabel, gridBagConstraints);

        topPanel.setBackground(java.awt.Color.white);
        topPanel.setLayout(new java.awt.BorderLayout());

        lblSpeed.setFont(new java.awt.Font("Tahoma", 1, 11));
        lblSpeed.setText("0");
        lblSpeed.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 2));
        topPanel.add(lblSpeed, java.awt.BorderLayout.LINE_END);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 0);
        gridButtonPanel.add(topPanel, gridBagConstraints);

        createNewCell.setAction(actionMap.get("createCellAction")); // NOI18N
        createNewCell.setIcon(resourceMap.getIcon("createNewCell.icon")); // NOI18N
        createNewCell.setText(resourceMap.getString("createNewCell.text")); // NOI18N
        createNewCell.setAlignmentY(0.0F);
        createNewCell.setFocusPainted(false);
        createNewCell.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        createNewCell.setMaximumSize(new java.awt.Dimension(45, 90));
        createNewCell.setMinimumSize(new java.awt.Dimension(45, 90));
        createNewCell.setName("createNewCellButton"); // NOI18N
        createNewCell.setPreferredSize(new java.awt.Dimension(45, 90));
        createNewCell.setPressedIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/DataController/eng/create-new-cell-selected.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(createNewCell, gridBagConstraints);

        jLabel1.setText("@");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        gridButtonPanel.add(jLabel1, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11));
        jLabel2.setText("x");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridButtonPanel.add(jLabel2, gridBagConstraints);

        findOffsetField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        findOffsetField.setText("00:00:00:000");
        findOffsetField.setToolTipText(resourceMap.getString("findOffsetField.toolTipText")); // NOI18N
        findOffsetField.setEnabled(false);
        findOffsetField.setMaximumSize(new java.awt.Dimension(80, 45));
        findOffsetField.setMinimumSize(new java.awt.Dimension(80, 45));
        findOffsetField.setName("findOffsetLabel"); // NOI18N
        findOffsetField.setPreferredSize(new java.awt.Dimension(80, 45));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(findOffsetField, gridBagConstraints);

        showTracksButton.setIcon(resourceMap.getIcon("showTracksButton.show.icon")); // NOI18N
        showTracksButton.setMaximumSize(new java.awt.Dimension(73, 45));
        showTracksButton.setMinimumSize(new java.awt.Dimension(73, 45));
        showTracksButton.setName("showTracksButton"); // NOI18N
        showTracksButton.setPreferredSize(new java.awt.Dimension(73, 45));
        showTracksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showTracksButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridButtonPanel.add(showTracksButton, gridBagConstraints);
        showTracksButton.getAccessibleContext().setAccessibleName("Show Tracks");

        getContentPane().add(gridButtonPanel, java.awt.BorderLayout.WEST);

        tracksPanel.setBackground(new java.awt.Color(255, 255, 255));
        tracksPanel.setPreferredSize(new java.awt.Dimension(800, 278));
        getContentPane().add(tracksPanel, java.awt.BorderLayout.EAST);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Action to invoke when the user clicks on the open button.
     * 
     * @param evt
     *            The event that triggered this action.
     */
    private void openVideoButtonActionPerformed(
            final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openVideoButtonActionPerformed
        OpenSHAPAFileChooser jd = new OpenSHAPAFileChooser();
        PluginManager pm = PluginManager.getInstance();

        // Add file filters for each of the supported plugins.
        for (FileFilter f : pm.getPluginFileFilters()) {
            jd.addChoosableFileFilter(f);
        }

        if (JFileChooser.APPROVE_OPTION == jd.showOpenDialog(this)) {
            File f = jd.getSelectedFile();
            FileFilter ff = jd.getFileFilter();

            for (DataViewer viewer : pm.buildDataViewers(ff, f)) {
                addDataViewer(viewer, f);
            }
        }
    }// GEN-LAST:event_openVideoButtonActionPerformed

    /**
     * Action to invoke when the user clicks the show tracks button.
     * 
     * @param evt
     *            The event that triggered this action.
     */
    private void showTracksButtonActionPerformed(
            final java.awt.event.ActionEvent evt) {// GEN-FIRST:event_showTracksButtonActionPerformed
        assert (evt.getSource() instanceof JButton);
        JButton button = (JButton) evt.getSource();
        ResourceMap resourceMap =
                Application.getInstance(org.openshapa.OpenSHAPA.class)
                        .getContext().getResourceMap(DataControllerV.class);
        if (tracksPanelEnabled) {
            // Panel is being displayed, hide it
            button.setIcon(resourceMap.getIcon("showTracksButton.show.icon"));
        } else {
            // Panel is hidden, show it
            button.setIcon(resourceMap.getIcon("showTracksButton.hide.icon"));
        }
        tracksPanelEnabled = !tracksPanelEnabled;
        showTracksPanel(tracksPanelEnabled);
    }// GEN-LAST:event_showTracksButtonActionPerformed

    /**
     * Adds a data viewer to this data controller.
     * 
     * @param viewer
     *            The new viewer that we are adding to the data controller.
     * @param f
     *            The parent file that the viewer represents.
     */
    private void addDataViewer(final DataViewer viewer, final File f) {
        addViewer(viewer, 0);

        addDataViewerToProject(viewer.getClass().getName(), f.getAbsolutePath());

        // Add the file to the tracks information panel
        addTrack(f.getAbsolutePath(), f.getName(), viewer.getDuration(), viewer
                .getOffset(), -1);
    }

    /**
     * Adds a track to the tracks panel.
     * 
     * @param mediaPath
     *            Absolute file path to the media file.
     * @param name
     *            The name of the track to add.
     * @param duration
     *            The duration of the data feed in milliseconds.
     * @param offset
     *            The time offset of the data feed in milliseconds.
     */
    public void addTrack(final String mediaPath, final String name,
            final long duration, final long offset, final long bookmark) {
        mixerControllerV.addNewTrack(mediaPath, name, duration, offset,
                bookmark);
    }

    /**
     * Add the data viewer to the current project.
     * 
     * @param pluginName
     *            Fully qualified plugin class name.
     * @param filePath
     *            Absolute file path to the data feed.
     */
    public void addDataViewerToProject(final String pluginName,
            final String filePath) {
        Project project = OpenSHAPA.getProject();
        project.addNewViewerSetting(pluginName, filePath);
        OpenSHAPA.getApplication().updateTitle();
    }

    public void addViewer(final DataViewer viewer, final long offset) {
        // Add the QTDataViewer to the list of viewers we are controlling.
        viewers.add(viewer);
        viewer.setParentController(this);
        viewer.setOffset(offset);
        OpenSHAPA.getApplication().show(viewer.getParentJFrame());

        // adjust the overall frame rate.
        float fps = viewer.getFrameRate();
        if (fps > currentFPS) {
            currentFPS = fps;
        }

        if (viewer.getOffset() + viewer.getDuration() > maxDuration) {
            maxDuration = viewer.getOffset() + viewer.getDuration();
        }

        if (windowPlayEnd < maxDuration) {
            windowPlayEnd = maxDuration;
            mixerControllerV.setPlayRegionEnd(windowPlayEnd);
        }
    }

    /**
     * Action to invoke when the user clicks the set cell onset button.
     */
    @Action
    public void setCellOnsetAction() {
        new SetSelectedCellStartTimeC(getCurrentTime());
    }

    /**
     * Action to invoke when the user clicks on the set cell offest button.
     */
    @Action
    public void setCellOffsetAction() {
        new SetSelectedCellStopTimeC(getCurrentTime());
        setFindOffsetField(getCurrentTime());
    }

    /**
     * @param show
     *            true to show the tracks layout, false otherwise.
     */
    public void showTracksPanel(final boolean show) {
        if (show) {
            this.setSize(gridButtonPanel.getWidth() + tracksPanel.getWidth(),
                    328);
        } else {
            this.setSize(285, 328);
        }
        tracksPanel.setVisible(show);
        tracksPanel.repaint();
        validate();
    }

    /**
     * Handler for a TracksControllerEvent.
     * 
     * @param e
     */
    public void tracksControllerChanged(final TracksControllerEvent e) {
        switch (e.getTracksEvent()) {
        case NEEDLE_EVENT:
            handleNeedleEvent((NeedleEvent) e.getEventObject());
            break;
        case MARKER_EVENT:
            handleMarkerEvent((MarkerEvent) e.getEventObject());
            break;
        case CARRIAGE_EVENT:
            handleCarriageEvent((CarriageEvent) e.getEventObject());
            break;
        default:
            break;
        }
    }

    /**
     * Handles a NeedleEvent (when the timing needle changes due to user
     * interaction).
     * 
     * @param e
     *            The Needle event that triggered this action.
     */
    private void handleNeedleEvent(final NeedleEvent e) {
        long newTime = e.getTime();
        if (newTime < windowPlayStart) {
            newTime = windowPlayStart;
        }
        if (newTime > windowPlayEnd) {
            newTime = windowPlayEnd;
        }
        clockStop(newTime);
        clockStep(newTime);
        setCurrentTime(newTime);
        clock.setTime(newTime);
    }

    /**
     * Handles a MarkerEvent (when one of the region marker changes due to user
     * interaction).
     * 
     * @param e
     *            The Marker Event that triggered this action.
     */
    private void handleMarkerEvent(final MarkerEvent e) {
        final long newWindowTime = e.getTime();
        final long tracksTime = mixerControllerV.getCurrentTime();
        switch (e.getMarker()) {
        case START_MARKER:
            if ((newWindowTime < maxDuration)
                    && (newWindowTime < windowPlayEnd)) {
                windowPlayStart = newWindowTime;
                mixerControllerV.setPlayRegionStart(windowPlayStart);
                if (tracksTime < windowPlayStart) {
                    mixerControllerV.setCurrentTime(windowPlayStart);
                    clock.setTime(windowPlayStart);
                    clockStep(windowPlayStart);
                }
            }
            break;
        case END_MARKER:
            if ((e.getTime() <= maxDuration) && (e.getTime() > windowPlayStart)) {
                windowPlayEnd = e.getTime();
                mixerControllerV.setPlayRegionEnd(windowPlayEnd);
                if (tracksTime > windowPlayEnd) {
                    mixerControllerV.setCurrentTime(windowPlayEnd);
                    clock.setTime(windowPlayEnd);
                    clockStep(windowPlayEnd);
                }
            }
            break;
        default:
            break;
        }
    }

    /**
     * Handles a CarriageEvent (when the carriage moves due to user
     * interaction).
     * 
     * @param e
     *            The carriage event that triggered this action.
     */
    private void handleCarriageEvent(final CarriageEvent e) {
        switch (e.getEventType()) {
        case OFFSET_CHANGE:
            // Look through our data viewers and update the offset
            Iterator<DataViewer> itOffset = viewers.iterator();
            while (itOffset.hasNext()) {
                DataViewer dv = itOffset.next();
                File feed = dv.getDataFeed();
                /*
                 * Found our data viewer, update the DV offset and the settings
                 * in the project file.
                 */
                if (feed.getAbsolutePath().equals(e.getTrackId())) {
                    dv.setOffset(e.getOffset());
                    Project project = OpenSHAPA.getProject();
                    project.addViewerOffsetSetting(dv.getClass().getName(), e
                            .getTrackId(), e.getOffset());
                    OpenSHAPA.getApplication().updateTitle();
                }
            }

            // Recalculate the maximum playback duration.
            maxDuration = 0;
            Iterator<DataViewer> itDuration = viewers.iterator();
            while (itDuration.hasNext()) {
                DataViewer dv = itDuration.next();
                if (dv.getDuration() + dv.getOffset() > maxDuration) {
                    maxDuration = dv.getDuration() + dv.getOffset();
                }
            }
            mixerControllerV.setMaxEnd(maxDuration);

            // Reset our playback windows
            if (windowPlayEnd > maxDuration) {
                windowPlayEnd = maxDuration;
                mixerControllerV.setPlayRegionEnd(windowPlayEnd);
            }

            if (windowPlayStart > windowPlayEnd) {
                windowPlayStart = 0;
                mixerControllerV.setPlayRegionStart(windowPlayStart);
            }

            // Reset the time if needed
            long tracksTime = mixerControllerV.getCurrentTime();
            if (tracksTime < windowPlayStart) {
                tracksTime = windowPlayStart;
            }
            if (tracksTime > windowPlayEnd) {
                tracksTime = windowPlayEnd;
            }
            mixerControllerV.setCurrentTime(tracksTime);

            clock.setTime(tracksTime);
            clockStep(tracksTime);
            break;
        case BOOKMARK_CHANGED: {
            // Look through our data viewers and update the bookmark
            Iterator<DataViewer> viewerIterator = viewers.iterator();
            while (viewerIterator.hasNext()) {
                DataViewer dv = viewerIterator.next();
                File feed = dv.getDataFeed();

                if (feed.getAbsolutePath().equals(e.getTrackId())) {
                    Project project = OpenSHAPA.getProject();
                    project.addViewerBookmarkSetting(dv.getClass().getName(), e
                            .getTrackId(), e.getBookmark());
                    OpenSHAPA.getApplication().updateTitle();
                }
            }
        }
        case BOOKMARK_SAVE: {
            // Look through our data viewers and update the bookmark
            Iterator<DataViewer> viewerIterator = viewers.iterator();
            while (viewerIterator.hasNext()) {
                DataViewer dv = viewerIterator.next();
                File feed = dv.getDataFeed();

                if (feed.getAbsolutePath().equals(e.getTrackId())) {
                    Project project = OpenSHAPA.getProject();
                    project.addViewerBookmarkSetting(dv.getClass().getName(), e
                            .getTrackId(), e.getBookmark());
                    OpenSHAPA.getApplication().updateTitle();
                }
            }
        }
        }
    }

    // --------------------------------------------------------------------------
    // Simulated clicks (for numpad calls)
    //

    /** Simulates play button clicked. */
    public void pressPlay() {
        playButton.doClick();
    }

    /** Simulates forward button clicked. */
    public void pressForward() {
        forwardButton.doClick();
    }

    /** Simulates rewind button clicked. */
    public void pressRewind() {
        rewindButton.doClick();
    }

    /** Simulates pause button clicked. */
    public void pressPause() {
        pauseButton.doClick();
    }

    /** Simulates stop button clicked. */
    public void pressStop() {
        stopButton.doClick();
    }

    /** Simulates shuttle forward button clicked. */
    public void pressShuttleForward() {
        shuttleForwardButton.doClick();
    }

    /** Simulates shuttle back button clicked. */
    public void pressShuttleBack() {
        shuttleBackButton.doClick();
    }

    /** Simulates find button clicked. */
    public void pressFind() {
        findButton.doClick();
    }

    /** Simulates set cell onset button clicked. */
    public void pressSetCellOnset() {
        setCellOnsetButton.doClick();
    }

    /** Simulates set cell offset button clicked. */
    public void pressSetCellOffset() {
        setCellOffsetButton.doClick();
    }

    /** Simulates set new cell onset button clicked. */
    public void pressSetNewCellOnset() {
        setNewCellOffsetButton.doClick();
    }

    /** Simulates go back button clicked. */
    public void pressGoBack() {
        goBackButton.doClick();
    }

    /** Simulates create new cell button clicked. */
    public void pressCreateNewCell() {
        createNewCell.doClick();
    }

    /** Simulates create new cell setting offset button clicked. */
    public void pressCreateNewCellSettingOffset() {
        createNewCellSettingOffset.doClick();
    }

    /** Simulates sync button clicked. */
    public void pressSyncButton() {
        syncButton.doClick();
    }

    /** Simulates sync button clicked. */
    public void pressSyncCtrlButton() {
        syncCtrlButton.doClick();
    }

    /** Simulates sync button clicked. */
    public void pressSyncVideoButton() {
        syncVideoButton.doClick();
    }

    // --------------------------------------------------------------------------
    // Playback actions
    //
    /**
     * Action to invoke when the user clicks on the play button.
     */
    @Action
    public void playAction() {
        playAt(PLAY_RATE);
    }

    /**
     * Action to invoke when the user clicks on the fast foward button.
     */
    @Action
    public void forwardAction() {
        playAt(FFORWARD_RATE);
    }

    /**
     * Action to invoke when the user clicks on the rewind button.
     */
    @Action
    public void rewindAction() {
        playAt(REWIND_RATE);
    }

    /**
     * Action to invoke when the user clicks on the pause button.
     */
    @Action
    public void pauseAction() {
        // Resume from pause at playback rate prior to pause.
        if (clock.isStopped()) {
            shuttleAt(pauseRate);

            // Pause views - store current playback rate.
        } else {
            pauseRate = clock.getRate();
            clock.stop();
            lblSpeed.setText("["
                    + FloatUtils.doubleToFractionStr(new Double(pauseRate))
                    + "]");
        }
    }

    /**
     * Action to invoke when the user clicks on the stop button.
     */
    @Action
    public void stopAction() {
        clock.stop();
        clock.setRate(0);
        shuttleRate = 0;
        pauseRate = 0;
        shuttleDirection = ShuttleDirection.UNDEFINED;
    }

    /**
     * Action to invoke when the user clicks on the shuttle forward button.
     * 
     * @todo proper behaviour for reversing shuttle direction?
     */
    @Action
    public void shuttleForwardAction() {
        if (clock.getTime() <= 0
                && (shuttleRate != 0 || shuttleDirection != ShuttleDirection.UNDEFINED)) {
            shuttleRate = 0;
            pauseRate = 0;
            shuttleDirection = ShuttleDirection.UNDEFINED;
            shuttle(ShuttleDirection.FORWARDS);
        } else {
            // BugzID:794 - Previously ignored pauseRate if paused
            if (clock.isStopped()) {
                shuttleRate = findShuttleIndex(pauseRate);
                shuttle(ShuttleDirection.FORWARDS);
                // shuttle(ShuttleDirection.BACKWARDS);
                // This makes current tests fail, but may be the desired
                // functionality.
            } else {
                shuttle(ShuttleDirection.FORWARDS);
            }
        }
    }

    /**
     * Action to invoke when the user clicks on the shuttle back button.
     */
    @Action
    public void shuttleBackAction() {
        if (clock.getTime() <= 0
                && (shuttleRate != 0 || shuttleDirection != ShuttleDirection.UNDEFINED)) {
            shuttleRate = 0;
            pauseRate = 0;
            shuttleDirection = ShuttleDirection.UNDEFINED;
        } else {
            // BugzID:794 - Previously ignored pauseRate if paused
            if (clock.isStopped()) {
                shuttleRate = findShuttleIndex(pauseRate);
                shuttle(ShuttleDirection.BACKWARDS);
                // shuttle(ShuttleDirection.FORWARDS);
                // This makes current tests fail, but may be the desired
                // functionality.
            } else {
                shuttle(ShuttleDirection.BACKWARDS);
            }
        }
    }

    /**
     * Searches the shuttle rates array for the given rate, and returns the
     * index.
     * 
     * @param pRate
     *            The rate to search for.
     * @return The index of the rate, or -1 if not found.
     */
    private int findShuttleIndex(final float pRate) {
        if (pRate == 0) {
            return 0;
        }
        for (int i = 0; i < SHUTTLE_RATES.length; i++) {
            if (SHUTTLE_RATES[i] == pRate || SHUTTLE_RATES[i] == pRate * (-1)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Populates the find time in the controller.
     * 
     * @param milliseconds
     *            The time to use when populating the find field.
     */
    public void setFindTime(final long milliseconds) {
        findTextField.setText(CLOCK_FORMAT.format(milliseconds));
    }

    /**
     * Populates the find offset time in the controller.
     * 
     * @param milliseconds
     *            The time to use when populating the find field.
     */
    public void setFindOffsetField(final long milliseconds) {
        findOffsetField.setText(CLOCK_FORMAT.format(milliseconds));
    }

    /**
     * Action to invoke when the user clicks on the find button.
     */
    @Action
    public void findAction() {
        if (shiftMask) {
            findOffsetAction();
        } else {
            try {
                jumpTo(CLOCK_FORMAT.parse(findTextField.getText()).getTime());
            } catch (ParseException e) {
                logger.error("unable to find within video", e);
            }
        }
    }

    /**
     * Action to invoke when the user holds shift down.
     */
    public void findOffsetAction() {
        try {
            jumpTo(CLOCK_FORMAT.parse(findOffsetField.getText()).getTime());
        } catch (ParseException e) {
            logger.error("unable to find within video", e);
        }
    }

    /**
     * Sets the playback region of interest to lie from the find time to offset
     * time.
     */
    public void setRegionOfInterestAction() {
        try {
            final long newWindowPlayStart =
                    CLOCK_FORMAT.parse(findTextField.getText()).getTime();
            final long newWindowPlayEnd =
                    CLOCK_FORMAT.parse(findOffsetField.getText()).getTime();

            windowPlayStart = newWindowPlayStart;
            mixerControllerV.setPlayRegionStart(newWindowPlayStart);

            if (newWindowPlayStart < newWindowPlayEnd) {
                windowPlayEnd = newWindowPlayEnd;
                mixerControllerV.setPlayRegionEnd(newWindowPlayEnd);
            } else {
                windowPlayEnd = newWindowPlayStart;
                mixerControllerV.setPlayRegionEnd(newWindowPlayStart);
            }
        } catch (ParseException e) {
            logger.error("Unable to set playback region of interest", e);
        }
    }

    /**
     * Action to invoke when the user clicks on the go back button.
     */
    @Action
    public void goBackAction() {
        try {
            long j = -CLOCK_FORMAT.parse(goBackTextField.getText()).getTime();
            jump(j);

            // BugzID:721 - After going back - start playing again.
            playAt(PLAY_RATE);

        } catch (ParseException e) {
            logger.error("unable to find within video", e);
        }
    }

    /**
     * Action to invoke when the user clicks on the jog backwards button.
     */
    @Action
    public void jogBackAction() {
        int mul = 1;
        if (shiftMask) {
            mul = SHIFTJOG;
        }
        if (ctrlMask) {
            mul = CTRLSHIFTJOG;
        }
        jump((long) (mul * (-ONE_SECOND) / currentFPS));
    }

    /**
     * Action to invoke when the user clicks on the jog forwards button.
     */
    @Action
    public void jogForwardAction() {
        int mul = 1;
        if (shiftMask) {
            mul = SHIFTJOG;
        }
        if (ctrlMask) {
            mul = CTRLSHIFTJOG;
        }
        jump((long) ((mul * ONE_SECOND) / currentFPS));
    }

    // --------------------------------------------------------------------------
    // [private] play back action helper functions
    //
    /**
     * @param rate
     *            Rate of play.
     */
    private void playAt(final float rate) {
        shuttleDirection = ShuttleDirection.UNDEFINED;
        shuttleRate = 0;
        pauseRate = 0;
        shuttleAt(rate);
    }

    /**
     * @param direction
     *            The required direction of the shuttle.
     */
    private void shuttle(final ShuttleDirection direction) {
        float rate = SHUTTLE_RATES[shuttleRate];
        if (ShuttleDirection.UNDEFINED == shuttleDirection) {
            shuttleDirection = direction;
            rate = SHUTTLE_RATES[0];

        } else if (direction == shuttleDirection) {
            if (shuttleRate < (SHUTTLE_RATES.length - 1)) {
                rate = SHUTTLE_RATES[++shuttleRate];
            }

        } else {
            if (shuttleRate > 0) {
                rate = SHUTTLE_RATES[--shuttleRate];

                // BugzID: 676 - Shuttle speed transitions between zero.
            } else {
                rate = 0;
                shuttleDirection = ShuttleDirection.UNDEFINED;
            }
        }

        shuttleAt(shuttleDirection.getParameter() * rate);
    }

    /**
     * @param rate
     *            Rate of shuttle.
     */
    private void shuttleAt(final float rate) {
        clock.setRate(rate);
        clock.start();
    }

    /**
     * @param step
     *            Milliseconds to jump.
     */
    private void jump(final long step) {
        clock.stop();
        clock.setRate(0);
        shuttleRate = 0;
        pauseRate = 0;
        shuttleDirection = ShuttleDirection.UNDEFINED;
        clock.stepTime(step);
    }

    /**
     * @param time
     *            Absolute time to jump to.
     */
    private void jumpTo(final long time) {
        clock.stop();
        clock.setRate(PLAY_RATE);
        clock.setTime(time);
    }

    // --------------------------------------------------------------------------
    //
    //
    /**
     * Action to invoke when the user clicks on the create new cell button.
     */
    @Action
    public void createCellAction() {
        new CreateNewCellC();
    }

    /**
     * Action to invoke when the user clicks on the new cell button.
     */
    @Action
    public void createNewCellAction() {
        new CreateNewCellC(getCurrentTime());
    }

    /**
     * Action to invoke when the user clicks on the new cell onset button.
     */
    @Action
    public void setNewCellStopTime() {
        new SetNewCellStopTimeC(getCurrentTime());
        setFindOffsetField(getCurrentTime());
    }

    /**
     * Action to invoke when the user clicks on the sync video button.
     */
    @Action
    public void syncVideoAction() {
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createNewCell;
    private javax.swing.JButton createNewCellSettingOffset;
    private javax.swing.JButton findButton;
    private javax.swing.JTextField findOffsetField;
    private javax.swing.JTextField findTextField;
    private javax.swing.JButton forwardButton;
    private javax.swing.JButton goBackButton;
    private javax.swing.JTextField goBackTextField;
    private javax.swing.JPanel gridButtonPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JButton jogBackButton;
    private javax.swing.JButton jogForwardButton;
    private javax.swing.JLabel lblSpeed;
    private javax.swing.JButton openVideoButton;
    private javax.swing.JButton pauseButton;
    private javax.swing.JButton playButton;
    private javax.swing.JButton rewindButton;
    private javax.swing.JButton setCellOffsetButton;
    private javax.swing.JButton setCellOnsetButton;
    private javax.swing.JButton setNewCellOffsetButton;
    private javax.swing.JButton showTracksButton;
    private javax.swing.JButton shuttleBackButton;
    private javax.swing.JButton shuttleForwardButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JButton syncButton;
    private javax.swing.JButton syncCtrlButton;
    private javax.swing.JButton syncVideoButton;
    private javax.swing.JLabel timestampLabel;
    private javax.swing.JPanel topPanel;
    private javax.swing.JPanel tracksPanel;
    // End of variables declaration//GEN-END:variables
}
