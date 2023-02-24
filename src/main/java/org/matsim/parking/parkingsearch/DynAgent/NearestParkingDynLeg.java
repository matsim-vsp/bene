package org.matsim.parking.parkingsearch.DynAgent;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.parking.parkingsearch.DynAgent.ParkingDynLeg;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.parking.parkingsearch.search.NearestParkingSpotSearchLogic;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * @author Work
 *
 */
public class NearestParkingDynLeg extends ParkingDynLeg {

	private boolean parkingAtEndOfLeg = true;
	private boolean reachedDestinationWithoutParking = false;
	
	public NearestParkingDynLeg(String mode, NetworkRoute route, PlanElement folowingPlanElement, ParkingSearchLogic logic,
															ParkingSearchManager parkingManager, Id<Vehicle> vehicleId, MobsimTimer timer, EventsManager events) {
		super(mode, route, logic, parkingManager, vehicleId, timer, events);
		if (folowingPlanElement.getAttributes().getAsMap().containsKey("parking") && folowingPlanElement.getAttributes().getAttribute("parking").equals("noParking"))
			parkingAtEndOfLeg = false;
	}
	
	@Override
	public void movedOverNode(Id<Link> newLinkId) {
		currentLinkIdx++;
		currentLinkId = newLinkId;
		if (!parkingMode) {
			if (currentLinkId.equals(this.getDestinationLinkId())) {
				if (!parkingAtEndOfLeg) {
					reachedDestinationWithoutParking = true;
				} else {
					this.parkingMode = true;
					this.events
							.processEvent(new StartParkingSearchEvent(timer.getTimeOfDay(), vehicleId, currentLinkId));
					hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
				}
			}
		} else {
			hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
		}
	}

	@Override
	public Id<Link> getNextLinkId() {
		if (!parkingMode && !reachedDestinationWithoutParking) {
			List<Id<Link>> linkIds = route.getLinkIds();

			if (currentLinkIdx == linkIds.size() - 1) {
				return route.getEndLinkId();
			}
			return linkIds.get(currentLinkIdx + 1);

		} else {
			if (hasFoundParking || reachedDestinationWithoutParking) {
				// easy, we can just park where at our destination link
				return null;
			} else {
				// need to find the next link
				Id<Link> nextLinkId = ((NearestParkingSpotSearchLogic) this.logic).getNextLink(currentLinkId, route.getEndLinkId(), vehicleId, mode, timer.getTimeOfDay());
				return nextLinkId;
			}
		}
	}
	
}
