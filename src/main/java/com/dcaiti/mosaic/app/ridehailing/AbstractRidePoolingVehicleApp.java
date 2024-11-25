package com.dcaiti.mosaic.app.ridehailing;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

import com.dcaiti.mosaic.app.ridehailing.config.CAbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.messages.RideBookingMessage;
import com.dcaiti.mosaic.app.ridehailing.messages.VehicleStatusMessage;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.vehicle.StopEvent;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import edu.umd.cs.findbugs.annotations.NonNull;

public abstract class AbstractRidePoolingVehicleApp<ConfigT extends CAbstractRidePoolingVehicleApp> extends ConfigurableApplication<ConfigT, VehicleOperatingSystem> implements CommunicationApplication {

    private static final long UPDATE_INTERVAL = 5 * TIME.SECOND;
    // TODO: heuristic determine this value or set this value upon spawn
    private static final int VEHICLE_CAPACITY = 2;

    private List<Ride> allRides = new LinkedList<>();
    private Queue<VehicleStop> currentStops = new LinkedList<>();
    private Queue<CandidateRoute> currentRoutes = new LinkedList<>();
    protected List<Ride> currentRides = new LinkedList<>();
    private List<Ride> declinedRides = new LinkedList<>();
    protected List<Ride> finishedRides = new LinkedList<>();

    public AbstractRidePoolingVehicleApp(Class<ConfigT> configClass) {
        super(configClass);
    }

    @Override
    public void onStartup() {
        getOs().getCellModule().enable();
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> sendUpdate());
    }

    protected abstract void onAcceptRide(Ride ride);
    protected abstract void onPickup(Ride ride);
    protected abstract void onDropOff(Ride ride);

    private void sendUpdate() {
        MessageRouting messageRouting = getOs().getCellModule().createMessageRouting().topoCast(getConfiguration().serverName);
        VehicleStatusMessage shuttleStatusMsg = new VehicleStatusMessage(
            messageRouting, 
            createVehicleStatus(),
            declinedRides,
            finishedRides
        );
        declinedRides = new LinkedList<>();
        finishedRides = new LinkedList<>();

        getOs().getCellModule().sendV2xMessage(shuttleStatusMsg);

        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> sendUpdate());
    }

    protected VehicleStatus createVehicleStatus() {
        return new VehicleStatus(
            getOs().getId(),
            getOs().getNavigationModule().getCurrentPosition(),
            currentRides,
            getOs().getVehicleData().getDistanceDriven()
        );
    }

    @Override
    public void processEvent(Event event) {
        if (event instanceof StopEvent rideStop && !allRides.isEmpty()) {
            int rideId = rideStop.getRideStop().getRideId();
            VehicleStop.StopReason stopReason = rideStop.getRideStop().getStopReason();

            allRides.parallelStream()
                .filter(ride -> ride.getBookingId() == rideId)
                .forEach(ride -> {
                    if (stopReason == VehicleStop.StopReason.PICK_UP) pickup(ride);
                    else if (stopReason == VehicleStop.StopReason.DROP_OFF) dropOff(ride);
                    }
                );
        }
    }

    protected void pickup(Ride ride) {
        ride.setStatus(Ride.Status.PICKED_UP);
        // } else {
        //     getLog().error("The stop has been declined (invalid dropoff location).");
        //     ride.setStatus(Ride.Status.DECLINED);
        // }
        onPickup(ride);
    }

    protected void dropOff(Ride ride) {
        ride.setStatus(Ride.Status.DROPPED_OFF);
        onDropOff(ride);
    }

    protected final @NonNull VehicleApp getVehicleApp() {
        return (VehicleApp) getOs().getApplications().parallelStream()
            .filter(app -> app instanceof VehicleApp)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("This app requires MultiStopApp to be mapped"));
    }

    // Receive message upon new accepted ride
    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage.getMessage() instanceof RideBookingMessage rideBookingMessage) {
            // Ride booking message targeted the wrong shuttle
            if (!rideBookingMessage.getTargetVehicle().equals(getOs().getId())) {
                getLog().warn("Ignoring ride booking for another vehicle.");
                return;
            }

            // Update information
            allRides = rideBookingMessage.getAllRides();
            currentStops = rideBookingMessage.getCurrentStops();
            currentRoutes = rideBookingMessage.getCurrentRoutes();
            declinedRides = new LinkedList<>();
            finishedRides = new LinkedList<>();

            allRides.parallelStream().filter(ride -> ride.getAssignedVehicleId().equals(getOs().getId())).forEach(ride -> {
                switch (ride.getStatus()) {
                    case DROPPED_OFF, REJECTED -> {}
                    case ASSIGNED -> {
                        // Decline rides if capacity is reached
                        if (currentRides.size() >= VEHICLE_CAPACITY) {
                            getLog().warn("Shuttle's capacity reached.");
                            ride.setStatus(Ride.Status.DECLINED);
                            declinedRides.add(ride);
                            System.err.println();
                            System.err.println("Ride declined.");
                        } else {
                            // Add accepted rides to currentRides
                            onAcceptRide(ride);
                            System.err.println();
                            System.err.println("Accepted ride: "+ride.getBookingId());
                            System.err.println();
                        }
                    }
                    default -> throw new IllegalArgumentException("Unexpected value: " + ride.getStatus());
                }
            });
            if (declinedRides.isEmpty()) {
                VehicleApp vehicleApp = getVehicleApp();
                // IMPORTANT: UPDATE ROUTES FIRST, THEN STOPS
                vehicleApp.updateRoutes(currentRoutes);
                vehicleApp.updateStops(currentStops);
            }
        }
    }

    @Override
    public void onShutdown() {}

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {}

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {}

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {}
}
