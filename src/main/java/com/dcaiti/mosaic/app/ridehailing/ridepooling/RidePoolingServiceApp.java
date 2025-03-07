package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import java.util.List;

import com.dcaiti.mosaic.app.ridehailing.AbstractRidePoolingServiceApp;
import com.dcaiti.mosaic.app.ridehailing.RidePoolingProvider;
import com.dcaiti.mosaic.app.ridehailing.heuristics.InsertionHeuristic;
import com.dcaiti.mosaic.app.ridehailing.heuristics.RestrictedSubgraphMatchingGreedy;
import com.dcaiti.mosaic.app.ridehailing.heuristics.RestrictedSubgraphMatchingSimple;
import com.dcaiti.mosaic.app.ridehailing.heuristics.RideHailingGreedy;
import com.dcaiti.mosaic.app.ridehailing.strategies.fleet.FleetManagement;
import com.dcaiti.mosaic.app.ridehailing.utils.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.utils.server.RideProvider;

public class RidePoolingServiceApp extends
    AbstractRidePoolingServiceApp<CRidePoolingServiceApp> {

    private final int heuristicMode = 1;
    public RidePoolingServiceApp() {
        super(CRidePoolingServiceApp.class);
    }

    @Override 
    protected RideProvider createRideBookingProvider() {
        return new RidePoolingProvider(getConfiguration().rideOrders);
    }

    @Override
    protected void assignBookingsToShuttles(List<Ride> newBookings) {
        switch(heuristicMode) {
            case 0 -> {
                FleetManagement.setShuttleCapacity(2);
                RestrictedSubgraphMatchingSimple.assignBookingsToShuttles(storedRides, registeredShuttles, newBookings, rides, stops, routes);
            }
            case 1 -> {
                FleetManagement.setShuttleCapacity(2);
                RestrictedSubgraphMatchingGreedy.assignBookingsToShuttles(storedRides, registeredShuttles, newBookings, rides, stops, routes);
            }
            case 2 -> {
                FleetManagement.setShuttleCapacity(1);
                RideHailingGreedy.assignBookingsToShuttles(storedRides, registeredShuttles, newBookings, rides, stops, routes);
            }
            case 3 -> {
                FleetManagement.setShuttleCapacity(2);
                InsertionHeuristic.assignBookingsToShuttles(storedRides, registeredShuttles, newBookings, rides, stops, routes);
            }
        }
    }

    @Override
    protected void onVehicleRidePickup(Ride booking) {}

    @Override
    protected void onVehicleRideDropoff(Ride booking) {}
}
