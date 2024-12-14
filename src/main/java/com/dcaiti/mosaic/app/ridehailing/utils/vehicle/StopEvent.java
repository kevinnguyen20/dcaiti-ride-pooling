package com.dcaiti.mosaic.app.ridehailing.utils.vehicle;

import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.lib.util.scheduling.EventProcessor;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class StopEvent extends Event {

    private final VehicleStop rideStop;
    private final String vehicle;

    public StopEvent(long time, String vehicle, VehicleStop rideStop, EventProcessor processor) {
        super(time, processor);
        this.vehicle = vehicle;
        this.rideStop = rideStop;
    }

    public VehicleStop getRideStop() {
        return rideStop;
    }

    public String getVehicle() {
        return vehicle;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .appendSuper(super.hashCode())
                .append(vehicle)
                .append(rideStop)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj.getClass() != getClass()) return false;

        StopEvent se = (StopEvent) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.vehicle, se.vehicle)
                .append(this.rideStop, se.rideStop)
                .isEquals();
    }
}
