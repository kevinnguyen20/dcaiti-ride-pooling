package com.dcaiti.mosaic.app.ridehailing.heuristics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import com.dcaiti.mosaic.app.ridehailing.strategies.fleet.FleetManagement;
import com.dcaiti.mosaic.app.ridehailing.utils.heuristics.HeuristicsUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.routing.RoutingUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

public class InsertionHeuristic {
    private static Map<String, List<Ride>> allRides;
    private static Map<String, Queue<VehicleStop>> currentStops;
    private static Map<String, Queue<CandidateRoute>> currentRoutes;

    public static void assignBookingsToShuttles(
        Map<Integer, Ride> storedRides,
        Map<String, VehicleStatus> registeredShuttles,
        List<Ride> newBookings,
        Map<String, List<Ride>> rides,
        Map<String, Queue<VehicleStop>> stops,
        Map<String, Queue<CandidateRoute>> routes) {

        allRides = rides;
        currentStops = stops;
        currentRoutes = routes;

        List<VehicleStatus> shuttles = FleetManagement.getAllShuttlesWithEnoughCapacity(registeredShuttles);

        newBookings.forEach(booking -> {
            // Decline booking if not shuttles are available
            if (shuttles.isEmpty()) {
                booking.setStatus(Ride.Status.DECLINED);
                return;
            }

            // Retrieve a random shuttle (other strategies possible/recommended)
            Random random = new Random();
            int randomIndex = random.nextInt(shuttles.size());
            VehicleStatus shuttle = shuttles.get(0);

            // Check for duplicate locations
            if (booking.getStatus() == Ride.Status.REJECTED ||
                HeuristicsUtils.checkForDuplicateCoordinates(shuttle, booking) ||
                HeuristicsUtils.hasIdenticalPickupAndDropoff(shuttle, booking)) {

                booking.setStatus(Ride.Status.REJECTED);
                return;
            }

            String shuttleId = shuttle.getVehicleId();
            allRides.putIfAbsent(shuttleId, new LinkedList<>());
            eraseHistoricStopsAndRoutes(shuttleId);

            // Update ride status
            booking.setAssignedVehicleId(shuttleId);
            booking.setStatus(Ride.Status.ASSIGNED);

            // Update rides
            allRides.get(shuttleId).add(booking);

            // Update stops
            updateStops(booking, shuttle);

            // Remove shuttle after assignment
            shuttles.remove(0);

            // Update routes
            updateRoutes(shuttle);
        });
    }

    private static void updateStops(Ride booking, VehicleStatus shuttle) {
        List<VehicleStop> currentShuttleStops = shuttle.getCurrentStops();
        List<CartesianPoint> currentStopsCartesian = new LinkedList<>();
        for (VehicleStop stop : currentShuttleStops) {
            currentStopsCartesian.add(RoutingUtils.centerOf(stop.getPositionOnRoad().getConnection()).toCartesian());
        }
        
        // Get the shuttle position
        IRoadPosition shuttlePositionOnRoad = RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition());

        // Get pickup and dropoff location
        VehicleStop pickup = booking.getPickupLocation();
        VehicleStop dropoff = booking.getDropoffLocation();
        RoutingUtils.addPositionOnRoad(pickup, shuttlePositionOnRoad);
        RoutingUtils.addPositionOnRoad(dropoff, shuttlePositionOnRoad);
        CartesianPoint pickupCartesianPoint = HeuristicsUtils.getCartesianPoint(pickup);
        CartesianPoint dropoffCartesianPoint = HeuristicsUtils.getCartesianPoint(dropoff);

        // Insertion heuristic
        CartesianPoint shuttlePositionCartesian = RoutingUtils.centerOf(shuttlePositionOnRoad.getConnection()).toCartesian();
        List<CartesianPoint> result = new ArrayList<>();
        double minDistance = Double.MAX_VALUE;

        if (shuttle.getCurrentRides().isEmpty()) {
            result.add(pickupCartesianPoint);
            result.add(dropoffCartesianPoint);
        } else {
            // First stop is always current shuttle position
            int start = 1;
            for (int i = start; i < shuttle.getCurrentRides().size() + 1; i++) {
                for (int j = i; j < shuttle.getCurrentRides().size() + 1; j++) {
                    List<CartesianPoint> tmp = new LinkedList<>(currentStopsCartesian);

                    // Insert current shuttle position
                    tmp.add(0, shuttlePositionCartesian);
                    
                    // Insert new stops
                    tmp.add(i, pickupCartesianPoint);
                    tmp.add(j + 1, dropoffCartesianPoint);

                    double distance = HeuristicsUtils.getTotalDistance(currentStopsCartesian);

                    if (distance < minDistance) {
                        minDistance = distance;
                        result = new ArrayList<>(tmp);
                    }
                }
            }
            
            // Remove the shuttle position
            result.remove(0);
        }

        // Map points back to stops
        convertToVehicleStops(result, pickup, dropoff, shuttle);
    }
    
    private static void updateRoutes(VehicleStatus shuttle) {
        // Determine the shuttle position on the road
        IRoadPosition shuttlePositionOnRoad = RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition());
        
        // Set road positions for upcoming stops
        Queue<VehicleStop> stops = currentStops.get(shuttle.getVehicleId());
        stops.forEach(stop -> RoutingUtils.addPositionOnRoad(stop, shuttlePositionOnRoad));
        
        List<VehicleStop> tmp = (LinkedList<VehicleStop>) stops;
        
        // Add the first route from shuttle to the first stop
        currentRoutes.get(shuttle.getVehicleId()).add(RoutingUtils.getBestRoute(shuttlePositionOnRoad, tmp.get(0).getPositionOnRoad()));
        
        // Add the routes between upcoming stops
        for (int i = 0; i < stops.size() - 1; i++) {
            CandidateRoute c = RoutingUtils.getBestRoute(tmp.get(i).getPositionOnRoad(), tmp.get(i + 1).getPositionOnRoad());

            currentRoutes.get(shuttle.getVehicleId()).add(c);
        }
    }
    
    private static void convertToVehicleStops(List<CartesianPoint> points, VehicleStop pickup, VehicleStop dropoff, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();
        List<VehicleStop> currentShuttleStops = shuttle.getCurrentStops();
        Queue<VehicleStop> stops = currentStops.get(shuttleId);

        Map<CartesianPoint, VehicleStop> tmp = new HashMap<>();
        tmp.put(HeuristicsUtils.getCartesianPoint(pickup), pickup);
        tmp.put(HeuristicsUtils.getCartesianPoint(dropoff), dropoff);
        for (VehicleStop stop : currentShuttleStops) {
            tmp.put(HeuristicsUtils.getCartesianPoint(stop), stop);
        }

        points.forEach(point -> {
            if (tmp.containsKey(point)) stops.add(tmp.get(point));
        });
    }

    private static void eraseHistoricStopsAndRoutes(String shuttleId) {
        currentStops.put(shuttleId, new LinkedList<>());
        currentRoutes.put(shuttleId, new LinkedList<>());
    }
}
