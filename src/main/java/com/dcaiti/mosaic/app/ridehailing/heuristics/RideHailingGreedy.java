package com.dcaiti.mosaic.app.ridehailing.heuristics;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.mosaic.lib.geo.CartesianPoint;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import com.dcaiti.mosaic.app.ridehailing.strategies.fleet.FleetManagement;
import com.dcaiti.mosaic.app.ridehailing.utils.heuristics.HeuristicsUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.routing.RoutingUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

public class RideHailingGreedy {
    private static VehicleStatus candidateShuttle = null;
    private static double shoretstDistance = Double.MAX_VALUE;

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

        // Create list of idle vehicles
        Map<String, List<VehicleStatus>> vehicleFleet = FleetManagement.analyzeFleet(registeredShuttles);
        List<VehicleStatus> shuttles = FleetManagement.getIdleShuttles(vehicleFleet);

        newBookings.forEach(booking -> {
            candidateShuttle = null;
            shoretstDistance = Double.MAX_VALUE;

            VehicleStop pickup = booking.getPickupLocation();

            shuttles.forEach(shuttle -> {
                // Determine the shuttle position on the road
                IRoadPosition shuttlePositionOnRoad = RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition());

                // Set road position for passenger origin
                RoutingUtils.addPositionOnRoad(pickup, shuttlePositionOnRoad);
                CartesianPoint passengerOrigin = HeuristicsUtils.getCartesianPoint(pickup);

                double distance = HeuristicsUtils.distance(passengerOrigin, RoutingUtils.centerOf(shuttlePositionOnRoad.getConnection()).toCartesian());

                if (distance < shoretstDistance) {
                    shoretstDistance = distance;
                    candidateShuttle = shuttle;
                }
            });

            if (candidateShuttle != null) {
                assignBookingToShuttle(booking, candidateShuttle);
                shuttles.remove(candidateShuttle);
            } else {
                booking.setStatus(Ride.Status.DECLINED);
            }
        });
    }

    private static void assignBookingToShuttle(Ride booking, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();

        // Init rides, stops, and routes
        allRides.putIfAbsent(shuttleId, new LinkedList<>());
        eraseHistoricStopsAndRoutes(shuttleId);

        // Update ride status
        booking.setAssignedVehicleId(shuttleId);
        booking.setStatus(Ride.Status.ASSIGNED);

        // Update rides, stops, and routes
        allRides.get(shuttleId).add(booking);
        updateStops(booking, shuttle);
        updateRoutes(shuttle,booking);
    }

    private static void updateStops(Ride booking, VehicleStatus shuttle) {
        // Set road positions for upcoming stops
        Queue<VehicleStop> stops = currentStops.get(shuttle.getVehicleId());
        stops.add(booking.getPickupLocation());
        stops.add(booking.getDropoffLocation());
    }

    private static void updateRoutes(VehicleStatus shuttle, Ride booking) {
        // Determine the shuttle position on the road
        IRoadPosition shuttlePositionOnRoad = RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition());

        // Set road positions for upcoming stops
        RoutingUtils.addPositionOnRoad(booking.getPickupLocation(), shuttlePositionOnRoad);
        RoutingUtils.addPositionOnRoad(booking.getDropoffLocation(), shuttlePositionOnRoad);
        IRoadPosition pickup = booking.getPickupLocation().getPositionOnRoad();
        IRoadPosition dropoff = booking.getDropoffLocation().getPositionOnRoad();

        // Update routes
        currentRoutes.get(shuttle.getVehicleId()).add(RoutingUtils.getBestRoute(shuttlePositionOnRoad, pickup));
        currentRoutes.get(shuttle.getVehicleId()).add(RoutingUtils.getBestRoute(pickup, dropoff));
    }

    private static void eraseHistoricStopsAndRoutes(String shuttleId) {
        currentStops.put(shuttleId, new LinkedList<>());
        currentRoutes.put(shuttleId, new LinkedList<>());
    }
}
