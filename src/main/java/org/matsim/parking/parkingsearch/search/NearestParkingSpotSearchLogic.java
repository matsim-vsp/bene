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

/**
 * 
 */
package org.matsim.parking.parkingsearch.search;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.config.Config;
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

	private Network network;
	private Map<Id<ActivityFacility>, ActivityFacility> activityFacilities;
	private ParkingRouter parkingRouter;
	private ParkingSearchManager parkingManager;
	private NetworkRoute actualRoute = null;
	private int currentLinkIdx;
	private HashSet <Id<ActivityFacility>> triedParking; 

	/**
	 * {@link Network} the network
	 * @param config 
	 * @param parkingManager 
	 * 
	 */
	public NearestParkingSpotSearchLogic(Network network, Config config, ParkingRouter parkingRouter, ParkingSearchManager parkingManager) {
		this.network = network;
		this.parkingRouter = parkingRouter;
		this.parkingManager = parkingManager;
		activityFacilities = ((FacilityBasedParkingManager) this.parkingManager).getParkingFacilities();
		currentLinkIdx = -1;
		triedParking = new HashSet<Id<ActivityFacility>>();
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
		} else if (currentLinkId.equals(actualRoute.getEndLinkId())) {
			currentLinkIdx = -1;
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
		}
		currentLinkIdx++;
		Id<Link> nextLink = actualRoute.getLinkIds().get(currentLinkIdx);
		
		return nextLink;

	}

	private ActivityFacility findNearestParkingFacility(Coord coordBaseLink, Coord coordCurrentLink) {
		ActivityFacility nearstActivityFacility = null;
		double minDistance = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (triedParking.contains(activityFacility.getId()))
				continue;
			double distanceBaseAndFacility = Double.MAX_VALUE;
			double distanceCurrentAndFacility = Double.MAX_VALUE;
			Coord facilityCoord = activityFacility.getCoord();

			distanceBaseAndFacility = NetworkUtils.getEuclideanDistance(facilityCoord, coordBaseLink);
			distanceCurrentAndFacility = NetworkUtils.getEuclideanDistance(coordCurrentLink, coordBaseLink);
			
			double distanceForParking = distanceBaseAndFacility + distanceCurrentAndFacility;
			if (distanceForParking < minDistance) {
				nearstActivityFacility = activityFacility;
				minDistance = distanceForParking;
			}
		}
		triedParking.add(nearstActivityFacility.getId());
		return nearstActivityFacility;
	}
	
	@Override
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Vehicle> vehicleId, String mode) {
		throw new RuntimeException("shouldn't happen - method not implemented");
	}
	
	@Override
	public void reset() {
	}

}
