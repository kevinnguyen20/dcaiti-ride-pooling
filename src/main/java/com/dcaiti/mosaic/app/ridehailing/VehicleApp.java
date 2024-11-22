package com.dcaiti.mosaic.app.ridehailing;

import java.util.LinkedList;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.ambassador.simulation.navigation.RoadPositionFactory;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.geo.GeoUtils;
import org.eclipse.mosaic.lib.objects.road.IConnection;
import org.eclipse.mosaic.lib.objects.road.IRoadPosition;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.routing.RoutingCostFunction;
import org.eclipse.mosaic.lib.routing.RoutingParameters;
import org.eclipse.mosaic.lib.routing.RoutingPosition;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import com.dcaiti.mosaic.app.ridehailing.config.CVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.vehicle.StopEvent;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class VehicleApp extends ConfigurableApplication<CVehicleApp, VehicleOperatingSystem> implements VehicleApplication {

    private static long minStopTime;
    private static long maxStopTime;
    private static VehicleStopMode stopMode;
    private static boolean considerTurnCosts;

    private VehicleStop currentPlannedStop = null;
    private boolean waitingForResume = false;
    private boolean waitingAtPointOfBusiness = false;
    private boolean initialStep = true;
    private VehicleStop pointOfBusiness = null;
    private boolean driveToFirstStop = true;
    private CandidateRoute currentRoute = null;
    private long lastStoppedAt;

    private Queue<VehicleStop> currentStops;
    private Queue<CandidateRoute> currentRoutes;

    public VehicleApp() {
        super(CVehicleApp.class);
    }

    @Override
    public void onStartup() {
        minStopTime = getConfiguration().stopTime;
        maxStopTime = getConfiguration().maxStopTime;
        stopMode = getConfiguration().stopMode;
        considerTurnCosts = getConfiguration().considerTurnCosts;

        // Always init to add point-of-business as first stop
        currentStops = new LinkedList<>();
        currentRoutes = new LinkedList<>();
    }

    // Called after each simulation step
    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @NonNull VehicleData updatedVehicleData) {
        // Create a point-of-business 100m away from spawnpoint
        if (initialStep) {
            createPointOfBusiness();
            driveToPointOfBusiness();
            initialStep = false;
        }

        // Log timestamp when vehicle waits for the first time
        if ((previousVehicleData != null && !previousVehicleData.isStopped()) && updatedVehicleData.isStopped()) lastStoppedAt = getOs().getSimulationTime();

        // If waiting for new requests, continue waiting
        // if (waitingForResume) return;

        // Return to point-of-business if the list of stops is empty
        if (waitingForResume && currentStops.isEmpty()) {
            if (waitingAtPointOfBusiness) return;
            currentPlannedStop = pointOfBusiness;
            currentStops.add(pointOfBusiness);
            driveToPointOfBusiness();
        }

        // Drive to point of business if idle (in theory, not implemented yet)
        // String currentRouteId = getOs().getNavigationModule().getCurrentRoute().getId();
        // if (currentRouteId != null && !currentRouteId.equals(updatedVehicleData.getRouteId())) {
        //     currentPlannedStop = pointOfBusiness;
        //     driveToFirstStop = true;
        // }

        // Vehicle on the way to first stop but new order arrives
        if (driveToFirstStop && !currentStops.isEmpty()) {
            driveToNextStop();
            driveToFirstStop = false;
        }

        // Vehicle reached stop
        if (hasReachedStop(currentPlannedStop)) {
            currentStops.poll();
            currentRoutes.poll();
            notifyOtherApps(currentPlannedStop);

            if (currentPlannedStop.getGeoPoint() == pointOfBusiness.getGeoPoint()) waitingAtPointOfBusiness = true;

            currentPlannedStop = null;
            waitingForResume = true;

            // Wait 20 seconds and resume
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
        }
    }

    private void createPointOfBusiness() {
        IRoadPosition initialStopPosition = RoadPositionFactory.createAlongRoute(
            getOs().getNavigationModule().getRoadPosition(), 
            getOs().getNavigationModule().getCurrentRoute(), 
            0, 
            100
        );
        pointOfBusiness = new VehicleStop(initialStopPosition, VehicleStop.StopReason.WAITING);
        currentPlannedStop = pointOfBusiness;
        currentStops.add(pointOfBusiness);
    }

    private void driveToPointOfBusiness() {
        final RoutingPosition target = new RoutingPosition(centerOf(pointOfBusiness.getPositionOnRoad().getConnection()), null, pointOfBusiness.getPositionOnRoad().getConnectionId());

        final RoutingParameters routingParameters = new RoutingParameters()
            .costFunction(RoutingCostFunction.Fastest)
            .considerTurnCosts(considerTurnCosts);

        final CandidateRoute bestRoute = getOs().getNavigationModule().calculateRoutes(target, routingParameters).getBestRoute();
        currentRoutes.add(bestRoute);
    }

    private static GeoPoint centerOf(IConnection connection) {
        return GeoUtils.getPointBetween(connection.getStartNode().getPosition(), connection.getEndNode().getPosition());
    }

    private void notifyOtherApps(VehicleStop rideStop) {
        getOs().getApplications().forEach(app -> {
            getOs().getEventManager().addEvent(new StopEvent(
                getOs().getSimulationTime(), 
                getOs().getId(),
                rideStop,
                app));
        });
    }

    private boolean hasReachedStop(VehicleStop stopPosition) {
        if (stopPosition == null) return false;

        return getOs().getVehicleData().isStopped()
            && hasReachedStopPosition(stopPosition)
            && isWaitingTimeReached(stopPosition);
    }

    private boolean isWaitingTimeReached(VehicleStop stopPosition) {
        boolean needToWait = getConfiguration().waitUntilDropOffTime;

        if (needToWait && 
            (stopPosition.getStopReason() == VehicleStop.StopReason.DROP_OFF || 
            stopPosition.getStopReason() == VehicleStop.StopReason.PICK_UP)) return Math.min(stopPosition.getWaitUntil(), lastStoppedAt + maxStopTime) <= getOs().getSimulationTime();

        return true;
    }

    private boolean hasReachedStopPosition(VehicleStop stopPosition) {
        return stopPosition.getPositionOnRoad().getConnectionId().equals(getOs().getRoadPosition().getConnectionId());
    }

    public void driveToNextStop() {
        if (currentStops.isEmpty()) {
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
            return;
        }

        if (waitingForResume) {
            getOs().resume();
            waitingAtPointOfBusiness = false;
            waitingForResume = false;
        }
        
        currentPlannedStop = currentStops.peek();
        currentRoute = currentRoutes.peek();
        getOs().getNavigationModule().switchRoute(currentRoute);
        getOs().stop(currentStops.peek().getPositionOnRoad(), stopMode, Long.MAX_VALUE);
    }

    // TODO: check new stops and routes, temporal differences
    public void updateStops(Queue<VehicleStop> currentStops) {
        this.currentStops = currentStops;
        if (currentPlannedStop != null) {
            getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, 0);
            currentPlannedStop = currentStops.peek();
            getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, Long.MAX_VALUE);
        }
    }

    public void updateRoutes(Queue<CandidateRoute> currentRoutes) {
        this.currentRoutes = currentRoutes;
        if (currentRoutes.peek() != null) getOs().getNavigationModule().switchRoute(currentRoutes.peek());
    }

    @Override
    public void onShutdown() {}

    @Override
    public void processEvent(Event event) {}

    // TODO
    // public void cancelStop(VehicleStop stopToCancel) {
    //     upcomingStops.remove(stopToCancel);
    //     if (currentPlannedStop == stopToCancel) {
    //         getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, 0);
    //         currentPlannedStop = null;
    //         // driveToNextStop();
    //     }
    // }
}
