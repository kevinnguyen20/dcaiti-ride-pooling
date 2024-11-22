package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.config.CAbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;

public class RidePoolingVehicleApp extends AbstractRidePoolingVehicleApp<CAbstractRidePoolingVehicleApp> {
    public RidePoolingVehicleApp() {
        super(CAbstractRidePoolingVehicleApp.class);
    }

    @Override
    protected void onAcceptRide(Ride ride) {
        currentRides.stream()
            .filter(currentRide -> currentRide.getBookingId() == ride.getBookingId())
            .forEach(currentRide -> currentRides.remove(currentRide));
        currentRides.add(ride);
    }

    @Override
    protected void onPickup(Ride ride) {}

    @Override
    protected void onDropOff(Ride ride) {}
}
