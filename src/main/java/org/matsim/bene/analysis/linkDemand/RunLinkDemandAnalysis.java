package org.matsim.bene.analysis.linkDemand;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
* @author ikaddoura
*/

public class RunLinkDemandAnalysis {

	public static void main(String[] args) {
		if (args.length == 2) {

			String outputDirectory = args[0];
			if (!outputDirectory.endsWith("/"))
				outputDirectory = outputDirectory + "/";
			String runId = args[1];

			EventsManager events = EventsUtils.createEventsManager();
			LinkDemandEventHandler handler = new LinkDemandEventHandler();
			events.addHandler(handler);

			String eventsFile = outputDirectory + runId + ".output_events.xml.gz";
			MatsimEventsReader reader = new MatsimEventsReader(events);
			events.initProcessing();
			reader.readFile(eventsFile);
			events.finishProcessing();

			handler.printResults(outputDirectory+"simwrapper_analysis/" + runId + ".");
		} else {
			throw new RuntimeException(
					"Please set the run directory path and/or password. \nCheck the class description for more details. Aborting...");
		}
	}

}
