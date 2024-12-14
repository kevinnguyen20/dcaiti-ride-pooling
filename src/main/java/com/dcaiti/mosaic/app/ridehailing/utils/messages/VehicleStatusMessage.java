package com.dcaiti.mosaic.app.ridehailing.utils.messages;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.VehicleStatus;

import java.util.List;

import javax.annotation.Nonnull;

public class VehicleStatusMessage extends V2xMessage {

    private static final long serialVersionUID = 1L;

    private final VehicleStatus status;
    private final List<Ride> declinedRides;
    private final List<Ride> finishedRides;

    public VehicleStatusMessage(MessageRouting routing, VehicleStatus status, List<Ride> declinedRides, List<Ride> finishedRides) {
        super(routing);
        this.status = status;
        this.declinedRides = declinedRides;
        this.finishedRides = finishedRides;
    }

    public String getVehicleId() {
        return getStatus().getVehicleId();
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public List<Ride> getDeclinedRides() {
        return declinedRides;
    }

    public List<Ride> getFinishedRides() {
        return finishedRides;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return new EncodedPayload(1);
    }
}
