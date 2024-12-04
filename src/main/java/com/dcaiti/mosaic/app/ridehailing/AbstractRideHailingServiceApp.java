// package com.dcaiti.mosaic.app.ridehailing;

// import com.dcaiti.mosaic.app.ridehailing.messages.RideBookingMessage;
// import com.dcaiti.mosaic.app.ridehailing.messages.VehicleStatusMessage;
// import com.dcaiti.mosaic.app.ridehailing.server.Ride;
// import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
// import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
// import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
// import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
// import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
// import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
// import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
// import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
// import org.eclipse.mosaic.lib.util.scheduling.Event;
// import org.eclipse.mosaic.rti.TIME;

// import java.util.Comparator;
// import java.util.HashMap;
// import java.util.Map;

// public abstract class AbstractRideHailingServiceApp<ConfigT>
//         extends ConfigurableApplication<ConfigT, ServerOperatingSystem>
//         implements CommunicationApplication {

//     private static final long UPDATE_INTERVAL = 5 * TIME.SECOND;

//     // Mapping rideId:ride
//     protected final Map<Integer, Ride> rides = new HashMap<>();
//     // Mapping vehicleId:vehicleStatus
//     protected final Map<String, VehicleStatus> registeredVehicles = new HashMap<>();

//     private RideProvider rideProvider;

//     protected AbstractRideHailingServiceApp(Class<ConfigT> configClass) {
//         super(configClass);
//     }

//     @Override
//     public void onStartup() {
//         getOs().getCellModule().enable();

//         rideProvider = createRideBookingProvider();

//         getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> checkPendingBookings());
//     }

//     protected abstract RideProvider createRideBookingProvider();

//     protected abstract String chooseTaxi(Ride booking);

//     protected abstract void onVehicleRidePickup(Ride booking);

//     protected abstract void onVehicleRideDropoff(Ride booking);

//     private void checkPendingBookings() {
//         if (isTornDown()) {
//             return;
//         }

//         // All ride order are read at once but some orders cannot yet be
//         // assigned as their "start" time is ahead the current timestamp
//         rideProvider.findNewRides(getOs().getSimulationTime())
//                 .forEach(booking -> rides.put(booking.getBookingId(), booking));

//         // Mapping taxiId:ride
//         final Map<String, Ride> assignedBookings = new HashMap<>();
//         rides.values().stream().sorted(Comparator.comparingLong(Ride::getCreationTime)).forEach(booking -> {
//             if (booking.getStatus() != Ride.Status.PENDING) {
//                 return;
//             }

//             // Better idea: first check if booking is already assigned to taxi

//             String taxiId = chooseTaxi(booking);
//             if (taxiId == null) {
//                 return;
//             }
//             if (assignedBookings.get(taxiId) != null || registeredVehicles.get(taxiId).getCurrentRide() != null) {
//                 // still on a ride, waiting
//                 return;
//             }

//             getLog().infoSimTime(this, "Assigned ride booking {} to vehicle {}", booking.getBookingId(), taxiId);

//             booking.setAssignedVehicleId(taxiId);
//             booking.setStatus(Ride.Status.ASSIGNED);
//             assignedBookings.put(taxiId, booking);

//             // sends the ride booking to the taxi
//             MessageRouting messageRouting = getOs().getCellModule().createMessageRouting().topoCast(taxiId);
//             getOs().getCellModule().sendV2xMessage(new RideBookingMessage(messageRouting, taxiId, booking));
//         });

//         // call this method again after UPDATE_INTERVAL of simulation time has passed
//         getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> checkPendingBookings());
//     }


//     @Override
//     public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
//         if (receivedV2xMessage.getMessage() instanceof VehicleStatusMessage taxiStatusMsg) {
//             VehicleStatus taxiStatus = taxiStatusMsg.getStatus();
//             registeredVehicles.put(taxiStatusMsg.getVehicleId(), taxiStatus);

//             Ride taxiRide = taxiStatusMsg.getStatus().getCurrentRide();
//             if (taxiRide == null) {
//                 return;
//             }

//             Ride storedRide = rides.get(taxiRide.getBookingId());
//             // Compare message from taxi vs. assigned vehicle for ride on
//             // dispatcher side
//             if (!taxiStatusMsg.getVehicleId().equals(storedRide.getAssignedVehicleId())) {
//                 return;
//             }

//             if (taxiRide.getStatus() == Ride.Status.DECLINED) {
//                 storedRide.setStatus(Ride.Status.PENDING);
//                 storedRide.setPickupTime(0);
//                 storedRide.setDropOffTime(0);
//                 storedRide.incrementNumberOfRejections();
//                 storedRide.setAssignedVehicleId(null);
//             } else {
//                 storedRide.setStatus(taxiRide.getStatus());
//             }

//             // First time taxi sends updated status of ride, update status of
//             // stored ride
//             if (taxiRide.getStatus() == Ride.Status.PICKED_UP && storedRide.getPickupTime() == 0) {
//                 storedRide.setPickupTime(getOs().getSimulationTime());
//                 onVehicleRidePickup(storedRide);
//             }

//             // Same here but just for drop off
//             if (taxiRide.getStatus() == Ride.Status.DROPPED_OFF && storedRide.getDropOffTime() == 0) {
//                 storedRide.setDropOffTime(getOs().getSimulationTime());
//                 onVehicleRideDropoff(storedRide);
//                 getLog().infoSimTime(this,
//                         "Vehicle {} completed ride booking {}.", storedRide.getAssignedVehicleId(), storedRide.getBookingId()
//                 );
//             }
//         }
//     }

//     @Override
//     public void onShutdown() {
//     }

//     @Override
//     public void processEvent(Event event) {
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
