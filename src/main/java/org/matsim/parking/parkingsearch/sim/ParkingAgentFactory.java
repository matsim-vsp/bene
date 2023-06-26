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

package org.matsim.parking.parkingsearch.sim;

import jakarta.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dynagent.DynAgent;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.manager.vehicleteleportationlogic.VehicleTeleportationLogic;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.DistanceMemoryParkingSearchLogic;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.contrib.parking.parkingsearch.search.RandomParkingSearchLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.AgentFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.parking.parkingsearch.DynAgent.agentLogic.BenensonParkingAgentLogic;
import org.matsim.parking.parkingsearch.DynAgent.agentLogic.MemoryBasedParkingAgentLogic;
import org.matsim.parking.parkingsearch.DynAgent.agentLogic.NearestParkingSpotAgentLogic;
import org.matsim.parking.parkingsearch.DynAgent.agentLogic.ParkingAgentLogic;
import org.matsim.parking.parkingsearch.ParkingSearchStrategy;
import org.matsim.parking.parkingsearch.search.BenensonParkingSearchLogic;
import org.matsim.parking.parkingsearch.search.NearestParkingSpotSearchLogic;

import com.google.inject.name.Named;

import java.util.Objects;

/**
 * @author jbischoff
 *
 */

public class ParkingAgentFactory implements AgentFactory {

	/**
	 *
	 */
	@Inject
	@Named(value = TransportMode.walk) RoutingModule walkRouter;
	@Inject
	ParkingSearchManager parkingManager;

	@Inject
	EventsManager events;
	@Inject
	ParkingRouter parkingRouter;
	@Inject
	Network network;
	@Inject
	VehicleTeleportationLogic teleportationLogic;
	QSim qsim;
	@Inject
	Config config;

	/**
	 *
	 */
	@Inject
	public ParkingAgentFactory(QSim qsim) {
		this.qsim = qsim;
	}

	@Override
	public MobsimAgent createMobsimAgentFromPerson(Person p) {
		ParkingSearchConfigGroup psConfigGroup = (ParkingSearchConfigGroup) config.getModules().get(ParkingSearchConfigGroup.GROUP_NAME);
		ParkingSearchLogic parkingLogic;
		ParkingAgentLogic agentLogic = null;

		switch (psConfigGroup.getParkingSearchStrategy()) {
			case Benenson -> {
				parkingLogic = new BenensonParkingSearchLogic(network, psConfigGroup);
				agentLogic = new BenensonParkingAgentLogic(p.getSelectedPlan(), parkingManager, walkRouter, network,
						parkingRouter, events, parkingLogic, ((QSim) qsim).getSimTimer(), teleportationLogic, psConfigGroup);
			}
			case Random -> {
				parkingLogic = new RandomParkingSearchLogic(network);
				agentLogic = new ParkingAgentLogic(p.getSelectedPlan(), parkingManager, walkRouter, network,
						parkingRouter, events, parkingLogic, ((QSim) qsim).getSimTimer(), teleportationLogic, psConfigGroup);
			}
			case DistanceMemory -> {
				parkingLogic = new DistanceMemoryParkingSearchLogic(network);
				agentLogic = new MemoryBasedParkingAgentLogic(p.getSelectedPlan(), parkingManager, walkRouter, network,
						parkingRouter, events, parkingLogic, ((QSim) qsim).getSimTimer(), teleportationLogic, psConfigGroup);
			}
			case NearestParkingSpot, NearestParkingSpotWithReservation -> {

				if (Objects.requireNonNull(psConfigGroup.getParkingSearchStrategy()) == ParkingSearchStrategy.NearestParkingSpotWithReservation) {
					parkingLogic = new NearestParkingSpotSearchLogic(network, parkingRouter, parkingManager, true);
				} else {
					parkingLogic = new NearestParkingSpotSearchLogic(network, parkingRouter, parkingManager, false);
				}
				agentLogic = new NearestParkingSpotAgentLogic(p.getSelectedPlan(), parkingManager, walkRouter,
						network, parkingRouter, events, parkingLogic, ((QSim) qsim).getSimTimer(),
						teleportationLogic, psConfigGroup);
			}
		}
		Id<Link> startLinkId = ((Activity) p.getSelectedPlan().getPlanElements().get(0)).getLinkId();
		if (startLinkId == null) {
			throw new NullPointerException(" No start link found. Should not happen.");
		}
		return new DynAgent(p.getId(), startLinkId, events, agentLogic);
	}

}
