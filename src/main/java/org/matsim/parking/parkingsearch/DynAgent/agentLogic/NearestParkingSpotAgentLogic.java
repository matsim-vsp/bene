/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.parking.parkingsearch.DynAgent.agentLogic;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.dynagent.DynAction;
import org.matsim.contrib.dynagent.IdleDynActivity;
import org.matsim.contrib.dynagent.StaticPassengerDynLeg;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.manager.vehicleteleportationlogic.VehicleTeleportationLogic;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.parking.parkingsearch.ParkingUtils;
import org.matsim.parking.parkingsearch.DynAgent.NearestParkingDynLeg;
import org.matsim.parking.parkingsearch.sim.ParkingSearchConfigGroup;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.vehicles.Vehicle;


/**
 * @author Ricardo Ewert
 *
 */
public class NearestParkingSpotAgentLogic extends ParkingAgentLogic {
	public NearestParkingSpotAgentLogic(Plan plan, ParkingSearchManager parkingManager, RoutingModule walkRouter, Network network,
										ParkingRouter parkingRouter, EventsManager events, ParkingSearchLogic parkingLogic, MobsimTimer timer,
										VehicleTeleportationLogic teleportationLogic, ParkingSearchConfigGroup configGroup) {
		super(plan, parkingManager, walkRouter, network, parkingRouter, events, parkingLogic, timer, teleportationLogic, configGroup);
	}

	@Override
	public DynAction computeNextAction(DynAction oldAction, double now) {
		// we have the following cases of ending dynacts:
		// non-car trip arrival: start Activity
		// car-trip arrival: add park-car activity 
		// park-car activity: get next PlanElement & add walk leg to activity location
		// walk-leg to act: start next PlanElement Activity
		// ordinary activity: get next Leg, if car: go to car, otherwise add ordinary leg by other mode
		// walk-leg to car: add unpark activity
		// unpark activity: find the way to the next route & start leg

		switch (lastParkActionState){
		case ACTIVITY:
			return nextStateAfterActivity(oldAction, now);
				
		case CARTRIP:
			return nextStateAfterCarTrip(oldAction,now);
			
		case NONCARTRIP:
			return nextStateAfterNonCarTrip(oldAction,now);
			
		case PARKACTIVITY:
			return nextStateAfterParkActivity(oldAction,now);
		
		case UNPARKACTIVITY:
			return nextStateAfterUnParkActivity(oldAction,now);
			
		case WALKFROMPARK:
			return nextStateAfterWalkFromPark(oldAction,now);
			
		case WALKTOPARK:
			return nextStateAfterWalkToPark(oldAction,now);
		
		}
		throw new RuntimeException("unreachable code");
	}

	@Override
	protected DynAction nextStateAfterUnParkActivity(DynAction oldAction, double now) {
		// we have unparked, now we need to get going by car again.
		
		Leg currentPlannedLeg = (Leg) currentPlanElement;
		Route plannedRoute = currentPlannedLeg.getRoute();
		NetworkRoute actualRoute = this.parkingRouter.getRouteFromParkingToDestination(plannedRoute.getEndLinkId(), now, agent.getCurrentLinkId());
		actualRoute.setVehicleId(currentlyAssignedVehicleId);
		if (!plannedRoute.getStartLinkId().equals(actualRoute.getStartLinkId()))
			currentPlannedLeg.setRoute(actualRoute);
		if ((this.parkingManager.unParkVehicleHere(currentlyAssignedVehicleId, agent.getCurrentLinkId(), now))||(isinitialLocation)){
			this.lastParkActionState = LastParkActionState.CARTRIP;
			isinitialLocation = false;
//			Leg currentLeg = (Leg) this.currentPlanElement;
			int planIndexNextActivity = planIndex+1;
			Activity nextPlanElement = (Activity) plan.getPlanElements().get(planIndexNextActivity);
			if (nextPlanElement.getAttributes().getAsMap().containsKey("parking") && nextPlanElement.getAttributes().getAttribute("parking").equals("noParking"))
				this.lastParkActionState = LastParkActionState.WALKFROMPARK;
			//this could be Car, Carsharing, Motorcylce, or whatever else mode we have, so we want our leg to reflect this.
			return new NearestParkingDynLeg(currentPlannedLeg, actualRoute, plan, planIndexNextActivity,  parkingLogic, parkingManager, currentlyAssignedVehicleId, timer, events);
		}
		else throw new RuntimeException("parking location mismatch");
		
	}
	
	@Override
	protected DynAction nextStateAfterParkActivity(DynAction oldAction, double now) {
		// add a walk leg after parking
		Leg currentPlannedLeg = (Leg) currentPlanElement;
		Facility fromFacility = new LinkWrapperFacility (network.getLinks().get(agent.getCurrentLinkId()));
		Facility toFacility = new LinkWrapperFacility (network.getLinks().get(currentPlannedLeg.getRoute().getEndLinkId()));
		List<? extends PlanElement> walkTrip = walkRouter.calcRoute(DefaultRoutingRequest.withoutAttributes(fromFacility, toFacility, now, plan.getPerson()));
		if (walkTrip.size() != 1 || ! (walkTrip.get(0) instanceof Leg)) {
			String message = "walkRouter returned something else than a single Leg, e.g. it routes walk on the network with non_network_walk to access the network. Not implemented in parking yet!";
			log.error(message);
			throw new RuntimeException(message);
		}
		Leg walkLeg = (Leg) walkTrip.get(0);
		this.lastParkActionState = LastParkActionState.WALKFROMPARK;
		this.stageInteractionType = null;
		if (!walkLeg.getTravelTime().equals(OptionalTime.defined(0.))) 
			return new StaticPassengerDynLeg(walkLeg.getRoute(), walkLeg.getMode());
		else
			return nextStateAfterWalkFromPark(oldAction,now);
	}

	@Override
	protected DynAction nextStateAfterActivity(DynAction oldAction, double now) {
		// we could either depart by car or not next

		if (plan.getPlanElements().size() >= planIndex+1){
		planIndex++;
		this.currentPlanElement = plan.getPlanElements().get(planIndex);
		Leg currentLeg = (Leg) currentPlanElement;
		if (currentLeg.getMode().equals(TransportMode.car)){
			Id<Vehicle> vehicleId = Id.create(this.agent.getId(), Vehicle.class);
			Id<Link> parkLink = this.parkingManager.getVehicleParkingLocation(vehicleId);
			
			if (parkLink == null){
				//this is the first activity of a day and our parking manager does not provide informations about initial stages. We suppose the car is parked where we are
				parkLink = agent.getCurrentLinkId();
			}

    		Facility fromFacility = new LinkWrapperFacility (network.getLinks().get(agent.getCurrentLinkId()));
            Id<Link> teleportedParkLink = this.teleportationLogic.getVehicleLocation(agent.getCurrentLinkId(), vehicleId, parkLink, now, currentLeg.getMode());
    		Facility toFacility = new LinkWrapperFacility (network.getLinks().get(teleportedParkLink));
    		List<? extends PlanElement> walkTrip = walkRouter.calcRoute(DefaultRoutingRequest.withoutAttributes(fromFacility, toFacility, now, plan.getPerson()));
    		if (walkTrip.size() != 1 || ! (walkTrip.get(0) instanceof Leg)) {
    			String message = "walkRouter returned something else than a single Leg, e.g. it routes walk on the network with non_network_walk to access the network. Not implemented in parking yet!";
    			log.error(message);
    			throw new RuntimeException(message);
    		}
    		Leg walkLeg = (Leg) walkTrip.get(0);
			this.currentlyAssignedVehicleId = vehicleId;
			this.stageInteractionType = ParkingUtils.PARKACTIVITYTYPE;
    		if (!walkLeg.getTravelTime().equals(OptionalTime.defined(0.))) {
				this.lastParkActionState = LastParkActionState.WALKTOPARK;
				return new StaticPassengerDynLeg(walkLeg.getRoute(), walkLeg.getMode());
    		}
    		else
    		{
    			return nextStateAfterWalkToPark(oldAction, now);
    		}
		}
		else if (currentLeg.getMode().equals(TransportMode.pt)) {
			if (currentLeg.getRoute() instanceof TransitPassengerRoute){
				throw new IllegalStateException ("not yet implemented");
			}
			else {
				this.lastParkActionState = LastParkActionState.NONCARTRIP;
				return new StaticPassengerDynLeg(currentLeg.getRoute(), currentLeg.getMode());
			}
		//teleport or pt route	
		} 
		else {
		//teleport	
			this.lastParkActionState = LastParkActionState.NONCARTRIP;
			return new StaticPassengerDynLeg(currentLeg.getRoute(), currentLeg.getMode());
		}
		
	}else throw new RuntimeException("no more leg to follow but activity is ending\nLastPlanElement: "+currentPlanElement.toString()+"\n Agent "+this.agent.getId()+"\nTime: "+Time.writeTime(now));
	}
	
	@Override
	protected DynAction nextStateAfterWalkToPark(DynAction oldAction, double now) {
		//walk2park is complete, we can unpark.
		this.lastParkActionState = LastParkActionState.UNPARKACTIVITY;
		PlanElement beforePlanElement = plan.getPlanElements().get(planIndex -1);
		if (beforePlanElement.getAttributes().getAsMap().containsKey("parking") && beforePlanElement.getAttributes().getAttribute("parking").equals("noParking"))
			return nextStateAfterUnParkActivity(oldAction, now);
		return new IdleDynActivity(this.stageInteractionType, now + configGroup.getUnparkduration());
	}
}
