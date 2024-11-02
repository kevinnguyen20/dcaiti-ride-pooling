package com.dcaiti.mosaic.app.ridehailing.simple;

import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class SimpleRideProvider implements RideProvider {

    private int nextId = 0;

    private final PriorityQueue<CSimpleRideHailingServiceApp.CRideOrder> rideOrders = new PriorityQueue<>(Comparator.comparingLong(a -> a.time));

    public SimpleRideProvider(List<CSimpleRideHailingServiceApp.CRideOrder> rideOrders) {
        this.rideOrders.addAll(rideOrders);
    }

    // Predefined ride orders in a file
    // A better solution would be converting ride orders into a data stream
    @Override
    public List<Ride> findNewRides(long timestamp) {
        final List<Ride> nextRides = new ArrayList<>();
        while (!rideOrders.isEmpty() && rideOrders.peek().time < timestamp) {
            CSimpleRideHailingServiceApp.CRideOrder rideOrder = rideOrders.poll();

            Ride ride = new Ride(nextId++, rideOrder.start, rideOrder.target, false);
            ride.setCreationTime(rideOrder.time);
            ride.setStatus(Ride.Status.PENDING);
            nextRides.add(ride);
        }
        return nextRides;
    }
}
