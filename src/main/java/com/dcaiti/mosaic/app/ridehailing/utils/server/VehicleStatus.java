package com.dcaiti.mosaic.app.ridehailing.utils.server;

import org.eclipse.mosaic.lib.geo.GeoPoint;

import com.dcaiti.mosaic.app.ridehailing.strategies.fleet.FleetManagement;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

import java.io.Serializable;
import java.util.List;

public class VehicleStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String vehicleId;
    private final GeoPoint currentPosition;
    private final List<Ride> currentRides;
    private final List<VehicleStop> currentStops;
    private final double totalDistanceDriven;

    public VehicleStatus(String vehicleId, GeoPoint currentPosition, List<Ride> currentRides, List<VehicleStop> currentStops, double distanceDriven) {
        this.vehicleId = vehicleId;
        this.currentPosition = currentPosition;
        this.currentRides = currentRides;
        this.currentStops = currentStops;
        this.totalDistanceDriven = distanceDriven;
    }

    public boolean hasEnoughCapacity() {
        return currentRides.size() < FleetManagement.SHUTTLE_CAPACITY;
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

    public List<VehicleStop> getCurrentStops() {
        return currentStops;
    }

    public double getTotalDistanceDriven() {
        return totalDistanceDriven;
    }
}
