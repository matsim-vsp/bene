/* *********************************************************************** *
 * project: org.matsim.*
 * LinksEventHandler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2023 by the members listed in the COPYING,        *
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
package org.matsim.bene.analysis.linkDemand;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;

/**
 * @author Ricardo
 *
 */
public class LinkDemandEventHandler
		implements LinkLeaveEventHandler {
	private static final Logger log = Logger.getLogger(LinkDemandEventHandler.class);

	private Map<Id<Link>, Integer> linkId2vehicles = new HashMap<Id<Link>, Integer>();


	public LinkDemandEventHandler() {
	}

	@Override
	public void reset(int iteration) {
		this.linkId2vehicles.clear();
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {

		if (this.linkId2vehicles.containsKey(event.getLinkId())) {
			int vehicles = this.linkId2vehicles.get(event.getLinkId());
			this.linkId2vehicles.put(event.getLinkId(), vehicles + 1);

		} else {
			this.linkId2vehicles.put(event.getLinkId(), 1);
		}

	}

	public void printResults(String fileNameWithoutEnding) {
		{
			String fileName = fileNameWithoutEnding + "link_volume.csv";

			File file = new File(fileName);

			try {
				BufferedWriter bw = new BufferedWriter(new FileWriter(file));
				bw.write("linkId;demandPerDay");
				bw.newLine();

				for (Id<Link> linkId : this.linkId2vehicles.keySet()) {
					double volume = this.linkId2vehicles.get(linkId);
					bw.write(linkId + ";" + volume);
					bw.newLine();
				}

				bw.close();
				log.info("Output written to " + fileName);

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	public Map<Id<Link>, Integer> getLinkId2demand() {
		return linkId2vehicles;
	}

}
