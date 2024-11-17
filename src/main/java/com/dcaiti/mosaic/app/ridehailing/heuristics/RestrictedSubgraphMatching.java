package com.dcaiti.mosaic.app.ridehailing.heuristics;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

public class RestrictedSubgraphMatching extends AbstractHeuristics{

    private static List<Ride> passengers = new LinkedList<>();
    private static final List<VehicleStatus> vehicleIdle = new LinkedList<>();
    private static final List<VehicleStatus> vehicleEnroute = new LinkedList<>();

    public static void assignBookingsToShuttles(
        Map<Integer, Ride> storedRides,
        Map<String, VehicleStatus> registeredShuttles, 
        List<Ride> bookings,
        Map<String, List<Ride>> rides,
        Map<String, Queue<VehicleStop>> stops,
        Map<String, Queue<CandidateRoute>> routes) {
        passengers = bookings;

        registeredShuttles.values().forEach(shuttle -> {
            if (shuttle.hasEnoughCapacity() && shuttle.getCurrentRides().size() > 0) vehicleEnroute.add(shuttle);
            else if (shuttle.getCurrentRides().size() == 0) vehicleIdle.add(shuttle);
        });

        passengers.forEach(passenger -> {
            VehicleStatus candidateShuttle = null;
            VehicleStop pickup = passenger.getPickupLocation();
            VehicleStop dropoff = passenger.getDropoffLocation();

            vehicleEnroute.forEach(shuttle -> {
                
            });

            if (candidateShuttle == null && vehicleIdle.size() > 0) {
                candidateShuttle = vehicleIdle.stream()
                    .min(Comparator.comparingDouble(shuttle -> getDistanceToIdleShuttle(pickup, shuttle.getCurrentPosition())))
                    // .map(VehicleStatus::getVehicleId)
                    .orElse(null);

                assignBookingToIdleShuttle(rides, stops, routes, passenger, candidateShuttle);
            }
        });
    }

    private static boolean isInsideEllipse(CartesianPoint pickup, CartesianPoint dropoff, CartesianPoint testPoint, double majorAxisLength) {
        double distancePickup = distance(dropoff, testPoint);
        double distanceDropoff = distance(dropoff, testPoint);
        
        return distancePickup + distanceDropoff <= majorAxisLength;
    }

    private static double getDistanceToIdleShuttle(VehicleStop pickup, GeoPoint shuttlePosition) {
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttlePosition);

        // Find the position on the road
        // TODO: what if not found
        if (!(addPositionOnRoad(pickup, shuttlePositionOnRoad))) return Double.MAX_VALUE;
        
        return distance(
            centerOf(pickup.getPositionOnRoad().getConnection()).toCartesian(),
            centerOf(shuttlePositionOnRoad.getConnection()).toCartesian());
    }

    private static void assignBookingToIdleShuttle(Map<String, List<Ride>> rides, Map<String, Queue<VehicleStop>> stops, Map<String, Queue<CandidateRoute>> routes, Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();
        
        // Initialize hash maps
        rides.putIfAbsent(shuttleId, new LinkedList<>());
        stops.putIfAbsent(shuttleId, new LinkedList<>());
        routes.putIfAbsent(shuttleId, new LinkedList<>());

        passenger.setAssignedVehicleId(shuttleId);
        passenger.setStatus(Ride.Status.ASSIGNED);

        rides.get(shuttleId).add(passenger);
        stops.get(shuttleId).add(passenger.getPickupLocation());
        stops.get(shuttleId).add(passenger.getDropoffLocation());
        updateRoutes(routes, shuttle, passenger);
    }

    private static void updateRoutes(Map<String, Queue<CandidateRoute>> routes, VehicleStatus shuttle, Ride passenger) {
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttle.getCurrentPosition());

        addPositionOnRoad(passenger.getPickupLocation(), shuttlePositionOnRoad);
        addPositionOnRoad(passenger.getDropoffLocation(), shuttlePositionOnRoad);
        IRoadPosition pickup = passenger.getPickupLocation().getPositionOnRoad();
        IRoadPosition dropoff = passenger.getDropoffLocation().getPositionOnRoad();
        
        routes.get(shuttle.getVehicleId()).add(getBestRoute(shuttlePositionOnRoad, pickup));
        routes.get(shuttle.getVehicleId()).add(getBestRoute(pickup, dropoff));
    }

    private static double distance(CartesianPoint p1, CartesianPoint p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }

    // private static double routeLength() {
    //     return 0.0;
    // }
}
