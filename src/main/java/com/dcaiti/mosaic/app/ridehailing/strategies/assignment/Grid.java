package com.dcaiti.mosaic.app.ridehailing.strategies.assignment;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;

import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;

public class Grid {
    private static final double GRID_SIZE = 2000; // Grid size in meters
    private static List<VehicleStatus> candidateShuttles = new LinkedList<>();
    // Map: coordinates to list of shuttles
    private static Map<String, List<VehicleStatus>> grid = new HashMap<>();

    private static List<VehicleStatus> finalListOfCandidateShuttles = new LinkedList<>();
    
    // TODO: test this method
    public static Map<String, List<VehicleStatus>> createGridFromShuttlePositions(Map<String, VehicleStatus> registeredShuttles) {
        candidateShuttles.clear();
        grid.clear();

        registeredShuttles.values().forEach(shuttle -> {
            // Fail-safe
            if (shuttle.hasEnoughCapacity()) candidateShuttles.add(shuttle);
        });

        assignShuttleToGridCell();

        return grid;
    }

    private static void assignShuttleToGridCell() {
        candidateShuttles.forEach(shuttle -> {
            GeoPoint shuttleLocation = shuttle.getCurrentPosition();
            int x = getGridCellIndex(shuttleLocation.toCartesian().getX());
            int y = getGridCellIndex(shuttleLocation.toCartesian().getY());

            String key = String.valueOf(x) + "," + String.valueOf(y);
            VehicleStatus value = shuttle;

            if (!grid.containsKey(key)) grid.put(key, new LinkedList<>());
            grid.get(key).add(value);
        });
    }

    private static int getGridCellIndex(double num) {
        int intNum = (int) (num / GRID_SIZE);
        int transformedNum = checkNegativity(intNum);

        return transformedNum;
    }

    private static int checkNegativity(int num) {
        return num < 0 ? num - 1 : num;
    }

    // TODO: test this method
    public static VehicleStatus getShuttle(Ride passenger) {
        finalListOfCandidateShuttles.clear();
        
        GeoPoint passengerLocation = passenger.getPickupLocation().getGeoPoint();
        int x = getGridCellIndex(passengerLocation.toCartesian().getX());
        int y = getGridCellIndex(passengerLocation.toCartesian().getY());

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                String key = String.valueOf(x + i) + "," + String.valueOf(y + j);

                if (!grid.containsKey(key)) continue;
                grid.get(key).forEach(shuttle -> finalListOfCandidateShuttles.add(shuttle));
            }
        }

        Collections.shuffle(finalListOfCandidateShuttles);
        finalListOfCandidateShuttles.stream().limit(5).toList();
        
        if (finalListOfCandidateShuttles.isEmpty()) return null;
        if (finalListOfCandidateShuttles.size() == 1) return finalListOfCandidateShuttles.get(0);

        VehicleStatus selectedShuttle = getClosestShuttle(passenger, finalListOfCandidateShuttles);

        return selectedShuttle;
    }

    private static VehicleStatus getClosestShuttle(Ride passenger, List<VehicleStatus> selectedShuttles) {
        VehicleStatus closestShuttle = null;
        double minDistance = Double.MAX_VALUE;

        for (VehicleStatus shuttle : selectedShuttles) {
            double distance = distance(passenger.getPickupLocation().getGeoPoint().toCartesian(), shuttle.getCurrentPosition().toCartesian());

            if (distance < minDistance) {
                minDistance = distance;
                closestShuttle = shuttle;
            }
        }

        return closestShuttle;
    }

    private static double distance(CartesianPoint p1, CartesianPoint p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }
}
