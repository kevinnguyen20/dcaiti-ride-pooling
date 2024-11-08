package com.dcaiti.mosaic.app.ridehailing.ridepooling;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.util.gson.TimeFieldAdapter;

import com.google.gson.annotations.JsonAdapter;

public class CRidePoolingServiceApp {
    public final List<CRideOrder> rideOrders = new ArrayList<>();

    public static class CRideOrder {
        @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
        public long orderTime;
        
        @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
        public long deadline;

        public GeoPoint pickup;
        public GeoPoint dropoff;
    }
}
