package com.dcaiti.mosaic.app.ridehailing.server;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class VehicleStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int VEHICLE_CAPACITY = 4;

    private final String vehicleId;
    private final GeoPoint currentPosition;
    private final List<Ride> currentRides;
    private final double totalDistanceDriven;
    
    private List<Ride> rides = new ArrayList<>();
    private Queue<VehicleStop> stops = new LinkedList<>();
    private Queue<CandidateRoute> routes = new LinkedList<>();

    public VehicleStatus(String vehicleId, GeoPoint currentPosition, List<Ride> currentRides, List<Ride> rides, Queue<VehicleStop> stops, Queue<CandidateRoute> routes, double distanceDriven) {
        this.vehicleId = vehicleId;
        this.currentPosition = currentPosition;
        this.currentRides = currentRides;
        this.rides = rides;
        this.stops = stops;
        this.routes = routes;
        this.totalDistanceDriven = distanceDriven;
    }

    public boolean hasEnoughCapacity() {
        return currentRides.size() < VEHICLE_CAPACITY;
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

    public List<Ride> getRides() {
        return rides;
    }

    public Queue<VehicleStop> getStops() {
        return stops;
    }
    
    public Queue<CandidateRoute> getRoutes() {
        return routes;
    }

    public double getTotalDistanceDriven() {
        return totalDistanceDriven;
    }
}
