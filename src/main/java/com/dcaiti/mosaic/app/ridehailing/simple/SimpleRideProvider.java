package com.dcaiti.mosaic.app.ridehailing.simple;

import com.dcaiti.mosaic.app.ridehailing.ridepooling.CRidePoolingServiceApp;
import com.dcaiti.mosaic.app.ridehailing.server.Ride;
import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class SimpleRideProvider implements RideProvider {

    private int nextId = 0;

    private final PriorityQueue<CRidePoolingServiceApp.CRideOrder> rideOrders = new PriorityQueue<>(Comparator.comparingLong(a -> a.orderTime));

    public SimpleRideProvider(List<CRidePoolingServiceApp.CRideOrder> rideOrders) {
        this.rideOrders.addAll(rideOrders);
    }

    // TODO: Run the simulation and the passenger model separately
    @Override
    public List<Ride> findNewRides(long timestamp) {
        final List<Ride> nextRides = new ArrayList<>();
        while (!rideOrders.isEmpty() && rideOrders.peek().orderTime < timestamp) {
            CRidePoolingServiceApp.CRideOrder rideOrder = rideOrders.poll();
            Ride ride = new Ride(nextId++, rideOrder.pickup, rideOrder.dropoff, false);
            ride.setCreationTime(rideOrder.orderTime);
            ride.setDeadline(rideOrder.deadline);
            ride.setStatus(Ride.Status.PENDING);
            nextRides.add(ride);
        }
        return nextRides;
    }
}
