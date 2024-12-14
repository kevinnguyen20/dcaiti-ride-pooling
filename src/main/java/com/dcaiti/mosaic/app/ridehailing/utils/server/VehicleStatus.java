package com.dcaiti.mosaic.app.ridehailing.utils.server;

import org.eclipse.mosaic.lib.geo.GeoPoint;

import java.io.Serializable;
import java.util.List;

public class VehicleStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String vehicleId;
    private final GeoPoint currentPosition;
    private final List<Ride> currentRides;
    private final double totalDistanceDriven;
    private int vehicleCapacity = 2;

    public VehicleStatus(String vehicleId, GeoPoint currentPosition, List<Ride> currentRides, double distanceDriven) {
        this.vehicleId = vehicleId;
        this.currentPosition = currentPosition;
        this.currentRides = currentRides;
        this.totalDistanceDriven = distanceDriven;
    }

    public boolean hasEnoughCapacity() {
        return currentRides.size() < vehicleCapacity;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public GeoPoint getCurrentPosition() {
        return currentPosition;
    }

    public List<Ride> getCurrentRides() {
        return currentRides;
    }

    public double getTotalDistanceDriven() {
        return totalDistanceDriven;
    }

    public int getVehicleCapacity() {
        return vehicleCapacity;
    }

    public void setVehicleCapacity(int vehicleCapacity) {
        this.vehicleCapacity = vehicleCapacity;
    }
}
