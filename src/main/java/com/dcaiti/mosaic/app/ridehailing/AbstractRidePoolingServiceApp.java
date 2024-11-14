package com.dcaiti.mosaic.app.ridehailing;

import com.dcaiti.mosaic.app.ridehailing.messages.RideBookingMessage;
import com.dcaiti.mosaic.app.ridehailing.messages.VehicleStatusMessage;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.CamBuilder;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedAcknowledgement;
import org.eclipse.mosaic.fed.application.ambassador.simulation.communication.ReceivedV2xMessage;
import org.eclipse.mosaic.fed.application.app.ConfigurableApplication;
import org.eclipse.mosaic.fed.application.app.api.CommunicationApplication;
import org.eclipse.mosaic.fed.application.app.api.os.ServerOperatingSystem;
import org.eclipse.mosaic.interactions.communication.V2xMessageTransmission;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.routing.CandidateRoute;
import org.eclipse.mosaic.lib.util.scheduling.Event;
import org.eclipse.mosaic.rti.TIME;

public abstract class AbstractRidePoolingServiceApp<ConfigT> 
        extends ConfigurableApplication<ConfigT, ServerOperatingSystem>
        implements CommunicationApplication{

    public static final long UPDATE_INTERVAL = 10 * TIME.SECOND;

    private RideProvider rideProvider;
    // Mapping of RideId:Ride, information stored on dispatcher's side
    protected static final Map<Integer, Ride> storedRides = new HashMap<>();
    // Mapping of VehicleId:Vehicle, information received from shuttles
    protected final Map<String, VehicleStatus> registeredShuttles = new HashMap<>();

    // List of rides for each shuttle
    protected static final Map<String, List<Ride>> rides = new HashMap<>();
    // List of VehicleStops for each vehicle (sorted)
    protected static final Map<String, Queue<VehicleStop>> stops = new HashMap<>();
    // List of routes for each vehicle (sorted)
    protected static Map<String, Queue<CandidateRoute>> routes = new HashMap<>();

    // TODO: queue for routing, if add new routes, replace old route at position
    // n with 2 new routes
    
    protected AbstractRidePoolingServiceApp(Class<ConfigT> configClass) {
        super(configClass);
    }

    @Override
    public void onStartup() {
        getOs().getCellModule().enable();
        rideProvider = createRideBookingProvider();
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> checkPendingBookings());
    }

    protected abstract RideProvider createRideBookingProvider();
    protected abstract String chooseShuttle(Ride booking);
    protected abstract void calculateRouting(Ride booking, VehicleStatus shuttle);
    protected abstract void onVehicleRidePickup(Ride booking);
    protected abstract void onVehicleRideDropoff(Ride booking);

    private void checkPendingBookings() {
        if (isTornDown()) return;

        // Fetching new ride bookings
        rideProvider.findNewRides(getOs().getSimulationTime())
            .forEach(booking -> storedRides.put(booking.getBookingId(), booking));

        final Map<String, Ride> assignedBookings = new HashMap<>();
        storedRides.values().parallelStream().sorted(Comparator.comparingLong(Ride::getCreationTime)).forEach(booking -> {
            if (booking.getStatus() != Ride.Status.PENDING) return;

            // Assign booking to vehicle
            String shuttleId = chooseShuttle(booking);
            if (shuttleId == null) return;

            // Shuttle assignment check
            if (assignedBookings.get(shuttleId) != null || !registeredShuttles.get(shuttleId).hasEnoughCapacity()) return;

            // Initialize hash maps
            rides.putIfAbsent(shuttleId, new ArrayList<>());
            stops.putIfAbsent(shuttleId, new LinkedList<>());
            routes.putIfAbsent(shuttleId, new LinkedList<>());

            // Store ride
            rides.get(shuttleId).add(booking);

            // Add new VehicleStops + calculate new routes
            VehicleStatus shuttle = registeredShuttles.get(shuttleId);
            calculateRouting(booking, shuttle);

            getLog().infoSimTime(this, "Assigned ride booking {} to shuttle {}", booking.getBookingId(), shuttleId);
            booking.setAssignedVehicleId(shuttleId);
            booking.setStatus(Ride.Status.ASSIGNED);
            assignedBookings.put(shuttleId, booking);

            // Send new ride booking to shuttle
            MessageRouting messageRouting = getOs().getCellModule().createMessageRouting().topoCast(shuttleId);
            getOs().getCellModule().sendV2xMessage(new RideBookingMessage(
                messageRouting,
                shuttleId,
                rides.get(shuttleId),
                stops.get(shuttleId),
                routes.get(shuttleId)));
        });

        // Check for new ride bookings every 10 seconds
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> checkPendingBookings());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (receivedV2xMessage.getMessage() instanceof VehicleStatusMessage shuttleStatusMsg) {
            // Update stored vehicle status
            VehicleStatus shuttle = shuttleStatusMsg.getStatus();
            registeredShuttles.put(shuttle.getVehicleId(), shuttle);

            List<Ride> listOfCurrentRides = shuttle.getRides();
            if (listOfCurrentRides.isEmpty()) return;

            // Update stored rides
            listOfCurrentRides.forEach(currentRide -> {
                Ride storedRide = storedRides.get(currentRide.getBookingId());
                // TODO: exception handling required
                if (!shuttle.getVehicleId().equals(storedRide.getAssignedVehicleId())) return;

                if (currentRide.getStatus() == Ride.Status.DECLINED) {
                    storedRide.setStatus(Ride.Status.PENDING);
                    storedRide.setPickupTime(0);
                    storedRide.setDropOffTime(0);
                    storedRide.incrementNumberOfRejections();
                    storedRide.setAssignedVehicleId(null);
                } else {
                    storedRide.setStatus(currentRide.getStatus());
                }
                
                if (currentRide.getStatus() == Ride.Status.PICKED_UP && storedRide.getPickupTime() == 0) {
                    storedRide.setPickupTime(getOs().getSimulationTime());
                    onVehicleRidePickup(storedRide);
                }

                if (currentRide.getStatus() == Ride.Status.DROPPED_OFF && storedRide.getDropOffTime() == 0) {
                    storedRide.setDropOffTime(getOs().getSimulationTime());
                    onVehicleRideDropoff(storedRide);
                    getLog().infoSimTime(this, "Vehicle {} completed ride booking {}.", storedRide.getAssignedVehicleId(), storedRide.getBookingId());
                    // rides.get(shuttle.getVehicleId()).removeIf(ride -> ride.getStatus() == Ride.Status.DROPPED_OFF);
                }
            });
        }
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public void processEvent(Event event) {
    }

    @Override
    public void onAcknowledgementReceived(ReceivedAcknowledgement acknowledgedMessage) {
    }

    @Override
    public void onCamBuilding(CamBuilder camBuilder) {
    }

    @Override
    public void onMessageTransmitted(V2xMessageTransmission v2xMessageTransmission) {
    }
}
