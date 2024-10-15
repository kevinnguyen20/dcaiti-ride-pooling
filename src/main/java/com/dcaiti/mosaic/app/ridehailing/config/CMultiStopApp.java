package com.dcaiti.mosaic.app.ridehailing.config;

import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.util.gson.TimeFieldAdapter;
import org.eclipse.mosaic.rti.TIME;

import com.google.gson.annotations.JsonAdapter;

public class CMultiStopApp {

    /**
     * The usual time the vehicle will stop at a stop location
     * before proceeding the journey.
     */
    @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
    public long stopTime = 20 * TIME.SECOND;

    /**
     * The time the vehicle will wait at most at the stop position, if
     * a wait time is given for the stop.
     */
    @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
    public long maxStopTime = 2 * TIME.MINUTE;

    /**
     * Defines the mode for stopping. Use "PARK_ON_ROADSIDE" or "STOP".
     */
    public VehicleStopMode stopMode = VehicleStopMode.PARK_ON_ROADSIDE;

    /**
     * If set to true, routing to the next stop location will consider turn costs.
     */
    public boolean considerTurnCosts = false;

    /**
     * If set to true, the vehicle waits at the drop-off stop
     * until the given time is reached, but it waits not more than
     * what is configured in maxStopTime.
     */
    public boolean waitUntilDropOffTime = false;

    /**
     * If set to true, the vehicle waits at the pick-up stop
     * until the given time is reached, but it waits not more than
     * what is configured in maxStopTime.
     */
    public boolean waitUntilPickUpTime = false;

    /**
     * If set, the simulation fails when a route to the next stop
     * cannot be calculated. If set to false, the vehicle will
     * wait at its last stop position and won't be able to process any further stops.
     */
    public boolean exitOnRoutingFailure = false;
}
