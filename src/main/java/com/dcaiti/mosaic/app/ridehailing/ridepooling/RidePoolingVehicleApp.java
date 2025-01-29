package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.config.CAbstractRidePoolingVehicleApp;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;

public class RidePoolingVehicleApp extends AbstractRidePoolingVehicleApp<CAbstractRidePoolingVehicleApp> {
    public RidePoolingVehicleApp() {
        super(CAbstractRidePoolingVehicleApp.class);
    }

    @Override
    protected synchronized void onAcceptRide(Ride ride) {
        // Update ride status
        currentRides.removeIf(currentRide -> currentRide.getBookingId() == ride.getBookingId());
        currentRides.add(ride);
    }

    @Override
    protected void onPickup(Ride ride) {
        ride.setStartDistance(getOs().getNavigationModule().getVehicleData().getDistanceDriven());
    }

    @Override
    protected void onDropOff(Ride ride) {
        currentRides.remove(ride);
        finishedRides.add(ride);

        ride.setEndDistance(getOs().getNavigationModule().getVehicleData().getDistanceDriven());
    }
}
