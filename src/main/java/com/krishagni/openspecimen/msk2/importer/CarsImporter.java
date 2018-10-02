package com.krishagni.openspecimen.msk2.importer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
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
		
		CollectionProtocolDetail cpDetail = checkCp(event, detail.getObject());
		checkEvent(cpDetail, event);
		checkSr(event);
		
		return null;
	}

	private CollectionProtocolEventDetail toEvent(CarsDetail detail) {
		CollectionProtocolEventDetail eventDetail = new CollectionProtocolEventDetail();
		SpecimenRequirementDetail srDetail = new SpecimenRequirementDetail();
		
		eventDetail.setCpShortTitle(detail.getIrbNumber());
		eventDetail.setEventLabel(detail.getCycleName() + detail.getTimepointName());
		
		srDetail.setCpShortTitle(detail.getIrbNumber());
		srDetail.setType(detail.getSpecimenType());
		srDetail.setStorageType(SPECIMEN_STORAGE_TYPE_VIRTUAL);
		srDetail.setAnatomicSite(NOT_SPECIFIED);
		srDetail.setLaterality(NOT_SPECIFIED);
		srDetail.setPathology(NOT_SPECIFIED);
		srDetail.setCollectionProcedure(NOT_SPECIFIED);
		srDetail.setEventLabel(detail.getCycleName() + detail.getTimepointName());
		srDetail.setName(detail.getProcedureName());
		srDetail.setInitialQty(new BigDecimal(0));
		srDetail.setCollectionContainer(detail.getCollectionContainer());
		srDetail.setSpecimenClass(getSpecimenClass(detail.getSpecimenType()));
		
		eventDetail.setSpecimenRequirements(Arrays.asList(srDetail));
		
		return eventDetail;
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

	private <T> RequestEvent<T> request(T payload) {
		return new RequestEvent<T>(payload);
	}
	
	//////////////////////////
	//
	// Specimen Requirement
	//
	/////////////////////////
	
	private void checkSr(CollectionProtocolEventDetail event) throws Exception {
		SpecimenRequirementDetail srDetail = srMap.get(event.getSpecimenRequirements().iterator().next().getName());
		
		if (srDetail == null) {
			SpecimenRequirement srFromDb = getSrFromDb(event);
			srDetail = srFromDb != null ? SpecimenRequirementDetail.from(srFromDb) : null;
		}
		
		if (srDetail != null) {
			updateSr(event.getSpecimenRequirements(), srDetail);
		} else {
			srDetail = createSr(event.getSpecimenRequirements());
		}
		
		srMap.put(event.getSpecimenRequirements().iterator().next().getName(), srDetail);
	}
	
	private SpecimenRequirement getSrFromDb(CollectionProtocolEventDetail event) {
		String srName = event.getSpecimenRequirements().iterator().next().getName();
		CollectionProtocolEvent cpe = daoFactory
				.getCollectionProtocolDao()
				.getCpeByShortTitleAndEventLabel(event.getCpShortTitle(), event.getEventLabel());
		
		Set<SpecimenRequirement> specimenRequirements = cpe != null ? cpe.getSpecimenRequirements() : Collections.emptySet();
		
		//
		// Currently assuming each CP -> Event -> Has only one SR with a unique name
		//
		
		for (SpecimenRequirement req : specimenRequirements) {
			if (StringUtils.equals(req.getName(), srName)) {
				return req;
			}
		}
		
		return null;
	}
	
	private SpecimenRequirementDetail createSr(List<SpecimenRequirementDetail> sprDetails) throws Exception {
		SpecimenRequirementDetail sprDetail = sprDetails.iterator().next();
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.addSpecimenRequirement(request(sprDetail));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	private SpecimenRequirementDetail updateSr(List<SpecimenRequirementDetail> srDetails, SpecimenRequirementDetail srFromDb) throws Exception {
		SpecimenRequirementDetail sprDetail = srDetails.iterator().next();
		sprDetail.setId(srFromDb.getId());
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.updateSpecimenRequirement(request(sprDetail));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}
	
	////////////////////////////////
	//
	// Collection Protocol Event
	//
	///////////////////////////////
	
	private void checkEvent(CollectionProtocolDetail cpDetail, CollectionProtocolEventDetail event) throws Exception {
		CollectionProtocolEventDetail eventDetail = eventsMap.get(event.getEventLabel());
		
		if (eventDetail == null) {
			CollectionProtocolEvent eventFromDb = getEventFromDb(event.getCpShortTitle(), event.getEventLabel());
			eventDetail = eventFromDb != null ? CollectionProtocolEventDetail.from(eventFromDb) : null;
		}
		
		if (eventDetail != null) {
			updateEvent(cpDetail, eventDetail, event);
		} else {
			eventDetail = createEvent(cpDetail, event);
		}
		
		eventsMap.put(event.getEventLabel(), eventDetail);
	}

	private CollectionProtocolEvent getEventFromDb(String cpShortTitle, String eventLabel) {
		return daoFactory.getCollectionProtocolDao().getCpeByShortTitleAndEventLabel(cpShortTitle, eventLabel);
	}
	
	private CollectionProtocolEventDetail createEvent(CollectionProtocolDetail cpDetail,
			CollectionProtocolEventDetail event) throws Exception {
		CollectionProtocolEventDetail eventDetail = new CollectionProtocolEventDetail();
		eventDetail.setCpShortTitle(cpDetail.getShortTitle());
		eventDetail.setEventLabel(event.getEventLabel());
		
		ResponseEvent<CollectionProtocolEventDetail> resp = cpSvc.addEvent(request(event));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	private void updateEvent(CollectionProtocolDetail cpDetail, CollectionProtocolEventDetail eventDetail,
			CollectionProtocolEventDetail event) {
		
	}
	
	//////////////////////////
	//
	// Collection Protocol
	//
	/////////////////////////
	
	private CollectionProtocolDetail checkCp(CollectionProtocolEventDetail event, CarsDetail object) throws Exception {
		CollectionProtocolDetail cpDetail = new CollectionProtocolDetail();
		cpDetail = cpMap.get(event.getCpShortTitle());
		
		if (cpDetail == null) {
			cpDetail = getCpFromDB(event.getCpShortTitle());
			if (cpDetail == null) {
		        	cpDetail = createCp(object);
			}
			cpMap.put(event.getCpShortTitle(), cpDetail);
		}
		
		return cpDetail;
	}
	
	private CollectionProtocolDetail getCpFromDB(String cpShortTitle) {
		CollectionProtocol cp = daoFactory.getCollectionProtocolDao().getCpByShortTitle(cpShortTitle);
		return cp != null ? CollectionProtocolDetail.from(cp) : null;
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
	
	private final static String SPECIMEN_STORAGE_TYPE_VIRTUAL = "Virtual";
	
	private final static String NOT_SPECIFIED = "Not Specified";
}
