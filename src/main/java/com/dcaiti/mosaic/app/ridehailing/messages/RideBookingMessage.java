package com.dcaiti.mosaic.app.ridehailing.messages;

import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import java.util.List;
import java.util.Queue;

import javax.annotation.Nonnull;

public class RideBookingMessage extends V2xMessage {
    private final String targetVehicle;
    private final List<Ride> rides;
    private final Queue<VehicleStop> stops;
    private final Queue<CandidateRoute> routes;

    public RideBookingMessage(MessageRouting routing, String targetVehicle, List<Ride> rides, Queue<VehicleStop> stops, Queue<CandidateRoute> routes) {
        super(routing);
        this.targetVehicle = targetVehicle;
        this.rides = rides;
        this.stops = stops;
        this.routes = routes;
    }

    public String getTargetVehicle() {
        return targetVehicle;
    }

    public List<Ride> getRides() {
        return rides;
    }

    public Queue<VehicleStop> getStops() {
        return stops;
    }

    public Queue<CandidateRoute> getRoutes() {
        return routes;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return new EncodedPayload(1);
    }
}
