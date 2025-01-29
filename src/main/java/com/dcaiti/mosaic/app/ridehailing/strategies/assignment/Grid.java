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
    private static List<VehicleStatus> listOfShuttlesOnCurrentSearchLevel;
    
    public static void createGridFromShuttlePositions(List<VehicleStatus> registeredShuttles) {
        candidateShuttles.clear();
        grid.clear();

        registeredShuttles.forEach(shuttle -> {
            // Fail-safe
            if (shuttle.hasEnoughCapacity()) candidateShuttles.add(shuttle);
        });

        assignShuttleToGridCell();
    }

    private static void assignShuttleToGridCell() {
        candidateShuttles.forEach(shuttle -> {
            // Get coordinates of the pickup location
            GeoPoint shuttleLocation = shuttle.getCurrentPosition();
            int x = getGridCellIndex(shuttleLocation.toCartesian().getX());
            int y = getGridCellIndex(shuttleLocation.toCartesian().getY());

            String key = String.valueOf(x) + "," + String.valueOf(y);
            VehicleStatus value = shuttle;

            // Create a new list if the grid cell is empty
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

    public static List<VehicleStatus> getShuttles(Ride passenger, int searchLevel) {
        // Extract shuttles from the current search level
        getShuttlesOnCurrentSearchLevel(passenger, searchLevel);

        // Limit the number of candidate shuttles, other methods possible
        Collections.shuffle(listOfShuttlesOnCurrentSearchLevel);
        List<VehicleStatus> finalListOfCandidateShuttles = listOfShuttlesOnCurrentSearchLevel.stream()
            .filter(VehicleStatus::hasEnoughCapacity)
            .limit(5)
            .toList();
        
        // Handle edge cases
        // if (finalListOfCandidateShuttles.isEmpty()) return null;
        // if (finalListOfCandidateShuttles.size() == 1) return finalListOfCandidateShuttles.get(0);

        // Get the closest shuttle on the current search level
        // VehicleStatus closestShuttle = getClosestShuttle(passenger, finalListOfCandidateShuttles);

        return finalListOfCandidateShuttles;
        // return closestShuttle;
    }

    private static void getShuttlesOnCurrentSearchLevel(Ride passenger, int searchLevel) {
        // Reset the list of shuttles on the current search level
        listOfShuttlesOnCurrentSearchLevel = new LinkedList<>();

        // Get coordinates from the pickup location
        GeoPoint passengerLocation = passenger.getPickupLocation().getGeoPoint();
        int x = getGridCellIndex(passengerLocation.toCartesian().getX());
        int y = getGridCellIndex(passengerLocation.toCartesian().getY());

        // Extract shuttles from the respective grid cells
        for (int i = -1*searchLevel; i < searchLevel+1; i++) {
            for (int j = -1*searchLevel; j < searchLevel+1; j++) {
                String key = String.valueOf(x + i) + "," + String.valueOf(y + j);

                if (!grid.containsKey(key)) continue;
                grid.get(key).forEach(shuttle -> listOfShuttlesOnCurrentSearchLevel.add(shuttle));
            }
        }
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
