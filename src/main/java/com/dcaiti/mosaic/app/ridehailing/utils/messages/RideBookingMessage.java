package com.dcaiti.mosaic.app.ridehailing.utils.messages;

import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.vehicle.VehicleStop;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import java.util.List;
import java.util.Queue;

import javax.annotation.Nonnull;

public class RideBookingMessage extends V2xMessage {
    private final String targetVehicle;
    private final List<Ride> allRides;
    private final Queue<VehicleStop> currentStops;
    private final Queue<CandidateRoute> currentRoutes;

    public RideBookingMessage(MessageRouting routing, String targetVehicle, List<Ride> rides, Queue<VehicleStop> stops, Queue<CandidateRoute> routes) {
        super(routing);
        this.targetVehicle = targetVehicle;
        this.allRides = rides;
        this.currentStops = stops;
        this.currentRoutes = routes;
    }

    public String getTargetVehicle() {
        return targetVehicle;
    }

    public List<Ride> getAllRides() {
        return allRides;
    }

    public Queue<VehicleStop> getCurrentStops() {
        return currentStops;
    }

    public Queue<CandidateRoute> getCurrentRoutes() {
        return currentRoutes;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return new EncodedPayload(1);
    }
}
