package com.dcaiti.mosaic.app.ridehailing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.lib.enums.VehicleStopMode;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import com.dcaiti.mosaic.app.ridehailing.config.CVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.vehicle.StopEvent;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class VehicleApp extends ConfigurableApplication<CVehicleApp, VehicleOperatingSystem> implements VehicleApplication{

    private static long minStopTime;
    private static long maxStopTime;
    private static VehicleStopMode stopMode;

    private VehicleStop currentPlannedStop = null;
    private boolean waitingForResume = false;
    private boolean initialStep = true;
    private boolean driveToFirstStop = true;
    private CandidateRoute currentRoute = null;
    private long lastStoppedAt;
    private int routeCalculationTries = 0;

    private List<Ride> rides = new ArrayList<>();
    private Queue<VehicleStop> stops = new LinkedList<>();
    private Queue<CandidateRoute> routes = new LinkedList<>();

    public VehicleApp() {
        super(CVehicleApp.class);
    }

    @Override
    public void onStartup() {
        minStopTime = getConfiguration().stopTime;
        maxStopTime = getConfiguration().maxStopTime;
        stopMode = getConfiguration().stopMode;
    }

    // Called after each simulation step
    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @NonNull VehicleData updatedVehicleData) {
        // Log timestamp when vehicle waits for the first time
        if ((previousVehicleData != null && !previousVehicleData.isStopped()) &&updatedVehicleData.isStopped()) lastStoppedAt = getOs().getSimulationTime();

        // If waiting for new requests, continue waiting
        if (waitingForResume) return;

        // Drive to point of business if idle (in theory, not implemented yet)
        String currentRouteId = getOs().getNavigationModule().getCurrentRoute().getId();
        if (currentRouteId != null && !currentRouteId.equals(updatedVehicleData.getRouteId())) {
            currentPlannedStop = null;
            driveToFirstStop = true;
        }

        // Create a stop 100m away from spawnpoint (only once)
        // if (initialStep) {
        //     VehicleRoute currentRoute = getOs().getNavigationModule().getCurrentRoute();
        //     IRoadPosition currentRoadPosition = getOs().getNavigationModule().getRoadPosition();
        //     IRoadPosition initialStopPosition = RoadPositionFactory.createAlongRoute(currentRoadPosition, currentRoute, 0, 100);
        //     addStop(new VehicleStop(initialStopPosition, VehicleStop.StopReason.WAITING));
        //     initialStep = false;
        // }

        // Vehicle on the way to first stop but new order arrives
        if (driveToFirstStop && !routes.isEmpty()) {
            driveToNextStop();
            driveToFirstStop = false;
        }



        // Vehicle reached stop
        if (hasReachedStop(currentPlannedStop)) {
            stops.poll();
            routes.poll();
            notifyOtherApps(currentPlannedStop);

            currentPlannedStop = null;
            waitingForResume = true;

            // Wait 20 seconds and resume
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
        }
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
        if (needToWait && (stopPosition.getStopReason() == VehicleStop.StopReason.DROP_OFF || stopPosition.getStopReason() == VehicleStop.StopReason.PICK_UP)) return Math.min(stopPosition.getWaitUntil(), lastStoppedAt + maxStopTime) <= getOs().getSimulationTime();

        return true;
    }

    private boolean hasReachedStopPosition(VehicleStop stopPosition) {
        return stopPosition.getPositionOnRoad().getConnectionId().equals(getOs().getRoadPosition().getConnectionId());
    }

    // TODO: handle routes when routes alreasy exist
    public void driveToNextStop() {
        if (routes.isEmpty()) {
            getOs().getEventManager().addEvent(getOs().getSimulationTime() + minStopTime, e -> driveToNextStop());
            return;
        }

        if (waitingForResume) {
            getOs().resume();
            waitingForResume = false;
        }
        
        currentPlannedStop = stops.peek();
        currentRoute = routes.peek();
        getOs().getNavigationModule().switchRoute(currentRoute);
        getOs().stop(stops.peek().getPositionOnRoad(), stopMode, Long.MAX_VALUE);
    }

    public void updateRides(List<Ride> rides) {
        this.rides = rides;
    }

    public void updateStops(Queue<VehicleStop> stops) {
        this.stops = stops;
    }

    public void updateRoutes(Queue<CandidateRoute> routes) {
        this.routes = routes;
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
