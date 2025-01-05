package com.dcaiti.mosaic.app.ridehailing.utils.heuristics;

import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.mosaic.lib.geo.CartesianPoint;

import com.dcaiti.mosaic.app.ridehailing.utils.routing.RoutingUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

public final class HeuristicsUtils {
    private HeuristicsUtils () {}

    public static CartesianPoint getCartesianPoint(VehicleStop position) {
        return RoutingUtils.centerOf(position.getPositionOnRoad().getConnection()).toCartesian();
    }

    public static double distance(CartesianPoint p1, CartesianPoint p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }

    public static double getTotalDistance(List<CartesianPoint> result) {
    return IntStream.range(0, result.size() - 1)
        .mapToDouble(i -> HeuristicsUtils.distance(result.get(i), result.get(i + 1)))
        .sum();
    }

    public static boolean hasIdenticalPickupAndDropoff(VehicleStatus shuttle, Ride booking) {
        // Add road positions for coordinates checking
        RoutingUtils.addPositionOnRoad(
            booking.getPickupLocation(), 
            RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition())
        );
        RoutingUtils.addPositionOnRoad(
            booking.getDropoffLocation(), 
            RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition())
        );

        CartesianPoint pickupLocationCartesian = getCartesianPoint(booking.getPickupLocation());
        CartesianPoint dropoffLocCartesianPoint = getCartesianPoint(booking.getDropoffLocation());

        return hasSameCartesianCoordinates(pickupLocationCartesian, dropoffLocCartesianPoint) ||    
            hasSameConnectionId(booking.getPickupLocation(), booking.getDropoffLocation());
    }

    public static boolean checkForDuplicateCoordinates(VehicleStatus shuttle, Ride booking) {
        List<Ride> currentRides = shuttle.getCurrentRides();

        // Add road positions for coordinates checking
        RoutingUtils.addPositionOnRoad(
            booking.getPickupLocation(), 
            RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition())
        );
        RoutingUtils.addPositionOnRoad(
            booking.getDropoffLocation(), 
            RoutingUtils.getClosestRoadPosition(shuttle.getCurrentPosition())
        );

        CartesianPoint candidatePickup = getCartesianPoint(booking.getPickupLocation());
        CartesianPoint candidateDropoff = getCartesianPoint(booking.getDropoffLocation());
        VehicleStop candidatePickupStop = booking.getPickupLocation();
        VehicleStop candidateDropoffStop = booking.getDropoffLocation();

        // Check for duplicate coordinates against all current rides
        for (Ride ride : currentRides) {
            CartesianPoint futurePickup = getCartesianPoint(ride.getPickupLocation());
            CartesianPoint futureDropoff = getCartesianPoint(ride.getDropoffLocation());

            if (
                (hasSameCartesianCoordinates(futurePickup, candidatePickup) ||
                    hasSameConnectionId(candidatePickupStop, ride.getPickupLocation())
                ) || (
                    hasSameCartesianCoordinates(futureDropoff, candidateDropoff) ||
                    hasSameConnectionId(candidateDropoffStop, ride.getDropoffLocation())
                ) || (
                    hasSameCartesianCoordinates(futurePickup, candidateDropoff) ||
                    hasSameConnectionId(candidatePickupStop, ride.getDropoffLocation())
                ) || (
                    hasSameCartesianCoordinates(futureDropoff, candidatePickup) ||
                    hasSameConnectionId(candidateDropoffStop, ride.getPickupLocation())
                )
            ) return true;
        }

        return false;
    }

    public static boolean hasSameCartesianCoordinates(CartesianPoint p1, CartesianPoint p2) {
        return 
            p1.getX() == p2.getX() && 
            p1.getY() == p2.getY() && 
            p1.getZ() == p2.getZ();
    }

    private static boolean hasSameConnectionId(VehicleStop s1, VehicleStop s2) {
        return s1.getPositionOnRoad().getConnectionId().equals(
            s2.getPositionOnRoad().getConnectionId()
        );
    }
}
