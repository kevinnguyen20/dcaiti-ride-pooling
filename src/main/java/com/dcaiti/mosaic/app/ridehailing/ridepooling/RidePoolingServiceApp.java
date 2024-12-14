package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import java.util.List;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingServiceApp;
import com.dcaiti.mosaic.app.ridehailing.RidePoolingProvider;
import com.dcaiti.mosaic.app.ridehailing.heuristics.RestrictedSubgraphMatching;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.RideProvider;

public class RidePoolingServiceApp extends
    AbstractRidePoolingServiceApp<CRidePoolingServiceApp> {
    public RidePoolingServiceApp() {
        super(CRidePoolingServiceApp.class);
    }

    @Override 
    protected RideProvider createRideBookingProvider() {
        return new RidePoolingProvider(getConfiguration().rideOrders);
    }

    @Override
    protected void assignBookingsToShuttles(List<Ride> newBookings) {
        // Vehicle capacity of 2
        RestrictedSubgraphMatching.assignBookingsToShuttles(storedRides, registeredShuttles, newBookings, rides, stops, routes);
    }

    @Override
    protected void onVehicleRidePickup(Ride booking) {}

    @Override
    protected void onVehicleRideDropoff(Ride booking) {}
}
