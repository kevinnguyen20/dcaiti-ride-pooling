package com.dcaiti.mosaic.app.ridehailing.messages;

import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;

import org.eclipse.mosaic.lib.objects.v2x.EncodedPayload;
import org.eclipse.mosaic.lib.objects.v2x.MessageRouting;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;

import javax.annotation.Nonnull;

public class VehicleStatusMessage extends V2xMessage {

    private static final long serialVersionUID = 1L;

    private final VehicleStatus status;

    public VehicleStatusMessage(MessageRouting routing, VehicleStatus status) {
        super(routing);
        this.status = status;
    }

    public String getVehicleId() {
        return getStatus().getVehicleId();
    }

    public VehicleStatus getStatus() {
        return status;
    }

    @Nonnull
    @Override
    public EncodedPayload getPayload() {
        return new EncodedPayload(1);
    }
}
