package org.fest.swing.fixture;

import static org.fest.reflect.core.Reflection.field;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Polygon;

import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.openshapa.controllers.component.NeedleController;
import org.openshapa.util.UIUtils;
import org.openshapa.views.component.NeedlePainter;

/**
 * Fixture for OpenSHAPA NeedlePainter.
 */
public class NeedleFixture extends ComponentFixture {
    /** The underlying mixercontroller. */
    private NeedleController needleC;

    /**
     * Constructor.
     * @param robot
     *            mainframe robot
     * @param target
     *            NeedleController
     */
    public NeedleFixture(final Robot robot, final NeedleController target) {
        super(robot, target.getView());
        needleC = target;
    }

    /**
     * @return Current time represented by the needle as a long.
     */
    public long getCurrentTimeAsLong() {
        return needleC.getCurrentTime();
    }

    /**
     * @return Current time represented by the needle as a timestamp.
     */
    public String getCurrentTimeAsTimeStamp() {
        return UIUtils.millisecondsToTimestamp(needleC.getCurrentTime());
    }

    public boolean isMouseOnNeedleHead() {
        return ((NeedlePainter) target).contains(MouseInfo.getPointerInfo()
                .getLocation());
    }

    /**
     * Drag number of pixels left (negative) or right (positive).
     * @param pixels
     */
    public void drag(final int pixels) {
        // Hold down left mouse button
        robot.pressMouse(getCenterOfMarker(), MouseButton.LEFT_BUTTON);

        // Move mouse to new position
        Point to = new Point(getCenterOfMarker().x + pixels,
                getCenterOfMarker().y);
        robot.moveMouse(to);

        // Release mouse
        robot.releaseMouse(MouseButton.LEFT_BUTTON);
    }

    /**
     * @return a point at the centre of the needle head.
     */
    private Point getCenterOfMarker() {
        /*
         * There are actually 4 points that define the needle polygon triangle,
         * because it has a slightly flat base (1pixel). We ignore the 4th
         * point.
         */
        Polygon needleMarker =
                field("needleMarker").ofType(Polygon.class).in(target).get();

        // Find middle x position
        int xPos =
                needleMarker.xpoints[2]
                        + ((NeedlePainter) target).getLocationOnScreen().x;
        // Find middle y position
        int yPos =
                (Math.max(Math.max(needleMarker.ypoints[0],
                        needleMarker.ypoints[1]), needleMarker.ypoints[2]) / 2)
                        + ((NeedlePainter) target).getLocationOnScreen().y;

        Point centrePoint = new Point(xPos, yPos);

        return centrePoint;
    }
}