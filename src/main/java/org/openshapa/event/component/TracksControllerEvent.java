package org.openshapa.event.component;

import java.util.EventObject;


/**
 * Event object used to inform listeners about child component events
 */
public class TracksControllerEvent extends EventObject {

    /**
     * Auto generated by Eclipse
     */
    private static final long serialVersionUID = 6049024296868823563L;

    public static enum TracksEvent {
        NEEDLE_EVENT, /** @see NeedleEvent */ MARKER_EVENT, /** @see MarkerEvent */
        CARRIAGE_EVENT, /** @see CarriageEvent */ TIMESCALE_EVENT
    }

    /** Needle event from child component */
    private EventObject eventObject;

    /** Type of track event that happened */
    private TracksEvent tracksEvent;

    public TracksControllerEvent(final Object source,
        final TracksEvent tracksEvent, final EventObject eventObject) {
        super(source);
        this.eventObject = eventObject;
        this.tracksEvent = tracksEvent;
    }

    /**
     * @return Needle event from child component
     */
    public EventObject getEventObject() {
        return eventObject;
    }

    /**
     * @return Type of track event that happened
     */
    public TracksEvent getTracksEvent() {
        return tracksEvent;
    }

}
