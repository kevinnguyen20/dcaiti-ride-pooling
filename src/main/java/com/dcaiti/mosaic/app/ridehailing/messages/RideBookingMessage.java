package com.dcaiti.mosaic.app.ridehailing.messages;

import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import javax.annotation.Nonnull;

public class RideBookingMessage extends V2xMessage {

    private final Ride ride;
    private final String targetVehicle;

    public RideBookingMessage(MessageRouting routing, String targetVehicle, Ride ride) {
        super(routing);
        this.targetVehicle = targetVehicle;
        this.ride = ride;
    }

    public String getTargetVehicle() {
        return targetVehicle;
    }

    public Ride getRideBooking() {
        return ride;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return new EncodedPayload(1);
    }
}
