/**
 * Copyright 2014 SeaClouds
 * Contact: SeaClouds
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package eu.draco.platform.discoverer.core;

import eu.draco.platform.discoverer.API.*;
import eu.draco.platform.discoverer.Crawler.CrawlerManager;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

import java.util.ArrayList;
import java.util.Collection;


public class Discoverer	extends Application<DiscovererConfiguration> {
	/* singleton */
	private static final Discoverer singleInstance = new Discoverer();
    private ArrayList<String> offeringNodeTemplates = new ArrayList<>();

    public static Discoverer instance() { return singleInstance; }

	/* vars */
	public CrawlerManager crawlerManager;
	public OfferingManager offeringManager;

	private static IAPI[] APIList = {
			new FetchAPI(),		/* /fetchOffer */
            new FetchAllAPI(),  /* /fetch_all */
			new DeleteAPI(),	/* /delete */
			//new AddAPI(),		/* /add */
			new StatisticsAPI() /* /statistics */
	};

	/* *************************************************************** */
	/* **                     subparts initialization		        ** */
	/* *************************************************************** */
	public Discoverer() {
		this.offeringManager = new OfferingManager();
		this.crawlerManager = new CrawlerManager();
	}


	/* *************************************************************** */
	/* **                       PUBLIC UTILS                        ** */
	/* *************************************************************** */
	public void emptyRepository() {
		offeringManager.emptyRepository();
	}


	/* *************************************************************** */
	/* **                   INTERFACE IMPLEMENTATION                ** */
	/* *************************************************************** */
	/**
	 * Reads an offering from the local repository.
	 * @param cloudOfferingId The ID of the offering to read.
	 * @return The <code>Offering</code> object instance for the fetchOffer'ed ID.
	 */
	public Offering fetchOffer(String cloudOfferingId) {
		Offering offering;
		try {
			offering = offeringManager.getOffering(cloudOfferingId);
		} catch(Exception ex) {
			ex.printStackTrace();
            offering = null;
		}

		return offering;
	}

    /**
     * Inserts or eventually updates an offering in the local repository
     *
     * @param newOffering The <code>Offering</code> object instance representing the new offering to insert.
     * @return the ID assigned to the newly-inserted offering.
     */
	public String addOffering(Offering newOffering) {
		String offeringId = offeringManager.getOfferingId(newOffering.getName());

        /* The offering was already present in the repository */
		if (offeringId != null) {
            Offering oldOffering = offeringManager.getOffering(offeringId);

            /* In case we are updating an already present offering */
            if (newOffering.moreRecent(oldOffering)) {
                offeringManager.removeOffering(offeringId);
                offeringId = offeringManager.addOffering(newOffering);
            }
        } else { // new offering, not present yet
            offeringId = offeringManager.addOffering(newOffering);
        }

        /* updates the list of all node templates */
        this.offeringNodeTemplates.add(newOffering.getNodeTemplate());

		return offeringId;
	}

	/**
	 * Removes an offering from the repository.
	 * @param cloudOfferingId The ID of the offering to remove.
	 * @return <code>true</code> in case of successful removal;
	 * <code>false</code> otherwise.
	 */
	public boolean removeOffering(String cloudOfferingId) {
		return Offering.validateOfferingId(cloudOfferingId) && offeringManager.removeOffering(cloudOfferingId);
	}

	/**
	 * Gets an iterator for the content of the offering repository.
	 * @return Iterator of cloud offering IDs that can be used as argument of
	 * <code>fetchOffer</code> method.
	 */
	public Collection<String> getAllOfferingIds() {
		return offeringManager.getAllOfferingIds();
	}

	public void initializeOfferings() {
		offeringManager.initializeOfferings();
	}

	public void generateSingleOffering() {
        String nodeTemplates = Offering.getPreamble() + "\n";

        for (String nodeTemplate : offeringNodeTemplates) {
            nodeTemplates += nodeTemplate + "\n";
        }

        this.offeringManager.generateSingleOffering(nodeTemplates);
        this.offeringNodeTemplates.clear();
	}

    public String getSingleOffering() {
        return this.offeringManager.getSingleOffering();
    }

	@Override
	public void run(DiscovererConfiguration configuration, Environment environment) {
		if (configuration.getCrawlOnStartup() == false) {
			this.initializeOfferings();
		} else {
			this.emptyRepository();
		}

		this.crawlerManager.startCrawling(
				configuration.getRefreshInterval(),
				configuration.getCrawlOnStartup(),
				configuration.getActiveCrawlers()
		);

		for (IAPI resource : APIList) {
			environment.jersey().register(resource);
		}
	}

	public static void main(String[] args) throws Exception {
		Discoverer.instance().run(args);
	}
}

