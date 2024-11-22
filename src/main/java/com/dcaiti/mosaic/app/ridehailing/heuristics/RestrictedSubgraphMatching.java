package com.dcaiti.mosaic.app.ridehailing.heuristics;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

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

        if(!init) {
            registeredShuttles.values().forEach(shuttle -> 
                shuttle.setVehicleCapacity(VEHICLE_CAPACITY));
        }

        // Reset lists of vehicles for reassignment
        vehicleEnroute = new LinkedList<>();
        vehicleIdle = new LinkedList<>();

        // Create lists of idle vehicles and vehicles en route
        registeredShuttles.values().forEach(shuttle -> {
            if (shuttle.hasEnoughCapacity() 
                && shuttle.getCurrentRides().size() > 0) 
                vehicleEnroute.add(shuttle);

            else if (shuttle.getCurrentRides().size() == 0) 
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

                IRoadPosition shuttlePosition = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttle.getCurrentPosition());
                // Set IRoadPosition for pickup location
                addPositionOnRoad(pickup, shuttlePosition);
                // set IRoadPosition for dropoff location
                addPositionOnRoad(dropoff, shuttlePosition);
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

            if (candidateShuttle == null && vehicleIdle.size() > 0) {
                VehicleStatus candidateShuttleIdle = vehicleIdle.stream()
                    .min(Comparator.comparingDouble(shuttle -> getDistanceToIdleShuttle(pickup, shuttle.getCurrentPosition())))
                    .orElse(null);

                assignBookingToIdleShuttle(passenger, candidateShuttleIdle);
                vehicleIdle.removeIf(shuttle -> shuttle.getVehicleId().equals(candidateShuttleIdle.getVehicleId()));
            } else {
                if (!vehicleEnroute.isEmpty() && candidateShuttle != null) vehicleEnroute.removeIf(shuttle -> shuttle.getVehicleId().equals(candidateShuttle.getVehicleId()));
            }
        });
    }

    private static boolean isInsideEllipse(CartesianPoint pickup, CartesianPoint dropoff, CartesianPoint testPoint) {
        double distanceToPickup = distance(pickup, testPoint);
        double distanceToDropoff = distance(dropoff, testPoint);
        double distanceTrip = distance(pickup, dropoff);
        
        return distanceToPickup + distanceToDropoff <= distanceTrip + 2 * Math.sqrt(distanceTrip);
    }

    private static void assignBookingToShuttleEnroute(Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();

        allRides.putIfAbsent(shuttleId, new LinkedList<>());
        // Erase historic stops and routes
        eraseHistoricStopsAndRoutes(shuttleId);

        passenger.setAssignedVehicleId(shuttle.getVehicleId());
        passenger.setStatus(Ride.Status.ASSIGNED);

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

        List<CartesianPoint> result = new LinkedList<>();
        List<CartesianPoint> tmp = new LinkedList<>();
        double minDistance = Double.MAX_VALUE;

        if (currentRide.getStatus() == Ride.Status.ASSIGNED) {
            for (int i = 0; i < 3; i++) {
                for (int j = i; j < 3; j++) {
                    tmp.add(rideOrigin);
                    tmp.add(rideDestination);
                    tmp.add(i, passengerOrigin);
                    tmp.add(j + 1, passengerDestination);
                    double distance = getTotalDistance(tmp);
                    if (distance < minDistance) {
                        minDistance = distance;
                        result = tmp.stream().collect(Collectors.toList());
                    }
                    tmp = new LinkedList<>();
                }
            }

            convertToVehicleStops(result, currentRide, passenger, shuttle);

        } else if (currentRide.getStatus() == Ride.Status.PICKED_UP) {
            IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttle.getCurrentPosition());

            CartesianPoint shuttleCartesian = centerOf(shuttlePositionOnRoad.getConnection()).toCartesian();

            for (int i = 0; i < 2; i++) {
                for (int j = i; j < 2; j++) {
                    tmp.add(rideDestination);
                    tmp.add(i, passengerOrigin);
                    tmp.add(j + 1, passengerDestination);
                    tmp.add(0, shuttleCartesian);
                    double distance = getTotalDistance(tmp);
                    if (distance < minDistance) {
                        minDistance = distance;
                        result = tmp.stream().collect(Collectors.toList());
                    }
                    tmp = new LinkedList<>();
                }
            }

            result.remove(0);
            Queue<VehicleStop> stops = currentStops.get(shuttle.getVehicleId());
            for (CartesianPoint point : result) {
                if (point.equals(getCartesianPoint(currentRide.getDropoffLocation())))
                    stops.add(currentRide.getDropoffLocation());
                else if (point.equals(getCartesianPoint(passenger.getPickupLocation())))
                    stops.add(passenger.getPickupLocation());
                else if (point.equals(getCartesianPoint(passenger.getDropoffLocation())))
                    stops.add(passenger.getDropoffLocation());
            }
        }
    }

    private static double getDistanceToIdleShuttle(VehicleStop pickup, GeoPoint shuttlePosition) {
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttlePosition);
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

    private static double distance(CartesianPoint p1, CartesianPoint p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }

    private static void convertToVehicleStops(List<CartesianPoint> points, Ride currentRide, Ride passenger, VehicleStatus shuttle) {
        for (CartesianPoint point : points) {
            if (point.equals(getCartesianPoint(currentRide.getPickupLocation()))) {
                currentStops.get(shuttle.getVehicleId()).add(currentRide.getPickupLocation());
            } else if (point.equals(getCartesianPoint(currentRide.getDropoffLocation()))) {
                currentStops.get(shuttle.getVehicleId()).add(currentRide.getDropoffLocation());
            } else if (point.equals(getCartesianPoint(passenger.getPickupLocation()))) {
                currentStops.get(shuttle.getVehicleId()).add(passenger.getPickupLocation());
            } else if (point.equals(getCartesianPoint(passenger.getDropoffLocation()))) {
                currentStops.get(shuttle.getVehicleId()).add(passenger.getDropoffLocation());
            }
        }
    }

    private static CartesianPoint getCartesianPoint(VehicleStop position) {
        return centerOf(position.getPositionOnRoad().getConnection()).toCartesian();
    }

    private static void assignBookingToIdleShuttle(Ride passenger, VehicleStatus shuttle) {
        String shuttleId = shuttle.getVehicleId();

        allRides.putIfAbsent(shuttleId, new LinkedList<>());
        // Erase historic stops and routes
        eraseHistoricStopsAndRoutes(shuttleId);

        passenger.setAssignedVehicleId(shuttleId);
        passenger.setStatus(Ride.Status.ASSIGNED);

        allRides.get(shuttleId).add(passenger);
        currentStops.get(shuttleId).add(passenger.getPickupLocation());
        currentStops.get(shuttleId).add(passenger.getDropoffLocation());
        updateRoutes(shuttle, passenger);
    }

    private static void eraseHistoricStopsAndRoutes(String shuttleId) {
        currentStops.put(shuttleId, new LinkedList<>());
        currentRoutes.put(shuttleId, new LinkedList<>());
    }

    private static void updateRoutes(VehicleStatus shuttle) {
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttle.getCurrentPosition());

        Queue<VehicleStop> stops = currentStops.get(shuttle.getVehicleId());
        stops.forEach(stop -> addPositionOnRoad(stop, shuttlePositionOnRoad));

        List<VehicleStop> tmp = (LinkedList<VehicleStop>) stops;
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(shuttlePositionOnRoad, tmp.get(0).getPositionOnRoad()));

        for (int i = 0; i < stops.size() - 1; i++) {
            CandidateRoute c = getBestRoute(tmp.get(i).getPositionOnRoad(), tmp.get(i + 1).getPositionOnRoad());
            currentRoutes.get(shuttle.getVehicleId()).add(c);
        }
    }

    private static void updateRoutes(VehicleStatus shuttle, Ride passenger) {
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttle.getCurrentPosition());

        addPositionOnRoad(passenger.getPickupLocation(), shuttlePositionOnRoad);
        addPositionOnRoad(passenger.getDropoffLocation(), shuttlePositionOnRoad);
        IRoadPosition pickup = passenger.getPickupLocation().getPositionOnRoad();
        IRoadPosition dropoff = passenger.getDropoffLocation().getPositionOnRoad();
        
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(shuttlePositionOnRoad, pickup));
        currentRoutes.get(shuttle.getVehicleId()).add(getBestRoute(pickup, dropoff));
    }
}
