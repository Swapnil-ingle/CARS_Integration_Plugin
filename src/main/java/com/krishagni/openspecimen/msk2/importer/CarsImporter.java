package com.krishagni.openspecimen.msk2.importer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.administrative.events.ListPvCriteria;
import com.krishagni.catissueplus.core.administrative.events.PvDetail;
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
import com.krishagni.catissueplus.core.common.service.PermissibleValueService;
import com.krishagni.catissueplus.core.importer.events.ImportObjectDetail;
import com.krishagni.catissueplus.core.importer.services.ObjectImporter;

@Configurable
public class CarsImporter implements ObjectImporter<CarsDetail, CarsDetail> {
	
	@Autowired
	private CollectionProtocolService cpSvc;
	
	@Autowired
	private PermissibleValueService pvSvc;
	
	@Autowired
	private DaoFactory daoFactory;
	
	private Map<String, CollectionProtocolDetail> cpMap = new HashMap<>();
	
	private Map<String, CollectionProtocolEventDetail> eventsMap = new HashMap<>();
	
	private Map<String, SpecimenRequirementDetail> srMap = new HashMap<>();
	
	private final static String SPECIMEN_STORAGE_TYPE_VIRTUAL = "Virtual";
	
	private final static String NOT_SPECIFIED = "Not Specified";
	
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
		CollectionProtocolDetail cp = toCp(detail.getObject());
		CollectionProtocolEventDetail event = toEvent(detail.getObject());
		SpecimenRequirementDetail sr = toSr(detail.getObject());
		
		getCp(cp);
		getEvent(event);
		getSr(sr);
		
		return null;
	}
	
	private <T> RequestEvent<T> request(T payload) {
		return new RequestEvent<T>(payload);
	}
	
	//////////////////////////
	//
	// Collection Protocol
	//
	/////////////////////////
	
	private CollectionProtocolDetail toCp(CarsDetail input) {
		CollectionProtocolDetail cp = new CollectionProtocolDetail();
		
		cp.setTitle(input.getIrbNumber());
		cp.setShortTitle(input.getIrbNumber());
		setCpSites(input, cp);
		setCpPI(input, cp);
		
		return cp;
	}
	
	private void setCpSites(CarsDetail input, CollectionProtocolDetail cpDetail) {
		CollectionProtocolSiteDetail cpSite = new CollectionProtocolSiteDetail();
		cpSite.setSiteName(input.getFacility());
		
		cpDetail.setCpSites(Arrays.asList(cpSite));
	}
	
	private void setCpPI(CarsDetail input, CollectionProtocolDetail cpDetail) {
		UserSummary pi = new UserSummary();
		pi.setEmailAddress(input.getPi());
		cpDetail.setPrincipalInvestigator(pi);
	}

	private CollectionProtocolDetail getCp(CollectionProtocolDetail input) throws Exception {
		return cpMap.computeIfAbsent(input.getShortTitle(), k -> createIfAbsent(input));
	}
	
	private CollectionProtocolDetail createIfAbsent(CollectionProtocolDetail input) throws OpenSpecimenException {
		CollectionProtocolDetail dbCp = getCpFromDB(input.getShortTitle());
		return dbCp != null ? dbCp : createCp(input);
	}

	private CollectionProtocolDetail getCpFromDB(String cpShortTitle) {
		CollectionProtocol cp = daoFactory.getCollectionProtocolDao().getCpByShortTitle(cpShortTitle);
		return cp != null ? CollectionProtocolDetail.from(cp) : null;
	}

	private CollectionProtocolDetail createCp(CollectionProtocolDetail cp) throws OpenSpecimenException {
		ResponseEvent<CollectionProtocolDetail> resp = cpSvc.createCollectionProtocol(request(cp));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	////////////////////////////////
	//
	// Collection Protocol Event
	//
	///////////////////////////////
	
	private CollectionProtocolEventDetail toEvent(CarsDetail input) {
		CollectionProtocolEventDetail eventDetail = new CollectionProtocolEventDetail();
		eventDetail.setCpShortTitle(input.getIrbNumber());
		setEventLabel(eventDetail, input);
		
		return eventDetail;
	}
	
	private void setEventLabel(CollectionProtocolEventDetail eventDetail, CarsDetail input) {
		if (input.getCycleName() == null && input.getTimepointName() == null) {
			eventDetail.setEventLabel(null);
			return;
		}
		eventDetail.setEventLabel(input.getCycleName() + input.getTimepointName());
	}

	private void getEvent(CollectionProtocolEventDetail input) throws Exception {
		CollectionProtocolEventDetail eventDetail = eventsMap.get(input.getEventLabel());
		
		if (eventDetail == null) {
			CollectionProtocolEvent eventFromDb = getEventFromDb(input.getCpShortTitle(), input.getEventLabel());
			eventDetail = eventFromDb != null ? CollectionProtocolEventDetail.from(eventFromDb) : null;
		}
		
		if (eventDetail != null) {
			updateEvent(eventDetail, input);
		} else {
			eventDetail = createEvent(input);
		}
		
		eventsMap.put(input.getEventLabel(), eventDetail);
	}

	private CollectionProtocolEvent getEventFromDb(String cpShortTitle, String eventLabel) {
		return daoFactory.getCollectionProtocolDao().getCpeByShortTitleAndEventLabel(cpShortTitle, eventLabel);
	}
	
	private void updateEvent(CollectionProtocolEventDetail existingEvent, CollectionProtocolEventDetail newEvent) {
		// This Scenario is not yet handled
	}
	
	private CollectionProtocolEventDetail createEvent(CollectionProtocolEventDetail input) throws Exception {
		ResponseEvent<CollectionProtocolEventDetail> resp = cpSvc.addEvent(request(input));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	//////////////////////////
	//
	// Specimen Requirement
	//
	/////////////////////////
	
	private SpecimenRequirementDetail toSr(CarsDetail input) {
		SpecimenRequirementDetail sr = new SpecimenRequirementDetail();
		
		sr.setCpShortTitle(input.getIrbNumber());
		sr.setEventLabel(input.getCycleName() + input.getTimepointName());
		sr.setName(input.getProcedureName());
		sr.setType(input.getSpecimenType());
		sr.setSpecimenClass(getSpecimenClass(input.getSpecimenType()));
		sr.setStorageType(SPECIMEN_STORAGE_TYPE_VIRTUAL);
		sr.setAnatomicSite(NOT_SPECIFIED);
		sr.setLaterality(NOT_SPECIFIED);
		sr.setPathology(NOT_SPECIFIED);
		sr.setCollectionProcedure(NOT_SPECIFIED);
		sr.setEventLabel(input.getCycleName() + input.getTimepointName());
		sr.setInitialQty(new BigDecimal(0));
		sr.setCollectionContainer(input.getCollectionContainer());
		
		return sr;
	}
	
	private String getSpecimenClass(String specimenType) {
		ListPvCriteria crit = new ListPvCriteria()
				.includeParentValue(true)
				.parentAttribute("specimen_type")
				.values(Collections.singletonList(specimenType));
		
		ResponseEvent<List<PvDetail>> resp = pvSvc.getPermissibleValues(request(crit));
		List<PvDetail> payload = resp.getPayload();
		
		return payload.isEmpty() ? "" : payload.iterator().next().getParentValue();
	}
	
	private void getSr(SpecimenRequirementDetail input) throws Exception {
		SpecimenRequirementDetail srDetail = srMap.get(input.getName());
		
		if (srDetail == null) {
			SpecimenRequirement srFromDb = getSrFromDb(input);
			srDetail = srFromDb != null ? SpecimenRequirementDetail.from(srFromDb) : null;
		}
		
		if (srDetail != null) {
			updateSr(srDetail, input);
		} else {
			srDetail = createSr(input);
		}
		
		srMap.put(input.getName(), srDetail);
	}
	
	private SpecimenRequirement getSrFromDb(SpecimenRequirementDetail sr) {
		CollectionProtocolEvent cpe = daoFactory
				.getCollectionProtocolDao()
				.getCpeByShortTitleAndEventLabel(sr.getCpShortTitle(), sr.getEventLabel());
		
		Set<SpecimenRequirement> specimenRequirements = cpe != null ? cpe.getTopLevelAnticipatedSpecimens() : Collections.emptySet();
		
		//
		// Currently assuming each CP -> Event -> Has only one SR with a unique name
		//
		
		String srName = sr.getName();
		return specimenRequirements.stream()
				.filter(req -> req.getName().equalsIgnoreCase(srName))
				.findFirst().orElse(null);
	}
	
	private SpecimenRequirementDetail updateSr(SpecimenRequirementDetail exisitingSr, SpecimenRequirementDetail newSr) throws Exception {
		newSr.setId(exisitingSr.getId());
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.updateSpecimenRequirement(request(newSr));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}
	
	private SpecimenRequirementDetail createSr(SpecimenRequirementDetail input) throws Exception {
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.addSpecimenRequirement(request(input));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}
}
