package com.dcaiti.mosaic.app.ridehailing.ridepooling;

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

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingServiceApp;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.simple.SimpleRideProvider;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;
import com.google.common.collect.Iterables;

public class RidePoolingServiceApp extends
    AbstractRidePoolingServiceApp<CRidePoolingServiceApp> {

    private static final int MAX_TRIES_CHANGE_STOP_POSITION = 10;
    private static final int MAX_TRIES_FIND_ROUTE = 10;
    // private long minStopTime = 20 * TIME.SECOND;
    // private long maxStopTime = 2 * TIME.MINUTE;
    // private VehicleStopMode stopMode = VehicleStopMode.PARK_ON_ROADSIDE;

    public RidePoolingServiceApp() {
        super(CRidePoolingServiceApp.class);
    }

    @Override 
    protected RideProvider createRideBookingProvider() {
        return new SimpleRideProvider(getConfiguration().rideOrders);
    }

    @Override
    protected String chooseShuttle(Ride booking) {
        return registeredShuttles.values().parallelStream()
            .filter(shuttle -> shuttle.hasEnoughCapacity())
            .map(shuttle -> shuttle.getVehicleId())
            .findFirst()
            .orElse(null);
    }

    // TODO: check if shuttle already has other rides
    @Override
    protected void calculateRouting(Ride booking, VehicleStatus shuttle) {
        GeoPoint shuttlePosition = shuttle.getCurrentPosition();
        VehicleStop pickupLocation = booking.getPickupLocation();
        VehicleStop dropoffLocation = booking.getDropoffLocation();
        IRoadPosition shuttlePositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(shuttlePosition);

        // Find the position on the road
        // TODO: what if not found
        if (!(addStop(pickupLocation, shuttlePositionOnRoad) &&
            addStop(dropoffLocation, shuttlePositionOnRoad))) return;

        IRoadPosition pickupRoadPosition = pickupLocation.getPositionOnRoad();
        IRoadPosition dropoffRoadPosition = dropoffLocation.getPositionOnRoad();

        RoutingParameters routingParameters = new RoutingParameters()
            .costFunction(RoutingCostFunction.Fastest)
            .considerTurnCosts(getConfiguration().considerTurnCosts);

        // Determine routing positions for routing
        RoutingPosition routingShuttlePosition = new RoutingPosition(centerOf(shuttlePositionOnRoad.getConnection()), null, shuttlePositionOnRoad.getConnectionId());
        RoutingPosition routingPickup = new RoutingPosition(centerOf(pickupRoadPosition.getConnection()), null, pickupRoadPosition.getConnectionId());
        RoutingPosition routingDropoff = new RoutingPosition(centerOf(dropoffRoadPosition.getConnection()), null, dropoffRoadPosition.getConnectionId());

        // Add new VehicleStops
        // TODO: changes required for insertion heuristics
        stops.get(shuttle.getVehicleId()).add(pickupLocation);
        stops.get(shuttle.getVehicleId()).add(dropoffLocation);

        // Add new routes and return
        // TODO: changes required for insertion heuristics
        routes.get(shuttle.getVehicleId()).add(
            SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findRoutes(new RoutingRequest(routingShuttlePosition, routingPickup, routingParameters)).getBestRoute()
        );
        routes.get(shuttle.getVehicleId()).add(
            SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findRoutes(new RoutingRequest(routingPickup, routingDropoff, routingParameters)).getBestRoute()
        );
    }

    public boolean addStop(VehicleStop rideStop, IRoadPosition shuttlePositionOnRoad) {
        return rideStop.getPositionOnRoad() == null && 
            setModifiedPositionOnRoad(rideStop, shuttlePositionOnRoad);
    }

    private static boolean setModifiedPositionOnRoad(VehicleStop rideStop, IRoadPosition shuttlePosition) {
        IRoadPosition stopPositionOnRoad = SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting().findClosestRoadPosition(rideStop.getGeoPoint());
        if (stopPositionOnRoad == null) return false;

        IConnection stopConnection = stopPositionOnRoad.getConnection();
        // Avoid to stop at the very end or beginning of a connection, as next
        // routing might fail since it would start near next node
        if (stopPositionOnRoad.getOffset() > stopConnection.getLength() * 0.8) stopPositionOnRoad = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.8);
        if (stopPositionOnRoad.getOffset() < stopConnection.getLength() * 0.2) stopPositionOnRoad = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.2);

        rideStop.setPositionOnRoad(findRoutableRoadPosition(stopPositionOnRoad, shuttlePosition));

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

    private static GeoPoint centerOf(IConnection connection) {
        return GeoUtils.getPointBetween(connection.getStartNode().getPosition(), connection.getEndNode().getPosition());
    }

    @Override
    protected void onVehicleRidePickup(Ride booking) {}

    @Override
    protected void onVehicleRideDropoff(Ride booking) {}
}
