package com.dcaiti.mosaic.app.ridehailing;

import com.dcaiti.mosaic.app.ridehailing.messages.RideBookingMessage;
import com.dcaiti.mosaic.app.ridehailing.messages.VehicleStatusMessage;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import java.util.Comparator;
import java.util.HashMap;
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
    protected static Map<String, Queue<VehicleStop>> stops = new HashMap<>();
    // List of routes for each vehicle (sorted)
    protected static Map<String, Queue<CandidateRoute>> routes = new HashMap<>();
    
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
    protected abstract void assignBookingsToShuttles(List<Ride> booking);
    protected abstract void onVehicleRidePickup(Ride booking);
    protected abstract void onVehicleRideDropoff(Ride booking);

    private void checkPendingBookings() {
        if (isTornDown()) return;

        // Fetch and store new ride bookings
        rideProvider.findNewRides(getOs().getSimulationTime())
            .forEach(booking -> storedRides.put(booking.getBookingId(), booking));

        // Update statuses and collect new bookings
        List<Ride> newBookings = storedRides.values().parallelStream()
            .peek(ride -> {
                if (ride.getStatus() == Ride.Status.DECLINED) ride.setStatus(Ride.Status.PENDING);
            })
            .filter(ride -> ride.getStatus() == Ride.Status.PENDING)
            .sorted(Comparator.comparingLong(Ride::getCreationTime))
            .toList();
        
        // Assign new bookings to shuttles
        if (!newBookings.isEmpty()) assignBookingsToShuttles(newBookings);

        // Notify shuttles of assigned bookings
        newBookings.stream()
            .filter(booking -> booking.getStatus() == Ride.Status.ASSIGNED)
            .forEach(booking -> {
                String shuttleId = booking.getAssignedVehicleId();
                getLog().infoSimTime(this, "Assigned ride booking {} to shuttle {}", booking.getBookingId(), shuttleId);
                sendRideBookingMessage(shuttleId, booking);
            });

        // Check for new ride bookings every 10 seconds
        getOs().getEventManager().addEvent(getOs().getSimulationTime() + UPDATE_INTERVAL, e -> checkPendingBookings());
    }

    @Override
    public void onMessageReceived(ReceivedV2xMessage receivedV2xMessage) {
        if (!(receivedV2xMessage.getMessage() instanceof VehicleStatusMessage shuttleStatusMsg)) return;

        VehicleStatus shuttle = shuttleStatusMsg.getStatus();
        registeredShuttles.put(shuttle.getVehicleId(), shuttle);

        // Declined rides
        shuttleStatusMsg.getDeclinedRides().forEach(declinedRide -> {
            Ride storedRide = storedRides.get(declinedRide.getBookingId());
            getLog().infoSimTime(this, "Shuttle {} declined ride booking {}", shuttle.getVehicleId(), declinedRide.getBookingId());
            storedRide.setStatus(Ride.Status.PENDING);
            storedRide.setPickupTime(0);
            storedRide.setDropOffTime(0);
            storedRide.incrementNumberOfRejections();
            storedRide.setAssignedVehicleId(null);
        });

        // Finished rides
        shuttleStatusMsg.getFinishedRides().forEach(finishedRide -> {
            Ride storedRide = storedRides.get(finishedRide.getBookingId());
            getLog().infoSimTime(this, "Vehicle {} completed ride booking {}.", storedRide.getAssignedVehicleId(), storedRide.getBookingId());

            if (finishedRide.getStatus() == Ride.Status.DROPPED_OFF && storedRide.getDropOffTime() == 0) {
                storedRide.setStatus(finishedRide.getStatus());
                registeredShuttles.get(shuttle.getVehicleId()).getCurrentRides().remove(finishedRide);
                storedRide.setDropOffTime(getOs().getSimulationTime());
                onVehicleRideDropoff(storedRide);
            }
        });

        // Current rides
        shuttle.getCurrentRides().forEach(currentRide -> {
            Ride storedRide = storedRides.get(currentRide.getBookingId());
            if (!shuttle.getVehicleId().equals(storedRide.getAssignedVehicleId())) return;

            storedRide.setStatus(currentRide.getStatus());
            if (currentRide.getStatus() == Ride.Status.PICKED_UP && storedRide.getPickupTime() == 0) {
                storedRide.setPickupTime(getOs().getSimulationTime());
                onVehicleRidePickup(storedRide);
            }
        });
    }

    private void sendRideBookingMessage(String shuttleId, Ride booking) {
        MessageRouting messageRouting = getOs().getCellModule().createMessageRouting().topoCast(shuttleId);
        getOs().getCellModule().sendV2xMessage(new RideBookingMessage(
            messageRouting,
            shuttleId,
            rides.get(shuttleId),
            stops.get(shuttleId),
            routes.get(shuttleId)
        ));
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
