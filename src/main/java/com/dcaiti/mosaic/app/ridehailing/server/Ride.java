package com.dcaiti.mosaic.app.ridehailing.server;

import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;
import org.eclipse.mosaic.lib.geo.GeoPoint;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;

public class Ride implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        PENDING, ASSIGNED, PICKED_UP, DROPPED_OFF, DECLINED
    }

    private final int bookingId;
    private final VehicleStop pickupLocation;
    private final VehicleStop dropoffLocation;
    private final boolean isFinalBooking;

    private Status status;

    private transient String assignedVehicleId;

    private long creationTime;
    private long pickupTime;
    private long dropOffTime;

    private int numberOfRejections;

    public Ride(Ride ride) {
        this.bookingId = ride.bookingId;
        this.status = Status.PENDING;
        this.isFinalBooking = ride.isFinalBooking;

        this.pickupLocation = new VehicleStop(bookingId, ride.pickupLocation.getGeoPoint(), VehicleStop.StopReason.PICK_UP);
        this.pickupLocation.setWaitUntil(ride.pickupLocation.getWaitUntil());

        this.dropoffLocation = new VehicleStop(bookingId, ride.dropoffLocation.getGeoPoint(), VehicleStop.StopReason.DROP_OFF);
        this.dropoffLocation.setWaitUntil(ride.dropoffLocation.getWaitUntil());
    }

    public Ride(int bookingId, GeoPoint pickupLocation, GeoPoint dropoffLocation, boolean isFinalBooking) {
        this.bookingId = bookingId;
        this.status = Status.PENDING;
        this.isFinalBooking = isFinalBooking;
        this.pickupLocation = new VehicleStop(bookingId, pickupLocation, VehicleStop.StopReason.PICK_UP);
        this.dropoffLocation = new VehicleStop(bookingId, dropoffLocation, VehicleStop.StopReason.DROP_OFF);
    }

    public int getBookingId() {
        return bookingId;
    }

    public VehicleStop getPickupLocation() {
        return pickupLocation;
    }

    public VehicleStop getDropoffLocation() {
        return dropoffLocation;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isFinalBooking() {
        return isFinalBooking;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAssignedVehicleId() {
        return assignedVehicleId;
    }

    public void setAssignedVehicleId(String assignedVehicleId) {
        this.assignedVehicleId = assignedVehicleId;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getPickupTime() {
        return pickupTime;
    }

    public long getDropOffTime() {
        return dropOffTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public void setPickupTime(long pickupTime) {
        this.pickupTime = pickupTime;
    }

    public void setDropOffTime(long dropOffTime) {
        this.dropOffTime = dropOffTime;
    }

    public void incrementNumberOfRejections() {
        numberOfRejections++;
    }

    public int getNumberOfRejections() {
        return numberOfRejections;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("bookingId", bookingId)
                .append("creationTime", creationTime)
                .append("pickupLocation", pickupLocation)
                .append("dropoffLocation", dropoffLocation)
                .toString();
    }
}