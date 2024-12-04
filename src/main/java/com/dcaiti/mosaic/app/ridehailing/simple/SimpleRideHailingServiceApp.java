// package com.dcaiti.mosaic.app.ridehailing.simple;

// import com.dcaiti.mosaic.app.ridehailing.AbstractRideHailingServiceApp;
// import com.dcaiti.mosaic.app.ridehailing.server.Ride;
// import com.dcaiti.mosaic.app.ridehailing.server.RideProvider;
// import com.dcaiti.mosaic.app.ridehailing.server.VehicleStatus;

// public class SimpleRideHailingServiceApp extends AbstractRideHailingServiceApp<CSimpleRideHailingServiceApp> {

//     public SimpleRideHailingServiceApp() {
//         super(CSimpleRideHailingServiceApp.class);
//     }

//     // Predefined ride orders in a file
//     // A better solution would be converting ride orders into a data stream
//     @Override
//     protected RideProvider createRideBookingProvider() {
//         return new SimpleRideProvider(getConfiguration().rideOrders);
//     }

//     @Override
//     protected String chooseTaxi(Ride booking) {
//         // simple dispatching: choose first free vehicle in list
//         for (VehicleStatus vehicle : registeredVehicles.values()) {
//             if (vehicle.getCurrentRide() == null) {
//                 return vehicle.getVehicleId();
//             }
//         }
//         return null;
//     }

//     @Override
//     protected void onVehicleRidePickup(Ride booking) {

//     }

//     @Override
//     protected void onVehicleRideDropoff(Ride booking) {

//     }
// }
