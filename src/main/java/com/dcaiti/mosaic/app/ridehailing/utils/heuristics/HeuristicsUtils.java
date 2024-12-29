package com.dcaiti.mosaic.app.ridehailing.utils.heuristics;

import org.eclipse.mosaic.lib.geo.CartesianPoint;

import com.dcaiti.mosaic.app.ridehailing.utils.routing.RoutingUtils;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

public final class HeuristicsUtils {
    private HeuristicsUtils () {}

    public static CartesianPoint getCartesianPoint(VehicleStop position) {
        return RoutingUtils.centerOf(position.getPositionOnRoad().getConnection()).toCartesian();
    }
}
