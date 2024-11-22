package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import java.util.List;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingServiceApp;
import com.dcaiti.mosaic.app.ridehailing.heuristics.RestrictedSubgraphMatching;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
import com.dcaiti.mosaic.app.ridehailing.simple.SimpleRideProvider;

public class RidePoolingServiceApp extends
    AbstractRidePoolingServiceApp<CRidePoolingServiceApp> {
    public RidePoolingServiceApp() {
        super(CRidePoolingServiceApp.class);
    }

    @Override 
    protected RideProvider createRideBookingProvider() {
        return new SimpleRideProvider(getConfiguration().rideOrders);
    }

    @Override
    protected void assignBookingsToShuttles(List<Ride> bookings) {
        RestrictedSubgraphMatching.assignBookingsToShuttles(storedRides, registeredShuttles, bookings, rides, stops, routes);
    }

    @Override
    protected void onVehicleRidePickup(Ride booking) {}

    @Override
    protected void onVehicleRideDropoff(Ride booking) {}
}
