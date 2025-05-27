# On-Demand Ride-Pooling with Eclipse MOSAIC

On-demand ride-pooling is emerging as an innovative mobility alternative to existing transportation modes. It offers greater flexibility than public transportation by operating without fixed routes or schedules, provides the convenience of door-to-door service, and is more affordable than owning a private vehicle, taking a taxi, or using ride-hailing services, as it allows users to share rides.

While ride-hailing has gained significant popularity in recent decades, its low occupancy rates and high number of empty miles contribute to increased congestion and emissions. With accelerating global population growth and urbanization, these challenges become more critical. Ride-pooling presents a promising alternative that combines the advantages of both worlds, ultimately helping to reduce traffic congestion and reduce emissions.

This project presents heuristics and metaheuristics as the optimal balance between computational speed and solution quality for ride-pooling problems. A centralized architecture is proposed, integrating multiple heuristics for assignment, rebalancing, and fleet management, rather than relying on a single heuristic. The architecture is implemented as an application within the Eclipse MOSAIC simulation framework.

A key contribution is the deployment of ride-pooling in the city-wide BeST scenario, which simulates Berlin's urban traffic for a realistic evaluation. Simulation results show that ride-pooling achieves waiting times comparable to ride-hailing while maintaining negligible detour times. From an operational perspective, it reduces total mileage, improves service rates, and enhances overall system efficiency, demonstrating its feasibility as an alternative to taxis, ride-hailing, and public transportation. Future research could explore machine learning techniques for fleet rebalancing and decentralized strategies for electric vehicle fleets.

## Overview

![alt text](https://github.com/user-attachments/assets/24bc2dc2-41a9-46c5-a7eb-6848fb57654b)

The ride-pooling architecture is implemented using the Eclipse MOSAIC Co-Simulation Framework. It integrates and synchronizes with the traffic simulator in Eclipse MOSAIC, which incorporates the BeST scenario. The Eclipse MOSAIC Cell Simulator is a built-in feature that enables entities, such as the dispatcher and shuttles, to communicate via cellular networks. Additionally, the Eclipse MOSAIC Application Simulator enables the implementation of application logic and its mapping to specific simulation units. Three applications were implemented for this work that will be used later in the simulations:

- **RidePoolingServiceApp**: Handles all tasks for which the dispatcher is accountable.  
- **RidePoolingVehicleApp**: Manages high-level actions of shuttles such as communication with the dispatcher and processing user requests.
- **ReturningToPointOfBusinessVehicleApp**: Manages low-level actions of
  shuttles such as route selection, stopping, and picking up or dropping off
  passengers.

![alt text](https://github.com/user-attachments/assets/727227b8-6db4-43ce-93ca-d74552dc6521)

## Operation Flow

![alt text](https://github.com/user-attachments/assets/80d68e6d-56cd-4811-b220-4d7b876cdb6b)

## Dispatcher and Shuttle

![alt text](https://github.com/user-attachments/assets/946584fd-6bca-4901-9f71-ba3dc64c844b)

## Simulation Scenario

| Parameter                          | Value                         |
|------------------------------------|-------------------------------|
| **Simulation time period**         | 12 p.m. - 7 a.m.              |
| **Simulation duration**            | 7 h                           |
| **Fleet size**                     | 100 shuttles                  |
| **Number of generated requests**   | 892                           |
| **Matching interval**              | 10 s                          |
| **Max. detour time**               | √(trip duration)              |
| **Simulation time step**           | 1 s                           |
| **Grid cell length**               | 2 km                          |
| **Shuttle status update interval** | 5 s                           |
| **Shuttle capacity**               | 2 passengers                  |
| **Min. stop time**                 | 20 s                          |
| **Max. stop time**                 | 2 min                         |

*Table: Relevant parameters for the simulation.*

## Repository Structre

This repository is structured as follows.

.\
├── AbstractRidePoolingServiceApp.java\
├── AbstractRidePoolingVehicleApp.java\
├── config\
│   ├── CAbstractRidePoolingVehicleApp.java\
│   └── CVehicleApp.java\
├── heuristics\
├── ridepooling\
├── RidePoolingProvider.java\
├── strategies\
│   ├── assignment\
│   ├── fleet\
│   └── rebalancing\
└── utils

- `AbstractRidePoolingServiceApp.java`: Defines the core functionality of a ride-pooling dispatcher.  
- `AbstractRidePoolingVehicleApp.java`: Implements the base logic for ride-pooling shuttles.  
- `config/`: Contains configuration classes.  
- `heuristics/`: Implements heuristic approaches.  
- `ridepooling/`: Contains core ride-pooling logic.  
- `RidePoolingProvider.java`: Manages the ride-pooling service, handling ride requests and vehicle assignments.  
- `strategies/`: Defines different strategies for optimizing the ride-pooling system.  
  - `assignment/`: Implements strategies for assigning passengers to shuttles.  
  - `fleet/`: Contains fleet management strategies.  
  - `rebalancing/`: Implements strategies to rebalance shuttles.
- `utils/`: Provides utility functions and helper classes.  
