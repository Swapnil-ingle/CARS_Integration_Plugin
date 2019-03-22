package com.krishagni.openspecimen.msk2.importer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolEventDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolSiteDetail;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenRequirementDetail;

public class CarsCpDetail {
	private String cpShortTitle;
	
	private String cpTitle;
	
	private String piAddress;
	
	private CollectionProtocolDetail cp;
	
	private List<CollectionProtocolSiteDetail> sites = new ArrayList<>();
	
	private List<CollectionProtocolEventDetail> events = new ArrayList<>();
	
	private List<SpecimenRequirementDetail> specmnReqs = new ArrayList<>();
	
	public CarsCpDetail from(CollectionProtocolDetail cp, 
			CollectionProtocolEventDetail event, 
			SpecimenRequirementDetail sr) {
		if (StringUtils.isBlank(this.cpShortTitle)) {
			setCpDetails(cp);
			this.setCp(cp);
		}
		addEvent(event);
		this.specmnReqs.add(sr);

		return this;
	}

	private void addEvent(CollectionProtocolEventDetail event) {
		if (eventExists(event)) {
			return;
		}
		this.events.add(event);
	}

	private boolean eventExists(CollectionProtocolEventDetail input) {
		return this.events.stream()
				.anyMatch(
					event -> StringUtils.equals(event.getEventLabel(), input.getEventLabel())
				);
	}

	private void setCpDetails(CollectionProtocolDetail cp) {
		this.cpShortTitle = cp.getShortTitle();
		this.cpTitle = cp.getTitle();
		this.setSites(cp.getCpSites());
		this.piAddress = cp.getPrincipalInvestigator().getEmailAddress();
	}

	public String getCpShortTitle() {
		return cpShortTitle;
	}

	public void setCpShortTitle(String cpShortTitle) {
		this.cpShortTitle = cpShortTitle;
	}

	public String getCpTitle() {
		return cpTitle;
	}

	public void setCpTitle(String cpTitle) {
		this.cpTitle = cpTitle;
	}

	public String getPiAddress() {
		return piAddress;
	}

	public void setPiAddress(String piAddress) {
		this.piAddress = piAddress;
	}

	public List<CollectionProtocolSiteDetail> getSites() {
		return sites;
	}

	public void setSites(List<CollectionProtocolSiteDetail> sites) {
		this.sites = sites;
	}
	
	public List<CollectionProtocolEventDetail> getEvents() {
		return events;
	}

	public void setEvents(List<CollectionProtocolEventDetail> events) {
		this.events = events;
	}

	public List<SpecimenRequirementDetail> getSpecmnReqs() {
		return specmnReqs;
	}

	public void setSpecmnReqs(List<SpecimenRequirementDetail> specmnReqs) {
		this.specmnReqs = specmnReqs;
	}

	public CollectionProtocolDetail getCp() {
		return cp;
	}

	public void setCp(CollectionProtocolDetail cp) {
		this.cp = cp;
	}
}
