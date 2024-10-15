package com.dcaiti.mosaic.app.ridehailing;

import com.dcaiti.mosaic.app.ridehailing.config.CMultiStopApp;
import com.dcaiti.mosaic.app.ridehailing.vehicle.StopEvent;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;
import org.eclipse.mosaic.fed.application.ambassador.SimulationKernel;
import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.RoadPositionFactory;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.Application;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.routing.RoutingRequest;
import org.eclipse.mosaic.lib.routing.RoutingResponse;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class MultiStopApp
        extends ConfigurableApplication<CMultiStopApp, VehicleOperatingSystem>
        implements VehicleApplication {

    private static final int MAX_TRIES_CHANGE_STOP_POSITION = 10;
    private static final int MAX_TRIES_FIND_ROUTE = 10;

    private long minStopTime = 20 * TIME.SECOND;
    private long maxStopTime = 2 * TIME.MINUTE;
    private VehicleStopMode stopMode = VehicleStopMode.PARK_ON_ROADSIDE;

    private final List<VehicleStop> upcomingStops = new ArrayList<>();

    private VehicleStop currentPlannedStop = null;
    private boolean waitingForResume = false;
    private boolean initialStep = true;
    private boolean driveToFirstStop = true;
    private String expectedRoute = null;
    private long lastStoppedAt;
    private int routeCalculationTries = 0;

    public MultiStopApp() {
        super(CMultiStopApp.class);
    }

    @Override
    public void onStartup() {
        minStopTime = getConfiguration().stopTime;
        maxStopTime = getConfiguration().maxStopTime;
        stopMode = getConfiguration().stopMode;
    }


    /**
     * Adds a stop to the list of stops of this vehicle.
     *
     * @return <code>true</code>, if the given stop could be added (i.e. it has a valid position within the map)
     */
    public boolean addStop(VehicleStop rideStop) {
        if (rideStop.getPositionOnRoad() == null) {
            IRoadPosition stopPosition = getOs().getNavigationModule().getClosestRoadPosition(rideStop.getGeoPoint());
            if (stopPosition == null) {
                return false;
            }
            IConnection stopConnection = stopPosition.getConnection();
            // Avoid to stop at the very end or beginning of a connection, as next routing might fail since it would start near next node
            if (stopPosition.getOffset() > stopConnection.getLength() * 0.8) {
                stopPosition = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.8);
            }
            if (stopPosition.getOffset() < stopConnection.getLength() * 0.2) {
                stopPosition = RoadPositionFactory.createFromSumoEdge(stopConnection.getId(), 0, stopConnection.getLength() * 0.2);
            }
            rideStop.setPositionOnRoad(findRoutableRoadPosition(stopPosition));
        }
        upcomingStops.add(rideStop);
        return true;
    }

    /**
     * If a stop position is located on a road, which is not routable (e.g. dead-end), the {@link IRoadPosition}
     * representing the stop position should not be used, otherwise the vehicle may get stuck. This method, therefore,
     * searches for a {@link IRoadPosition} which is connected to the given <code>requestRoadPosition</code>, but still routable.
     * To do so, it checks if a route can be generated with the <code>requestRoadPosition</code> as target. If not, it means, that no
     * connection leads into the given <code>requestRoadPosition</code>, and, therefore, an outgoing connection will be chosen and the test
     * is repeated until a routable target connection is found (maximum 10 tries). The same principle is done using
     * <code>requestRoadPosition</code> as source.
     */
    private IRoadPosition findRoutableRoadPosition(IRoadPosition requestRoadPosition) {
        IRoadPosition target = requestRoadPosition;
        int maxTries = MAX_TRIES_CHANGE_STOP_POSITION;
        while (maxTries > 0 && isNotReachable(getOs().getRoadPosition(), target)) {
            IConnection outgoing = Iterables.getFirst(target.getConnection().getOutgoingConnections(), null);
            if (outgoing != null) {
                target = RoadPositionFactory.createFromSumoEdge(outgoing.getId(), 0, outgoing.getLength() / 2d);
            }
            maxTries--;
        }
        maxTries = MAX_TRIES_CHANGE_STOP_POSITION;
        while (maxTries > 0 && isNotReachable(target, getOs().getRoadPosition())) {
            IConnection incoming = Iterables.getFirst(target.getConnection().getIncomingConnections(), null);
            if (incoming != null) {
                target = RoadPositionFactory.createFromSumoEdge(incoming.getId(), 0, incoming.getLength() / 2d);
            }
            maxTries--;
        }
        return target;
    }

    private boolean isNotReachable(IRoadPosition from, IRoadPosition to) {
        final RoutingPosition routingStart = new RoutingPosition(centerOf(from.getConnection()), null, from.getConnection().getId());
        final RoutingPosition routingTarget = new RoutingPosition(centerOf(to.getConnection()), null, to.getConnection().getId());
        return SimulationKernel.SimulationKernel.getCentralNavigationComponent().getRouting()
                .findRoutes(new RoutingRequest(routingStart, routingTarget)).getBestRoute() == null;
    }

    private static GeoPoint centerOf(IConnection connection) {
        return GeoUtils.getPointBetween(connection.getStartNode().getPosition(), connection.getEndNode().getPosition());
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        if ((previousVehicleData != null && !previousVehicleData.isStopped()) && updatedVehicleData.isStopped()) {
            lastStoppedAt = getOs().getSimulationTime();
        }

        if (waitingForResume) {
            return;
        }

        if (expectedRoute != null && !expectedRoute.equals(updatedVehicleData.getRouteId())) {
            currentPlannedStop = null;
            driveToFirstStop = true;
        }

        if (initialStep) {
            VehicleRoute currentRoute = getOs().getNavigationModule().getCurrentRoute();
            IRoadPosition currentRoadPosition = getOs().getNavigationModule().getRoadPosition();
            IRoadPosition initialStopPosition = RoadPositionFactory.createAlongRoute(currentRoadPosition, currentRoute, 0, 100);
            addStop(new VehicleStop(initialStopPosition, VehicleStop.StopReason.WAITING));
            initialStep = false;
        }

        if (driveToFirstStop && !upcomingStops.isEmpty()) {
            driveToNextStop();
            driveToFirstStop = false;
        }

        if (hasReachedStop(currentPlannedStop)) {
            if (upcomingStops.remove(currentPlannedStop)) {
                notifyOtherApps(currentPlannedStop);
            }
            currentPlannedStop = null;
            waitingForResume = true;

            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
        }
    }

    public void cancelStop(VehicleStop stopToCancel) {
        upcomingStops.remove(stopToCancel);
        if (currentPlannedStop == stopToCancel) {
            getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, 0);
            currentPlannedStop = null;
            driveToNextStop();
        }
    }

    private void notifyOtherApps(VehicleStop rideStop) {
        for (Application app : getOs().getApplications()) {
            getOs().getEventManager().addEvent(new StopEvent(
                    getOs().getSimulationTime(),
                    getOs().getId(),
                    rideStop,
                    app
            ));
        }
    }

    private void driveToNextStop() {
        if (upcomingStops.isEmpty()) {
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
            return;
        }
        routeCalculationTries++;

        final VehicleStop nextTarget = Iterables.getFirst(upcomingStops, null);

        final RoutingPosition routingTarget = new RoutingPosition(
                centerOf(nextTarget.getPositionOnRoad().getConnection()), null,
                nextTarget.getPositionOnRoad().getConnection().getId()
        );
        final RoutingParameters routingParameters = new RoutingParameters()
                .costFunction(RoutingCostFunction.Fastest)
                .considerTurnCosts(getConfiguration().considerTurnCosts);

        final RoutingResponse routingResponse = getOs().getNavigationModule().calculateRoutes(routingTarget, routingParameters);
        final CandidateRoute bestRoute = routingResponse.getBestRoute();
        if (bestRoute != null) {
            routeCalculationTries = 0;
            if (!isRoadPositionOnRoute(nextTarget.getPositionOnRoad(), bestRoute.getConnectionIds())
                    || isBehindConnectionOfTargetStop(nextTarget)) {
                nextTarget.setPositionOnRoad(RoadPositionFactory.createAtEndOfRoute(bestRoute.getConnectionIds(), 0));
            }

            if (waitingForResume) {
                getOs().resume();
                waitingForResume = false;
            }

            getOs().getNavigationModule().switchRoute(bestRoute);
            expectedRoute = getOs().getNavigationModule().getCurrentRoute().getId();

            getOs().stop(nextTarget.getPositionOnRoad(), stopMode, Long.MAX_VALUE);
            currentPlannedStop = nextTarget;
        } else {
            if (routeCalculationTries == MAX_TRIES_FIND_ROUTE) {
                getLog().error("Can not calculate route from '{}' to '{}'.",
                        getOs().getRoadPosition().getConnectionId(), nextTarget.getPositionOnRoad().getConnectionId()
                );
                if (getConfiguration().exitOnRoutingFailure) {
                    throw new IllegalStateException("Error during route calculation of vehicle " + getOs().getId());
                }
            } else {
                // sometimes routing fails if vehicle is already on junction. So try to route to next stop in a few seconds again.
                getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
            }
        }

    }

    private boolean isBehindConnectionOfTargetStop(VehicleStop nextTarget) {
        return nextTarget.getPositionOnRoad().getConnectionId().equals(getOs().getRoadPosition().getConnectionId())
                && getOs().getRoadPosition().getOffset() > nextTarget.getPositionOnRoad().getOffset();
    }

    private boolean isRoadPositionOnRoute(IRoadPosition positionOnRoad, List<String> connectionIdList) {
        for (String connection : connectionIdList) {
            if (connection.equals(positionOnRoad.getConnectionId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasReachedStop(VehicleStop stopPosition) {
        if (stopPosition == null) {
            return false;
        }
        return getOs().getVehicleData().isStopped()
                && hasReachedStopPosition(stopPosition)
                && isWaitingTimeReached(stopPosition);
    }

    private boolean isWaitingTimeReached(VehicleStop stopPosition) {
        if (getConfiguration().waitUntilDropOffTime && stopPosition.getStopReason() == VehicleStop.StopReason.DROP_OFF
                || getConfiguration().waitUntilPickUpTime && stopPosition.getStopReason() == VehicleStop.StopReason.PICK_UP) {
            return Math.min(stopPosition.getWaitUntil(), lastStoppedAt + maxStopTime) <= getOs().getSimulationTime();
        }
        return true;
    }

    private boolean hasReachedStopPosition(VehicleStop stopPosition) {
        return stopPosition.getPositionOnRoad().getConnectionId().equals(getOs().getRoadPosition().getConnectionId());
    }

    @Override
    public void onShutdown() {

    }

    @Override
    public void processEvent(Event event) {
        //nop
    }
}
