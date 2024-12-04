// package com.dcaiti.mosaic.app.ridehailing;

// import com.dcaiti.mosaic.app.ridehailing.config.CAbstractRideHailingVehicleApp;
// import com.dcaiti.mosaic.app.ridehailing.messages.RideBookingMessage;
// import com.dcaiti.mosaic.app.ridehailing.messages.VehicleStatusMessage;
// import com.dcaiti.mosaic.app.ridehailing.server.Ride;
// import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
// import com.dcaiti.mosaic.app.ridehailing.vehicle.StopEvent;
// import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
// import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
// import org.eclipse.mosaic.fed.application.app.api.Application;
// import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
// import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
// import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
// import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
// import org.eclipse.mosaic.lib.util.scheduling.Event;
// import org.eclipse.mosaic.rti.TIME;

// import javax.annotation.Nonnull;

// public abstract class AbstractRideHailingVehicleApp<ConfigT extends CAbstractRideHailingVehicleApp>
//         extends ConfigurableApplication<ConfigT, VehicleOperatingSystem>
//         implements CommunicationApplication {

//     private static final long UPDATE_INTERVAL = 5 * TIME.SECOND;

//     // TODO: for ride-pooling should be list of rides
//     protected Ride currentRide = null;

//     public AbstractRideHailingVehicleApp(Class<ConfigT> configClass) {
//         super(configClass);
//     }

//     @Override
//     public void onStartup() {
//         getOs().getCellModule().enable();

//         getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> sendUpdate());
//     }

//     protected abstract void onAcceptRide(Ride ride);

//     protected abstract void onPickup(Ride ride);

//     protected abstract void onDropOff(Ride ride);

//     private void sendUpdate() {
//         MessageRouting messageRouting = getOs().getCellModule().createMessageRouting().topoCast(getConfiguration().serverName);
//         VehicleStatusMessage vehicleStatus = new VehicleStatusMessage(messageRouting, createVehicleStatus());
//         getOs().getCellModule().sendV2xMessage(vehicleStatus);
//         if (currentRide != null && currentRide.getStatus() == Ride.Status.DROPPED_OFF) {
//             // we've sent the last ride status to the server, so we can delete our current ride and wait for new rides
//             currentRide = null;
//         }
//         getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> sendUpdate());
//     }

//     protected VehicleStatus createVehicleStatus() {
//         return new VehicleStatus(
//                 getOs().getId(),
//                 getOs().getNavigationModule().getCurrentPosition(),
//                 getOs().getVehicleData().getDistanceDriven(),
//                 currentRide
//         );
//     }

//     @Override
//     public void processEvent(Event event) {
//         if (event instanceof StopEvent rideStop) {
//             if (currentRide == null) {
//                 // not on a ride, nothing to do
//                 return;
//             }

//             // Another vehicle's ride
//             int rideId = rideStop.getRideStop().getRideId();
//             if (rideId != currentRide.getBookingId()) {
//                 return;
//             }

//             if (rideStop.getRideStop().getStopReason() == VehicleStop.StopReason.PICK_UP) {
//                 pickup(currentRide);
//             } else if (rideStop.getRideStop().getStopReason() == VehicleStop.StopReason.DROP_OFF) {
//                 dropOff(currentRide);
//             }
//         }
//     }

//     protected void pickup(Ride ride) {
//         MultiStopApp multiStopApp = getMultistopApp();
//         if (multiStopApp.addStop(ride.getDropoffLocation())) {
//             ride.setStatus(Ride.Status.PICKED_UP);
//         } else {
//             getLog().error("The stop has been declined (invalid dropoff location).");
//             ride.setStatus(Ride.Status.DECLINED);
//         }
//         onPickup(ride);
//     }

//     protected void dropOff(Ride ride) {
//         ride.setStatus(Ride.Status.DROPPED_OFF);
//         onDropOff(ride);
//     }

//     protected final @Nonnull MultiStopApp getMultistopApp() {
//         for (Application application : getOs().getApplications()) {
//             if (application instanceof MultiStopApp) {
//                 return (MultiStopApp) application;
//             }
//         }
//         throw new IllegalStateException("This app requires MultiStopApp to be mapped");
//     }

//     @Override
//     public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
//         if (receivedV2xMessage.getMessage() instanceof RideBookingMessage rideBookingMessage) {
//             if (!rideBookingMessage.getTargetVehicle().equals(getOs().getId())) {
//                 getLog().warn("Ignoring ride booking for another vehicle.");
//                 return;
//             }
//             Ride ride = rideBookingMessage.getRideBooking();
//             MultiStopApp multiStopApp = getMultistopApp();

//             // For ride-pooling: check vehicle capacity (multiple rider possible)
//             if (currentRide != null) {
//                 getLog().error("Cannot accept multiple ride bookings.");
//                 return;
//             }

//             final Ride rideCopy = new Ride(ride);
//             if (multiStopApp.addStop(rideCopy.getPickupLocation())) {
//                 currentRide = rideCopy;
//                 currentRide.setStatus(Ride.Status.ASSIGNED);
//                 onAcceptRide(ride);
//             } else {
//                 getLog().error("The ride has been declined (invalid pickup location).");

//             }
//         }
//     }

//     @Override
//     public void onShutdown() {

//     }

//     @Override
//     public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {

//     }

//     @Override
//     public void onCamBuilding(CamBuilder camBuilder) {

//     }

//     @Override
//     public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {

//     }
// }
