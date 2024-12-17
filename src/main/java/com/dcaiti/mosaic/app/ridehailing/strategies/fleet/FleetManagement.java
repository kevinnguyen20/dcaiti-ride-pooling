package com.dcaiti.mosaic.app.ridehailing.strategies.fleet;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;

public class FleetManagement {
    // Homogenous vehicle fleet
    public static final int SHUTTLE_CAPACITY = 2;
    private static Map<String, List<VehicleStatus>> vehicleFleet = new HashMap<>();

    public static Map<String, List<VehicleStatus>> analyzeFleet(Map<String, VehicleStatus> registeredShuttles) {
        // Clear the old status of the fleet
        initVehicleFleet();

        // Divide vehicle fleet by capacity
        registeredShuttles.values().forEach(shuttle -> {
            List<Ride> currentRides = shuttle.getCurrentRides();
            if (currentRides.isEmpty()) vehicleFleet.get("idleShuttles").add(shuttle);
            else if (!currentRides.isEmpty() && shuttle.hasEnoughCapacity()) vehicleFleet.get("partlyOccupiedShuttles").add(shuttle);
            else if (!currentRides.isEmpty() && !shuttle.hasEnoughCapacity())
            vehicleFleet.get("fullyOccupiedShuttles").add(shuttle);
        });
        
        return vehicleFleet;
    }

    private static void initVehicleFleet() {
        vehicleFleet.clear();

        vehicleFleet.put("idleShuttles", new LinkedList<>());
        vehicleFleet.put("partlyOccupiedShuttles", new LinkedList<>());
        vehicleFleet.put("fullyOccupiedShuttles", new LinkedList<>());
    }

    public static List<VehicleStatus> getShuttlesWithSpecificCapacity(Map<String, List<VehicleStatus>> vehicleFleet, int capacity) {
        List<VehicleStatus> shuttles = new LinkedList<>();
        
        if (capacity == 0) return getIdleShuttles(vehicleFleet);
        else if (capacity == SHUTTLE_CAPACITY) return getFullyOccupiedShuttles(vehicleFleet);
        else {
            getPartlyOccupiedShuttles(vehicleFleet).forEach(shuttle -> {
                if (shuttle.getCurrentRides().size() == capacity) shuttles.add(shuttle);
            });
        }

        return shuttles;
    }

    public static List<VehicleStatus> getIdleShuttles(Map<String, List<VehicleStatus>> vehicleFleet) {
        return vehicleFleet.get("idleShuttles");
    }

    public static List<VehicleStatus> getPartlyOccupiedShuttles(Map<String, List<VehicleStatus>> vehicleFleet) {
        return vehicleFleet.get("partlyOccupiedShuttles");
    }

    public static List<VehicleStatus> getFullyOccupiedShuttles(Map<String, List<VehicleStatus>> vehicleFleet) {
        return vehicleFleet.get("fullyOccupiedShuttles");
    }
}
