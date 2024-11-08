package com.dcaiti.mosaic.app.ridehailing.server;

import org.eclipse.mosaic.lib.geo.GeoPoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class VehicleStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int VEHICLE_CAPACITY = 4;

    private final String vehicleId;
    private final GeoPoint currentPosition;
    private List<Ride> listOfCurrentRides = new ArrayList<>();
    private final Ride currentRide;
    private final double totalDistanceDriven;

    public VehicleStatus(String vehicleId, GeoPoint currentPosition, double distanceDriven, List<Ride> listOfCurrentRides, Ride currentRide) {
        this.vehicleId = vehicleId;
        this.currentPosition = currentPosition;
        this.listOfCurrentRides = listOfCurrentRides;
        this.currentRide = currentRide;
        this.totalDistanceDriven = distanceDriven;
    }

    public boolean hasEnoughCapacity() {
        return listOfCurrentRides.size() < VEHICLE_CAPACITY;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public GeoPoint getCurrentPosition() {
        return currentPosition;
    }

    public List<Ride> getlistOfCurrentRides() {
        return listOfCurrentRides;
    }

    public Ride getCurrentRide() {
        return currentRide;
    }

    public double getTotalDistanceDriven() {
        return totalDistanceDriven;
    }
}
