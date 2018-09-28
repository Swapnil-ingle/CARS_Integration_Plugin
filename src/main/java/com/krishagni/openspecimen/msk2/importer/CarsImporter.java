package com.krishagni.openspecimen.msk2.importer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocolEvent;
import com.krishagni.catissueplus.core.biospecimen.domain.SpecimenRequirement;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolEventDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolSiteDetail;
import com.krishagni.catissueplus.core.biospecimen.events.SpecimenRequirementDetail;
import com.krishagni.catissueplus.core.biospecimen.repository.DaoFactory;
import com.krishagni.catissueplus.core.biospecimen.services.CollectionProtocolService;
import com.krishagni.catissueplus.core.common.PlusTransactional;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.events.RequestEvent;
import com.krishagni.catissueplus.core.common.events.ResponseEvent;
import com.krishagni.catissueplus.core.common.events.UserSummary;
import com.krishagni.catissueplus.core.importer.events.ImportObjectDetail;
import com.krishagni.catissueplus.core.importer.services.ObjectImporter;

@Configurable
public class CarsImporter implements ObjectImporter<CarsDetail, CarsDetail> {
	
	private final static Log logger = LogFactory.getLog(CarsImporter.class);
	
	@Autowired
	private CollectionProtocolService cpSvc;
	
	@Autowired
	private DaoFactory daoFactory;
	
	private Map<String, CollectionProtocolDetail> cpMap = new HashMap<>();
	
	private Map<String, CollectionProtocolEventDetail> eventsMap = new HashMap<>();
	
	private Map<String, SpecimenRequirementDetail> srMap = new HashMap<>();
	
	@Override
	public ResponseEvent<CarsDetail> importObject(RequestEvent<ImportObjectDetail<CarsDetail>> req) {
		try {
			ImportObjectDetail<CarsDetail> detail = req.getPayload();
			importRecord(detail);
			return ResponseEvent.response(detail.getObject());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
	
	@PlusTransactional
	private ResponseEvent<Object> importRecord(ImportObjectDetail<CarsDetail> detail) throws Exception {
		CollectionProtocolEventDetail event = toEvent(detail.getObject());
		
		////////////////
		// Checking CP
		////////////////
		
		CollectionProtocolDetail cpDetail = cpMap.get(event.getCpShortTitle());
		
		if (cpDetail == null) {
			// Not seen in this run
			if (getCpFromDB(event.getCpShortTitle()) == null) {
				// Also not in DB
		        cpDetail = createCp(detail.getObject());
			}
			cpMap.put(event.getCpShortTitle(), cpDetail);
		}
		
		////////////////
		// Checking Event
		////////////////
		
		CollectionProtocolEventDetail eventDetail = eventsMap.get(event.getEventLabel());
		
		if (eventDetail == null) {
			// Not seen in this run
			// Try getting from DB
			CollectionProtocolEvent eventFromDb = getEventFromDb(event.getCpShortTitle(), event.getEventLabel());
			eventDetail = eventFromDb != null ? CollectionProtocolEventDetail.from(eventFromDb) : null;
		}
		
		if (eventDetail != null) {
			// Present in DB
			updateEvent(cpDetail, eventDetail, event);
		} else {
			// Not in DB also so create new
			eventDetail = createEvent(cpDetail, event);
		}
		
		eventsMap.put(event.getEventLabel(), eventDetail);
		
		//////////////////////////////
		// Checking Specimen Requirement
		//////////////////////////////
		
		SpecimenRequirementDetail srDetail = srMap.get(event.getSpecimenRequirements().get(0).getName());
		
		if (srDetail == null) {
			// Not seen in this run
			// Try getting from DB
			SpecimenRequirement srFromDb = getSrFromDb(event.getCpShortTitle(),
								event.getEventLabel(), 
								event.getSpecimenRequirements().get(0).getName());
			
			srDetail = srFromDb != null ? SpecimenRequirementDetail.from(srFromDb) : null;
		}
		
		if (srDetail != null) {
			// Present in DB
			updateSr();
		} else {
			// Not in DB also so create new SR
			srDetail = createSr();
		}
		
		srMap.put(event.getSpecimenRequirements().get(0).getName(), srDetail);
		
		//////////////////
		// For Testing
		/////////////////
		
		logger.info(" Printing Maps... \n");
		logger.info(eventsMap + "\n");
		logger.info(cpMap + "\n");
		logger.info(srMap + "\n");
		
		return null;
	}
	
	private CollectionProtocolEventDetail toEvent(CarsDetail detail) {
		CollectionProtocolEventDetail eventDetail = new CollectionProtocolEventDetail();
		SpecimenRequirementDetail srDetail = new SpecimenRequirementDetail();
		
		eventDetail.setCpShortTitle(detail.getIrbNumber());
		eventDetail.setEventLabel(detail.getCycleName() + detail.getTimepointName());
		srDetail.setName(detail.getProcedureName());
		eventDetail.setSpecimenRequirements(Arrays.asList(srDetail));
		
		return eventDetail;
	}

	private <T> RequestEvent<T> request(T payload) {
		return new RequestEvent<T>(payload);
	}
	
	//////////////////////////
	//
	// Specimen Requirement
	//
	/////////////////////////
	
	private SpecimenRequirement getSrFromDb(String cpShortTitle, String eventLabel, String srName) {
		CollectionProtocolEvent cpe = daoFactory.getCollectionProtocolDao().getCpeByShortTitleAndEventLabel(cpShortTitle, eventLabel);
		
		Set<SpecimenRequirement> specimenRequirements = cpe != null ? cpe.getSpecimenRequirements() : Collections.emptySet();
		
		//
		// Currently assuming each CP -> Event -> Has only one SR with a unique name
		//
		
		for (SpecimenRequirement req : specimenRequirements) {
			if (req.getName() == srName) {
				return req;
			}
		}
		
		return null;
	}
	
	private SpecimenRequirementDetail createSr() {
		return null;
	}

	private void updateSr() {
		
	}
	
	////////////////////////////////
	//
	// Collection Protocol Event
	//
	///////////////////////////////

	private CollectionProtocolEvent getEventFromDb(String cpShortTitle, String eventLabel) {
		return daoFactory.getCollectionProtocolDao().getCpeByShortTitleAndEventLabel(cpShortTitle, eventLabel);
	}
	
	private CollectionProtocolEventDetail createEvent(CollectionProtocolDetail cpDetail,
			CollectionProtocolEventDetail event) {
		return null;
	}

	private void updateEvent(CollectionProtocolDetail cpDetail, CollectionProtocolEventDetail eventDetail,
			CollectionProtocolEventDetail event) {
	}
	
	//////////////////////////
	//
	// Collection Protocol
	//
	/////////////////////////

	private CollectionProtocol getCpFromDB(String cpShortTitle) {
		return daoFactory.getCollectionProtocolDao().getCpByShortTitle(cpShortTitle);
	}

	private CollectionProtocolDetail createCp(CarsDetail input) throws Exception {
		CollectionProtocolDetail cpDetail = new CollectionProtocolDetail();
		cpDetail.setTitle(input.getIrbNumber());
		cpDetail.setShortTitle(input.getIrbNumber());
		setCpSites(input, cpDetail);
		setCpPI(input, cpDetail);
		
		ResponseEvent<CollectionProtocolDetail> resp = cpSvc.createCollectionProtocol(request(cpDetail));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	private void setCpPI(CarsDetail input, CollectionProtocolDetail cpDetail) {
		UserSummary pi = new UserSummary();
		pi.setEmailAddress(input.getPi());
		cpDetail.setPrincipalInvestigator(pi);
	}

	private void setCpSites(CarsDetail input, CollectionProtocolDetail cpDetail) {
		CollectionProtocolSiteDetail cpSite = new CollectionProtocolSiteDetail();
		cpSite.setSiteName(input.getFacility());
		
		cpDetail.setCpSites(Arrays.asList(cpSite));
	}
}
