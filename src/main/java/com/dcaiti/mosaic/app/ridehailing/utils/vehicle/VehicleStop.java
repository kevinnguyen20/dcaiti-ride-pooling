package com.dcaiti.mosaic.app.ridehailing.utils.vehicle;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;

public class VehicleStop implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum StopReason {
        PICK_UP, DROP_OFF, WAITING
    }

    transient IRoadPosition positionOnRoad;

    private final GeoPoint geoPoint;

    private final int rideId;

    private final StopReason stopReason;

    private long waitUntil = 0;

    public VehicleStop(IRoadPosition roadPosition, StopReason stopReason) {
        this.positionOnRoad = roadPosition;
        this.rideId = -1;
        this.stopReason = stopReason;

        if (roadPosition.getPreviousNode() != null) {
            double direction = GeoUtils.azimuth(roadPosition.getPreviousNode().getPosition(), roadPosition.getUpcomingNode().getPosition());
            this.geoPoint = GeoUtils.getGeoPointFromDirection(roadPosition.getPreviousNode().getPosition(), direction, roadPosition.getOffset());
        } else if (roadPosition.getConnection() != null && roadPosition.getConnection().getStartNode() != null) {
            double direction = GeoUtils.azimuth(roadPosition.getConnection().getStartNode().getPosition(), roadPosition.getConnection().getEndNode().getPosition());
            this.geoPoint = GeoUtils.getGeoPointFromDirection(roadPosition.getConnection().getStartNode().getPosition(), direction, roadPosition.getOffset());
        } else {
            throw new IllegalStateException("Cannot calculate stop position");
        }

    }

    public VehicleStop(int rideId, GeoPoint location, StopReason reason) {
        this.rideId = rideId;
        this.geoPoint = location;
        this.stopReason = reason;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public int getRideId() {
        return rideId;
    }

    public GeoPoint getGeoPoint() {
        return geoPoint;
    }

    public IRoadPosition getPositionOnRoad() {
        return positionOnRoad;
    }

    public void setPositionOnRoad(IRoadPosition positionOnRoad) {
        this.positionOnRoad = positionOnRoad;
    }

    public void setWaitUntil(long waitUntil) {
        this.waitUntil = waitUntil;
    }

    public long getWaitUntil() {
        return waitUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VehicleStop rideStop = (VehicleStop) o;
        return new EqualsBuilder()
                .append(rideId, rideStop.rideId)
                .append(stopReason, rideStop.stopReason)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(geoPoint)
                .append(positionOnRoad)
                .append(rideId)
                .append(stopReason)
                .toHashCode();
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("geoPoint", geoPoint)
                .toString();
    }


}
