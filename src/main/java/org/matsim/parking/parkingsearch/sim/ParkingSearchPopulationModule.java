package org.matsim.parking.parkingsearch.sim;

import org.matsim.contrib.parking.parkingsearch.sim.ParkingPopulationAgentSource;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.agents.AgentFactory;

public class ParkingSearchPopulationModule extends AbstractQSimModule {
	public final static String COMPONENT_NAME = "ParkingSearch";

	@Override
	protected void configureQSim() {
		if (getConfig().transit().isUseTransit()) {
			throw new RuntimeException("parking search together with transit is not implemented (should not be difficult)") ;
		} 
		
		bind(AgentFactory.class).to(ParkingAgentFactory.class).asEagerSingleton(); // (**)
		bind(AgentSource.class).to(ParkingPopulationAgentSource.class).asEagerSingleton();
		
		addNamedComponent(ParkingPopulationAgentSource.class, COMPONENT_NAME);
	}

}
