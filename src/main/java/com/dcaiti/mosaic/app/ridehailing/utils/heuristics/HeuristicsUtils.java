package com.dcaiti.mosaic.app.ridehailing.utils.heuristics;

import java.util.List;

import org.eclipse.mosaic.lib.geo.CartesianPoint;

import com.dcaiti.mosaic.app.ridehailing.utils.routing.RoutingUtils;
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
        double totalDistance = 0.0;
        for (int i = 0; i < result.size() - 1; i++) {
            totalDistance += HeuristicsUtils.distance(result.get(i), result.get(i + 1));
        }
        return totalDistance;
    }
}
