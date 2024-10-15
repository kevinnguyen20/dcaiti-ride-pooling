package com.dcaiti.mosaic.app.ridehailing.simple;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.util.gson.TimeFieldAdapter;

import com.google.gson.annotations.JsonAdapter;

import java.util.ArrayList;
import java.util.List;

public class CSimpleRideHailingServiceApp {

    public final List<CRideOrder> rideOrders = new ArrayList<>();

    public static class CRideOrder {

        @JsonAdapter(TimeFieldAdapter.NanoSeconds.class)
        public long time;

        public GeoPoint start;
        public GeoPoint target;
    }
}
