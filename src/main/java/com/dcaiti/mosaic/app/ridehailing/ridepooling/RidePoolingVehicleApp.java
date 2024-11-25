package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import java.util.List;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.config.CAbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;

public class RidePoolingVehicleApp extends AbstractRidePoolingVehicleApp<CAbstractRidePoolingVehicleApp> {
    public RidePoolingVehicleApp() {
        super(CAbstractRidePoolingVehicleApp.class);
    }

    @Override 
    protected synchronized void onAcceptRide(Ride ride) {
        List<Ride> ridesToRemove = currentRides.parallelStream()
            .filter(currentRide -> currentRide.getBookingId() == ride.getBookingId())
            .toList();

        currentRides.removeAll(ridesToRemove);

        currentRides.add(ride);
    }

    @Override
    protected void onPickup(Ride ride) {}

    @Override
    protected void onDropOff(Ride ride) {
        currentRides.remove(ride);
        finishedRides.add(ride);
    }
}
