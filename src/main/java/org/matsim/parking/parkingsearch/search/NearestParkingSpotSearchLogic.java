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


package org.matsim.parking.parkingsearch.search;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.facilities.ActivityFacility;
import org.matsim.parking.parkingsearch.manager.FacilityBasedParkingManager;
import org.matsim.vehicles.Vehicle;

import java.util.HashSet;
import java.util.Map;

/**
 * @author jbischoff
 *
 */

public class NearestParkingSpotSearchLogic implements ParkingSearchLogic {

	private final Network network;
	private final Map<Id<ActivityFacility>, ActivityFacility> activityFacilities;
	private final ParkingRouter parkingRouter;
	private NetworkRoute actualRoute = null;
	private int currentLinkIdx;
	private final HashSet <Id<ActivityFacility>> triedParking;
	private int counter;
	private Id<Link> nextLink;

	/**
	 * {@link Network} the network
	 * @param parkingManager
	 * 
	 */
	public NearestParkingSpotSearchLogic(Network network, ParkingRouter parkingRouter, ParkingSearchManager parkingManager) {
		this.network = network;
		this.parkingRouter = parkingRouter;
		activityFacilities = ((FacilityBasedParkingManager) parkingManager).getParkingFacilities();
		currentLinkIdx = 0;
		triedParking = new HashSet<>();
		counter = 0;
		nextLink = null;
	}

	/**
	 * @param baseLinkId linkId of the origin destination where the parkingSearch starts
	 */
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Link> baseLinkId, Id<Vehicle> vehicleId, String mode, double now) {
		
		if (actualRoute == null) {
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
			actualRoute.setVehicleId(vehicleId);
		} else if (currentLinkId.equals(actualRoute.getEndLinkId())) {
			currentLinkIdx = 0;
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
			counter = 0;
			actualRoute.setVehicleId(vehicleId);
		}

	/*	if (currentLinkIdx == -1) {
			nextLink = actualRoute.getStartLinkId();
			currentLinkIdx++;
		} else */if (counter % 2 == 0) {
			if (currentLinkIdx == actualRoute.getLinkIds().size() ) {
				return actualRoute.getEndLinkId();
			}
			nextLink = actualRoute.getLinkIds().get(currentLinkIdx);
			currentLinkIdx++;
		}
		counter++;
		return nextLink;

	}
	
	public Id<Link> getNextParkingLocation(){
		return actualRoute.getEndLinkId();
	}
	
	public NetworkRoute getNextRoute(){
		return actualRoute;
	}
	
//	public NetworkRoute getRouteToNextParkingLocation(Id<Link> currentLinkId, Id<Link> baseLinkId, Id<Vehicle> vehicleId, String mode, double now) {
//		Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
//		Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
//		ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink);
//		actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
//				currentLinkId);
//		actualRoute.setVehicleId(vehicleId);
//		return actualRoute;
//	}
	
	private ActivityFacility findNearestParkingFacility(Coord coordBaseLink, Coord coordCurrentLink) {
		ActivityFacility nearstActivityFacility = null;
		double minDistance = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (triedParking.contains(activityFacility.getId()))
				continue;
			Coord facilityCoord = activityFacility.getCoord();

			double distanceBaseAndFacility = NetworkUtils.getEuclideanDistance(facilityCoord, coordBaseLink);
			double distanceCurrentAndFacility = NetworkUtils.getEuclideanDistance(coordCurrentLink, coordBaseLink);
			
			double distanceForParking = distanceBaseAndFacility + distanceCurrentAndFacility;
			if (distanceForParking < minDistance) {
				nearstActivityFacility = activityFacility;
				minDistance = distanceForParking;
			}
		}
		assert nearstActivityFacility != null;
		triedParking.add(nearstActivityFacility.getId());
		return nearstActivityFacility;
	}
	
	@Override
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Vehicle> vehicleId, String mode) {
		throw new RuntimeException("shouldn't happen - method not implemented");
	}
	
	@Override
	public void reset() {
		actualRoute = null;
		currentLinkIdx = 0;
		counter = 0;
	}

}
