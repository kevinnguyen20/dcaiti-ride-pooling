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
        if (previousVehicleData != null && !previousVehicleData.isStopped() && updatedVehicleData.isStopped()) lastStoppedAt = getOs().getSimulationTime();

        // If waiting for new requests, continue waiting
        // if (waitingForResume) return;

        // Return to point-of-business if idle
        if (waitingForResume && currentStops.isEmpty() && !waitingAtPointOfBusiness) {
            currentPlannedStop = pointOfBusiness;
            currentStops.add(pointOfBusiness);
            driveToFirstStop = true;
            driveToPointOfBusiness();
        }

        // Drive to point of business if idle (in theory, not implemented yet)
        // String currentRouteId = getOs().getNavigationModule().getCurrentRoute().getId();
        // if (currentRouteId != null && !currentRouteId.equals(updatedVehicleData.getRouteId())) {
        //     currentPlannedStop = pointOfBusiness;
        //     driveToFirstStop = true;
        // }

        // Handle arrival at first stop if new orders are present
        if (driveToFirstStop && !currentStops.isEmpty()) {
            driveToNextStop();
            driveToFirstStop = false;
        }

        // Vehicle reached stop
        if (hasReachedStop(currentPlannedStop)) {
            currentStops.poll();
            currentRoutes.poll();
            notifyOtherApps(currentPlannedStop);

            waitingAtPointOfBusiness = currentPlannedStop.getGeoPoint().equals(pointOfBusiness.getGeoPoint());
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
        currentRoutes.add(
            getNewCurrentRoute(pointOfBusiness.getPositionOnRoad().getConnection())
        );
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
        return stopPosition != null 
            && getOs().getVehicleData().isStopped()
            && hasReachedStopPosition(stopPosition)
            && isWaitingTimeReached(stopPosition);
    }

    private boolean isWaitingTimeReached(VehicleStop stopPosition) {
        if (!getConfiguration().waitUntilDropOffTime) return true;
    
        boolean isDropOffOrPickUp = stopPosition.getStopReason() == VehicleStop.StopReason.DROP_OFF 
            || stopPosition.getStopReason() == VehicleStop.StopReason.PICK_UP;
    
        // Stop reasons different from DROPPED_OFF AND PICK_UP always true
        return !isDropOffOrPickUp || Math.min(stopPosition.getWaitUntil(), lastStoppedAt + maxStopTime) <= getOs().getSimulationTime();
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

        getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, 0);
        getOs().getNavigationModule().switchRoute(currentRoute);
        getOs().stop(currentStops.peek().getPositionOnRoad(), stopMode, Long.MAX_VALUE);
    }

    // Important: update routes first, then stops
    public void updateRoutes(Queue<CandidateRoute> routes) {
        if (routes.isEmpty()) return;

        currentRoutes = routes;
        CandidateRoute route = currentRoutes.peek();
        if (route != null) {
            IRoadPosition shuttlePositionOnRoad = getOs().getVehicleData().getRoadPosition();
            String currentConnectionId = shuttlePositionOnRoad.getConnectionId();

            if (route.getConnectionIds().stream().noneMatch(connectionId -> connectionId.equals(currentConnectionId))) {
                String targetConnectionId = route.getConnectionIds().get(route.getConnectionIds().size() - 1);
                IConnection targetConnection = getOs().getNavigationModule().getConnection(targetConnectionId);

                // Calculate new route and remove the old deprecated route
                currentRoute = getNewCurrentRoute(targetConnection);
                removeDeprecatedRoute();

                // Revoke the current stop and switch to new route
                if (currentPlannedStop != null) getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, 0);
                getOs().getNavigationModule().switchRoute(currentRoute);
            } else getOs().getNavigationModule().switchRoute(route);
        }
    }

    // Important: update routes first, then stops
    public void updateStops(Queue<VehicleStop> currentStops) {
        if (currentStops.isEmpty()) return;

        this.currentStops = currentStops;
        if (currentPlannedStop == null) return;

        currentPlannedStop = currentStops.peek();
        getOs().stop(currentPlannedStop.getPositionOnRoad(), stopMode, Long.MAX_VALUE);
    }

    private void removeDeprecatedRoute() {
        Queue<CandidateRoute> tmp = new LinkedList<>();
        tmp.add(currentRoute);
        currentRoutes.poll();
        currentRoutes.forEach(r -> tmp.add(r));
        currentRoutes = tmp;
    }

    private CandidateRoute getNewCurrentRoute(IConnection connection) {
        RoutingPosition target = new RoutingPosition(centerOf(connection), null, connection.getId());
        RoutingParameters routingParameters = new RoutingParameters()
            .costFunction(RoutingCostFunction.Fastest)
            .considerTurnCosts(considerTurnCosts);
        return getOs().getNavigationModule().calculateRoutes(target, routingParameters).getBestRoute();
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
