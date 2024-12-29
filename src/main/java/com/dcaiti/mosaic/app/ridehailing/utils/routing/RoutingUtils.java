package com.dcaiti.mosaic.app.ridehailing.utils.routing;

import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.RoadPositionFactory;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingRequest;

import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;
import com.google.common.collect.Iterables;

public final class RoutingUtils {
    public static final boolean TURN_COSTS = false;

    private static final int MAX_TRIES_CHANGE_STOP_POSITION = 10;

    public static CandidateRoute getBestRoute(IRoadPosition start, IRoadPosition target) {
        RoutingPosition routingStart = new RoutingPosition(centerOf(start.getConnection()), null, start.getConnectionId());
        RoutingPosition routingTarget = new RoutingPosition(centerOf(target.getConnection()), null, target.getConnectionId());

        RoutingParameters routingParameters = new RoutingParameters()
            .costFunction(RoutingCostFunction.Fastest)
            .considerTurnCosts(TURN_COSTS);

        return SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findRoutes(new RoutingRequest(
            routingStart,
            routingTarget,
            routingParameters
        )).getBestRoute();
    }

    public static boolean addPositionOnRoad(VehicleStop rideStop, IRoadPosition shuttlePositionOnRoad) {
        return rideStop.getPositionOnRoad() == null && setModifiedPositionOnRoad(rideStop, shuttlePositionOnRoad);
    }

    private static boolean setModifiedPositionOnRoad(VehicleStop stop, IRoadPosition shuttlePosition) {
        IRoadPosition stopPositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(stop.getGeoPoint());
        if (stopPositionOnRoad == null) return false;

        IConnection stopConnection = stopPositionOnRoad.getConnection();
        // Avoid to stop at the very end or beginning of a connection, as next
        // routing might fail since it would start near next node
        if (stopPositionOnRoad.getOffset() > stopConnection.getLength() * 0.8) stopPositionOnRoad = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.8);
        if (stopPositionOnRoad.getOffset() < stopConnection.getLength() * 0.2) stopPositionOnRoad = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.2);

        stop.setPositionOnRoad(findRoutableRoadPosition(stopPositionOnRoad, shuttlePosition));

        return true;
    }

    /**
     * If a stop position is located on a road, which is not routable (e.g.
     * dead-end), the {@link IRoadPosition} representing the stop position
     * should not be used, otherwise the vehicle may get stuck. This method
     * searches for a {@link IRoadPosition} which is connected to the given
     * <code>requestRoadPosition</code>, but still routable. To do so, it checks
     * if a route can be generated with the <code>requestRoadPosition</code> as
     * target. If not, if means that no connection leads into the given
     * <code>requestRoadPosition</code>, and therefore, an outgoing connection
     * will be chosen and the test is repeated until a routable target
     * conneciton is found (maximum 10 tries). The same principle is done using
     * <code>requestRoadPosition</code> as source.
     */
    private static IRoadPosition findRoutableRoadPosition(IRoadPosition requestRoadPosition, IRoadPosition shuttlePosition) {
        IRoadPosition target = requestRoadPosition;
        int maxTries = MAX_TRIES_CHANGE_STOP_POSITION;

        while (maxTries > 0 && findBestRoute(shuttlePosition, target) == null) {
            IConnection outgoing = Iterables.getFirst(target.getConnection().getOutgoingConnections(), null);
            if (outgoing != null) target = RoadPositionFactory.createFromSumoEdge(outgoing.getId(), 0, outgoing.getLength() / 2d);

            maxTries--;
        }

        maxTries =  MAX_TRIES_CHANGE_STOP_POSITION;
        while (maxTries > 0 && findBestRoute(target, shuttlePosition) == null) {
            IConnection incoming = Iterables.getFirst(target.getConnection().getIncomingConnections(), null);
            if (incoming != null) target = RoadPositionFactory.createFromSumoEdge(incoming.getId(), 0, incoming.getLength() / 2d);

            maxTries--;
        }

        return target;
    }

    private static CandidateRoute findBestRoute(IRoadPosition from, IRoadPosition to) {
        final RoutingPosition pickup = new RoutingPosition(centerOf(from.getConnection()), null, from.getConnection().getId());
        final RoutingPosition dropoff = new RoutingPosition(centerOf(to.getConnection()), null, to.getConnection().getId());

        return SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findRoutes(new RoutingRequest(pickup, dropoff)).getBestRoute();
    }

    public static GeoPoint centerOf(IConnection connection) {
        return GeoUtils.getPointBetween(connection.getStartNode().getPosition(), connection.getEndNode().getPosition());
    }
}
