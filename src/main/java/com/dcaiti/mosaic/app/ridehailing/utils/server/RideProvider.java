package com.dcaiti.mosaic.app.ridehailing.utils.server;

import java.util.List;

public interface RideProvider {

    List<Ride> findNewRides(long timestamp);

}
