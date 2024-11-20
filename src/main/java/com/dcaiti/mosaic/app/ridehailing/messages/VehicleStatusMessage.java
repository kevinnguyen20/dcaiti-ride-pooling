package com.dcaiti.mosaic.app.ridehailing.messages;

import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;
import com.dcaiti.mosaic.app.ridehailing.vehicle.VehicleStop;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.routing.CandidateRoute;

import java.util.List;
import java.util.Queue;

import javax.annotation.Nonnull;

public class VehicleStatusMessage extends V2xMessage {

    private static final long serialVersionUID = 1L;

    private final VehicleStatus status;
    private final List<Ride> allRides;
    private final Queue<VehicleStop> currentStops;
    private final Queue<CandidateRoute> currentRoutes;

    public VehicleStatusMessage(MessageRouting routing, VehicleStatus status, List<Ride> allRides, Queue<VehicleStop> currentStops, Queue<CandidateRoute> currentRoutes) {
        super(routing);
        this.status = status;
        this.allRides = allRides;
        this.currentStops = currentStops;
        this.currentRoutes = currentRoutes;
    }

    public String getVehicleId() {
        return getStatus().getVehicleId();
    }

    public VehicleStatus getStatus() {
        return status;
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
