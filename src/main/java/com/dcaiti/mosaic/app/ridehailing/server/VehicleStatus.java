package com.dcaiti.mosaic.app.ridehailing.server;

import org.eclipse.mosaic.lib.geo.GeoPoint;

import java.io.Serializable;

public class VehicleStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String vehicleId;
    private final GeoPoint currentPosition;
    private final Ride currentRide;
    private final double totalDistanceDriven;

    public VehicleStatus(String vehicleId, GeoPoint currentPosition, double distanceDriven, Ride currentRide) {
        this.vehicleId = vehicleId;
        this.currentPosition = currentPosition;
        this.currentRide = currentRide;
        this.totalDistanceDriven = distanceDriven;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public GeoPoint getCurrentPosition() {
        return currentPosition;
    }

    public Ride getCurrentRide() {
        return currentRide;
    }

    public double getTotalDistanceDriven() {
        return totalDistanceDriven;
    }
}
