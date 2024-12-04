package com.dcaiti.mosaic.app.ridehailing.heuristics;

import java.util.ArrayList;
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

public class RestrictedSubgraphMatching extends AbstractHeuristics {
    private static final int VEHICLE_CAPACITY = 2;
    // private static final double VEHICLE_SPEED = 5.28;
    private static boolean init = false;

    private static List<Ride> passengers = new LinkedList<>();
    private static List<VehicleStatus> vehicleIdle = new LinkedList<>();
    private static List<VehicleStatus> vehicleEnroute = new LinkedList<>();
    private static VehicleStatus candidateShuttle = null;

    private static Map<String, List<Ride>> allRides;
    private static Map<String, Queue<VehicleStop>> currentStops;
    private static Map<String, Queue<CandidateRoute>> currentRoutes;

    // TODO: no deadline check yet (if it is even possible to transport in time)
    public static void assignBookingsToShuttles(
        Map<Integer, Ride> storedRides,
        Map<String, VehicleStatus> registeredShuttles, 
        List<Ride> newBookings,
        Map<String, List<Ride>> rides,
        Map<String, Queue<VehicleStop>> stops,
        Map<String, Queue<CandidateRoute>> routes) {
        passengers = newBookings;

        allRides = rides;
        currentStops = stops;
        currentRoutes = routes;

        // TODO: Still manually setting vehicle capacities
        if (!init) registeredShuttles.values().forEach(shuttle -> 
            shuttle.setVehicleCapacity(VEHICLE_CAPACITY)
        );

        // Reset lists of vehicles for reassignment
        vehicleEnroute = new LinkedList<>();
        vehicleIdle = new LinkedList<>();

        // Create lists of idle vehicles and vehicles en route
        registeredShuttles.values().forEach(shuttle -> {
            if (shuttle.hasEnoughCapacity() && !shuttle.getCurrentRides().isEmpty()) vehicleEnroute.add(shuttle);

            else if (shuttle.getCurrentRides().isEmpty()) 
                vehicleIdle.add(shuttle);
        });

        passengers.forEach(passenger -> {
            candidateShuttle = null;
            VehicleStop pickup = passenger.getPickupLocation();
            VehicleStop dropoff = passenger.getDropoffLocation();
            
            vehicleEnroute.forEach(shuttle -> {
                // Shuttles with enough capacity which are en-route only have
                // max. 1 passenger
                Ride currentRide = shuttle.getCurrentRides().get(0);
                CartesianPoint shuttleOrigin = getCartesianPoint(currentRide.getPickupLocation());
                CartesianPoint shuttleDestination = getCartesianPoint(currentRide.getDropoffLocation());

                // Determine the shuttle position on the road
                IRoadPosition shuttlePositionOnRoad = getClosestRoadPosition(shuttle.getCurrentPosition());

                // If passenger is already picked up, set origin of current ride
                // to the current shuttle position
                if (currentRide.getStatus() == Ride.Status.PICKED_UP) shuttleOrigin = centerOf(shuttlePositionOnRoad.getConnection()).toCartesian();

                // Set road positions for upcoming stops
                addPositionOnRoad(pickup, shuttlePositionOnRoad);
                addPositionOnRoad(dropoff, shuttlePositionOnRoad);
                CartesianPoint passengerOrigin = getCartesianPoint(pickup);
                CartesianPoint passengerDestination = getCartesianPoint(dropoff);

                if (isInsideEllipse(shuttleOrigin, shuttleDestination, passengerOrigin)) {
                    if (isInsideEllipse(passengerOrigin, passengerDestination, shuttleOrigin)) {
                        assignBookingToShuttleEnroute(passenger, shuttle);
                        candidateShuttle = shuttle;
                        return;
                    } else if (isInsideEllipse(shuttleOrigin, shuttleDestination, passengerDestination) && distance(passengerDestination, shuttleDestination) < distance(passengerOrigin, shuttleDestination)) {
                        assignBookingToShuttleEnroute(passenger, shuttle);
                        candidateShuttle = shuttle;
                        return;
                    }
                };
            });

            // Assign idle vehicle to passenger
            if (candidateShuttle == null && !vehicleIdle.isEmpty()) {
                VehicleStatus candidateShuttleIdle = vehicleIdle.stream()
                    .min(Comparator.comparingDouble(shuttle -> getDistanceToIdleShuttle(pickup, shuttle.getCurrentPosition())))
                    .orElse(null);

                if (candidateShuttleIdle != null) {
                    assignBookingToIdleShuttle(passenger, candidateShuttleIdle);
                    vehicleIdle.removeIf(shuttle -> shuttle.getVehicleId().equals(candidateShuttleIdle.getVehicleId()));
                } else passenger.setStatus(Ride.Status.DECLINED);
            } else {
                // Remove full shuttle from list of enroute vehicles
                if (!vehicleEnroute.isEmpty() && candidateShuttle != null) vehicleEnroute.removeIf(shuttle -> shuttle.getVehicleId().equals(candidateShuttle.getVehicleId()));

                if (passenger.getAssignedVehicleId() == null) {
                    passenger.setStatus(Ride.Status.DECLINED);
                }
            }
        });
    }

    private static boolean isInsideEllipse(CartesianPoint pickup, CartesianPoint dropoff, CartesianPoint testPoint) {
        double distanceToPickup = distance(pickup, testPoint);
        double distanceToDropoff = distance(dropoff, testPoint);
        double distanceTrip = distance(pickup, dropoff);
        
        // Convert meter to kilometer
        return distanceToPickup + distanceToDropoff <= distanceTrip + (Math.sqrt(distanceTrip / 1000.0) * 1000);
    }

    private static void assignBookingToShuttleEnroute(Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();

        // Init rides, stops, and routes
        allRides.putIfAbsent(shuttleId, new LinkedList<>());
        eraseHistoricStopsAndRoutes(shuttleId);

        // Update ride status
        passenger.setAssignedVehicleId(shuttle.getVehicleId());
        passenger.setStatus(Ride.Status.ASSIGNED);

        // Update rides, stops, and routes
        allRides.get(shuttleId).add(passenger);
        stopOrder(passenger, shuttle);
        updateRoutes(shuttle);
    }

    private static void stopOrder(Ride passenger, VehicleStatus shuttle) {
        Ride currentRide = shuttle.getCurrentRides().get(0);
        CartesianPoint rideOrigin = getCartesianPoint(currentRide.getPickupLocation());
        CartesianPoint rideDestination = getCartesianPoint(currentRide.getDropoffLocation());
        CartesianPoint passengerOrigin = getCartesianPoint(passenger.getPickupLocation());
        CartesianPoint passengerDestination = getCartesianPoint(passenger.getDropoffLocation());

        List<CartesianPoint> result = new ArrayList<>();
        double minDistance = Double.MAX_VALUE;

        // Differentiation between ASSIGNED and PICKED_UP
        // First stop either shuttle's current position or a ride's origin
        List<CartesianPoint> initialPoints = currentRide.getStatus() == Ride.Status.PICKED_UP
            ? List.of(centerOf(getClosestRoadPosition(shuttle.getCurrentPosition()).getConnection()).toCartesian(), rideDestination)
            : List.of(rideOrigin, rideDestination);

        int start = currentRide.getStatus() == Ride.Status.PICKED_UP ? 1 : 0;

        // Get the order of stops with minimum total distance
        for (int i = start; i < 3; i++) {
            for (int j = i; j < 3; j++) {
                List<CartesianPoint> tmp = new ArrayList<>(initialPoints);
                tmp.add(i, passengerOrigin);
                tmp.add(j + 1, passengerDestination);

                double distance = getTotalDistance(tmp);
                if (distance < minDistance) {
                    minDistance = distance;
                    result = new ArrayList<>(tmp);
                }
            }
        }

        // Remove the shuttle position if already picked up
        if (currentRide.getStatus() == Ride.Status.PICKED_UP) result.remove(0);

        // Convert Cartesian points into stops
        convertToVehicleStops(result, currentRide, passenger, shuttle);
    }

    private static double getDistanceToIdleShuttle(VehicleStop pickup, GeoPoint shuttlePosition) {
        // Determine the shuttle position on the road
        IRoadPosition shuttlePositionOnRoad = getClosestRoadPosition(shuttlePosition);

        // Set road positions for upcoming stops
        addPositionOnRoad(pickup, shuttlePositionOnRoad);
        
        return distance(
            centerOf(pickup.getPositionOnRoad().getConnection()).toCartesian(),
            centerOf(shuttlePositionOnRoad.getConnection()).toCartesian());
    }

    private static double getTotalDistance(List<CartesianPoint> result) {
        return distance(result.get(0), result.get(1))
            + distance(result.get(1), result.get(2))
            + distance(result.get(2), result.get(3));
    }

    protected static double distance(CartesianPoint p1, CartesianPoint p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }

    private static void convertToVehicleStops(List<CartesianPoint> points, Ride currentRide, Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();
        Queue<VehicleStop> stops = currentStops.get(shuttleId);
    
        Map<CartesianPoint, VehicleStop> tmp = Map.of(
            getCartesianPoint(currentRide.getPickupLocation()), currentRide.getPickupLocation(),
            getCartesianPoint(currentRide.getDropoffLocation()), currentRide.getDropoffLocation(),
            getCartesianPoint(passenger.getPickupLocation()), passenger.getPickupLocation(),
            getCartesianPoint(passenger.getDropoffLocation()), passenger.getDropoffLocation()
        );
    
        points.forEach(point -> {
            if (tmp.containsKey(point)) stops.add(tmp.get(point));
        });
    }

    private static CartesianPoint getCartesianPoint(VehicleStop position) {
        return centerOf(position.getPositionOnRoad().getConnection()).toCartesian();
    }

    private static void assignBookingToIdleShuttle(Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();

        // Init rides, stops, and routes
        allRides.putIfAbsent(shuttleId, new LinkedList<>());
        eraseHistoricStopsAndRoutes(shuttleId);

        // Update ride status
        passenger.setAssignedVehicleId(shuttleId);
        passenger.setStatus(Ride.Status.ASSIGNED);

        // Update ride, stop, and route information
        allRides.get(shuttleId).add(passenger);
        Queue<VehicleStop> stops = currentStops.get(shuttleId);
        stops.add(passenger.getPickupLocation());
        stops.add(passenger.getDropoffLocation());
        updateRoutes(shuttle, passenger);
    }

    private static void eraseHistoricStopsAndRoutes(String shuttleId) {
        currentStops.put(shuttleId, new LinkedList<>());
        currentRoutes.put(shuttleId, new LinkedList<>());
    }

    private static void updateRoutes(VehicleStatus shuttle) {
        // Determine the shuttle position on the road
        IRoadPosition shuttlePositionOnRoad = getClosestRoadPosition(shuttle.getCurrentPosition());

        // Set road positions for upcoming stops
        Queue<VehicleStop> stops = currentStops.get(shuttle.getVehicleId());
        stops.forEach(stop -> addPositionOnRoad(stop, shuttlePositionOnRoad));

        List<VehicleStop> tmp = (LinkedList<VehicleStop>) stops;

        // Add the first route from shuttle to the first stop
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(shuttlePositionOnRoad, tmp.get(0).getPositionOnRoad()));

        // Add the routes between upcoming stops
        for (int i = 0; i < stops.size() - 1; i++) {
            CandidateRoute c = getBestRoute(tmp.get(i).getPositionOnRoad(), tmp.get(i + 1).getPositionOnRoad());
            currentRoutes.get(shuttle.getVehicleId()).add(c);
        }
    }

    private static void updateRoutes(VehicleStatus shuttle, Ride passenger) {
        // Determine the shuttle position on the road
        IRoadPosition shuttlePositionOnRoad = getClosestRoadPosition(shuttle.getCurrentPosition());

        // Set road positions for upcoming stops
        addPositionOnRoad(passenger.getPickupLocation(), shuttlePositionOnRoad);
        addPositionOnRoad(passenger.getDropoffLocation(), shuttlePositionOnRoad);
        IRoadPosition pickup = passenger.getPickupLocation().getPositionOnRoad();
        IRoadPosition dropoff = passenger.getDropoffLocation().getPositionOnRoad();

        // Update routes
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(shuttlePositionOnRoad, pickup));
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(pickup, dropoff));
    }

    private static IRoadPosition getClosestRoadPosition(GeoPoint position) {
        return SimulationKernel.SimulationKernel.getCentralNavigationComponent()
            .getRouting()
            .findClosestRoadPosition(position);
    }
}
