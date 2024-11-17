package com.dcaiti.mosaic.app.ridehailing;

import java.util.ArrayList;
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
    private static final int VEHICLE_CAPACITY = 4;

    private List<Ride> rides = new LinkedList<>();
    private Queue<VehicleStop> stops = new LinkedList<>();
    private Queue<CandidateRoute> routes = new LinkedList<>();

    private List<Ride> currentRides = new LinkedList<>();

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
        VehicleStatusMessage shuttleStatusMsg = new VehicleStatusMessage(messageRouting, createVehicleStatus());
        getOs().getCellModule().sendV2xMessage(shuttleStatusMsg);

        // rides.removeIf(ride -> ride.getStatus() == Ride.Status.DROPPED_OFF);

        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> sendUpdate());
    }

    protected VehicleStatus createVehicleStatus() {
        return new VehicleStatus(
            getOs().getId(),
            getOs().getNavigationModule().getCurrentPosition(),
            currentRides,
            rides, stops, routes,
            getOs().getVehicleData().getDistanceDriven()
        );
    }

    @Override
    public void processEvent(Event event) {
        if (event instanceof StopEvent rideStop && !rides.isEmpty()) {
            int rideId = rideStop.getRideStop().getRideId();
            VehicleStop.StopReason stopReason = rideStop.getRideStop().getStopReason();

            rides.parallelStream()
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
        currentRides.add(ride);
        // } else {
        //     getLog().error("The stop has been declined (invalid dropoff location).");
        //     ride.setStatus(Ride.Status.DECLINED);
        // }
        onPickup(ride);
    }

    protected void dropOff(Ride ride) {
        ride.setStatus(Ride.Status.DROPPED_OFF);
        currentRides.remove(currentRides.indexOf(ride));
        onDropOff(ride);
    }

    protected final @NonNull VehicleApp getVehicleApp() {
        return (VehicleApp) getOs().getApplications().parallelStream()
            .filter(app -> app instanceof VehicleApp)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("This app requires MultiStopApp to be mapped"));
        // for (Application application : getOs().getApplications()) {
        //     if (application instanceof VehicleApp) return (VehicleApp) application;
        // }
        // throw new IllegalStateException("This app requires MultiStopApp to be mapped");
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage.getMessage() instanceof RideBookingMessage rideBookingMessage) {
            // Ride booking message targeted the wrong shuttle
            if (!rideBookingMessage.getTargetVehicle().equals(getOs().getId())) {
                getLog().warn("Ignoring ride booking for another vehicle.");
                return;
            }

            // Shuttle's capacity reached
            // TODO: set status DECLINED for ride
            if (!(rides.size() < VEHICLE_CAPACITY)) {
                getLog().error("Shuttle's capacity reached.");
                return;
            }
            rides = rideBookingMessage.getRides();
            stops = rideBookingMessage.getStops();
            routes = rideBookingMessage.getRoutes();
            
            rides.stream()
                .filter(ride -> ride.getStatus() == Ride.Status.ASSIGNED)
                .forEach(ride -> onAcceptRide(ride));

            VehicleApp vehicleApp = getVehicleApp();
            vehicleApp.updateRides(rides);
            vehicleApp.updateStops(stops);
            vehicleApp.updateRoutes(routes);
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
