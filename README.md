# On-Demand Ride-Pooling with Eclipse MOSAIC

![alt text](https://mother.dcaiti.tu-berlin.de/svn/thesis/kevin.nguyen/Thesis/03-Methodology/Images/trips-heatmap.png)

The ride-pooling architecture is implemented using the Eclipse MOSAIC Co-Simulation Framework. It integrates and synchronizes with the traffic simulator in Eclipse MOSAIC, which incorporates the BeST scenario. The Eclipse MOSAIC Cell Simulator is a built-in feature that enables entities, such as the dispatcher and shuttles, to communicate via cellular networks. Additionally, the Eclipse MOSAIC Application Simulator enables the implementation of application logic and its mapping to specific simulation units. Three applications were implemented for this work that will be used later in the simulations:

- **RidePoolingServiceApp**: Handles all tasks for which the dispatcher is accountable.  
- **RidePoolingVehicleApp**: Manages high-level actions of shuttles such as communication with the dispatcher and processing user requests.
- **ReturningToPointOfBusinessVehicleApp**: Manages low-level actions of
  shuttles such as route selection, stopping, and picking up or dropping off
  passengers.

![alt text](https://mother.dcaiti.tu-berlin.de/svn/thesis/kevin.nguyen/Thesis/04-Implementation/Images/mosaic.png)

## Operation Flow

![alt text](https://mother.dcaiti.tu-berlin.de/svn/thesis/kevin.nguyen/Thesis/03-Methodology/Images/dispatcher.png)

## Dispatcher and Shuttle

![alt text](https://mother.dcaiti.tu-berlin.de/svn/thesis/kevin.nguyen/Thesis/04-Implementation/Images/apps.png)

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
