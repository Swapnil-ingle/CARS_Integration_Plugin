package com.krishagni.openspecimen.msk2.importer;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.administrative.domain.ScheduledJobRun;
import com.krishagni.catissueplus.core.administrative.events.ListPvCriteria;
import com.krishagni.catissueplus.core.administrative.events.PvDetail;
import com.krishagni.catissueplus.core.administrative.services.ScheduledTask;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocol;
import com.krishagni.catissueplus.core.biospecimen.domain.CollectionProtocolEvent;
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

@Configurable
public class CarsImporter implements ScheduledTask {
	private final static Log logger = LogFactory.getLog(CarsImporter.class);
	
	@Autowired
	private CollectionProtocolService cpSvc;
	
	@Autowired
	private PermissibleValueService pvSvc;
	
	@Autowired
	private DaoFactory daoFactory;
	
	private final static String SPECIMEN_STORAGE_TYPE_VIRTUAL = "Virtual";
	
	private final static String NOT_SPECIFIED = "Not Specified";
	
	@Override
	public void doJob(ScheduledJobRun jobRun) throws Exception {
		CarsReader carsReader = new CarsReader();
		try {
			while (carsReader.hasDistinctCp()) {
				importRecords(carsReader);
			}
		} catch (Exception e) {
			logger.error(e);
		} finally {
			carsReader.close();
		}
	}
	
	private void importRecords(CarsReader carsReader) throws Exception {
		try {
			CarsCpDetail carsCpDetail = getCarsCpDetail(carsReader);
			processCpDiff(carsCpDetail);
		} catch (OpenSpecimenException ose) {
			logger.error(ose);
		}
	}
	
	private CarsCpDetail getCarsCpDetail(CarsReader carsReader) {
		CarsCpDetail carsCpDetail = new CarsCpDetail();
		
		while (carsReader.hasCp()) {
			CarsDetail carsDetail = carsReader.nextCp();
			
			CollectionProtocolDetail cp = toCp(carsDetail);
			CollectionProtocolEventDetail event = toEvent(carsDetail);
			SpecimenRequirementDetail sr = toSr(carsDetail);
			
			carsCpDetail = carsCpDetail.from(cp, event, sr);
		}
		return carsCpDetail;
	}
	
	@PlusTransactional
	private void processCpDiff(CarsCpDetail carsCpDetail) throws Exception {
		CollectionProtocolDetail osCp = getCpFromDB(carsCpDetail.getCpShortTitle());
		
		if (osCp == null) {
			createCp(carsCpDetail);
			return;
		}
		
		List<CollectionProtocolEvent> existingEvents = getEventsFromDb(osCp.getShortTitle());
		
		processEvents(eventDetailListFrom(existingEvents), carsCpDetail.getEvents());
		processSr(getSpecmnReqFromEvents(existingEvents), carsCpDetail.getSpecmnReqs());
	}
	
	private void processEvents(List<CollectionProtocolEventDetail> existingEvents,
			List<CollectionProtocolEventDetail> newEvents) throws OpenSpecimenException {
		List<String> existingEventLabels = nullableListToStream(existingEvents)
				.map(CollectionProtocolEventDetail::getEventLabel)
				.collect(Collectors.toList());
		
		List<String> newEventLabels = nullableListToStream(newEvents)
				.map(CollectionProtocolEventDetail::getEventLabel)
				.collect(Collectors.toList());
		
		List<CollectionProtocolEventDetail> eventsAdded = eventsAdded(existingEventLabels, newEvents);
		List<CollectionProtocolEventDetail> eventsUpdated = eventsUpdated(existingEventLabels, newEvents);
		List<CollectionProtocolEventDetail> eventsDeleted = eventsDeleted(existingEvents, newEventLabels);
		
		eventsAdded.forEach(event -> createEvent(event));
		eventsUpdated.forEach(event -> updateEvent(event)); 
		eventsDeleted.forEach(event -> deleteEvent(event));
	}
	
	private void processSr(List<SpecimenRequirementDetail> existingSpecmnReqs, 
			List<SpecimenRequirementDetail> newSpecmnReqs) throws OpenSpecimenException {
		List<String> existingSrNames = getSrNames(existingSpecmnReqs);
		List<String> newSrNames = getSrNames(newSpecmnReqs);
		
		List<SpecimenRequirementDetail> srAdded = addedSrs(existingSrNames, newSpecmnReqs);
		List<SpecimenRequirementDetail> srUpdated = updatedSrs(existingSrNames, newSpecmnReqs);
		List<SpecimenRequirementDetail> srDeleted = deletedSrs(existingSpecmnReqs, newSrNames);
		
		srAdded.forEach(sr -> createSr(sr));
		srUpdated.forEach(sr -> updateSr(existingSpecmnReqs, sr));
		srDeleted.forEach(sr -> deleteSr(sr));
	}
	
	private static <T> Stream<T> nullableListToStream(List<T> list) {
	    return list == null ? Stream.empty() : list.stream();
	}
	
	private static <T> Stream<T> nullableListToStream(Set<T> set) {
	    return set == null ? Stream.empty() : set.stream();
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
	
	private void createCp(CarsCpDetail carsCpDetail) throws Exception {
		createCp(carsCpDetail.getCp());
		carsCpDetail.getEvents().forEach(event -> createEvent(event));
		carsCpDetail.getSpecmnReqs().forEach(sr -> createSr(sr));
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
	
	private List<CollectionProtocolEvent> getEventsFromDb(String cpShortTitle) {
		return daoFactory.getCollectionProtocolDao().getCpByShortTitle(cpShortTitle).getOrderedCpeList();
	}
	
	private List<CollectionProtocolEventDetail> eventDetailListFrom(List<CollectionProtocolEvent> events) {
		return nullableListToStream(events)
				.map(CollectionProtocolEventDetail::from)
				.collect(Collectors.toList());
	}
	
	private List<CollectionProtocolEventDetail> eventsAdded(List<String> existingEventLabels,
			List<CollectionProtocolEventDetail> newEvents) {
		return nullableListToStream(newEvents)
				.filter(newEvent -> !existingEventLabels.contains(newEvent.getEventLabel()))
				.collect(Collectors.toList());
	}
	
	private List<CollectionProtocolEventDetail> eventsUpdated(List<String> existingEventLabels,
			List<CollectionProtocolEventDetail> newEvents) {
		return nullableListToStream(newEvents)
				.filter(newEvent -> existingEventLabels.contains(newEvent.getEventLabel()))
				.collect(Collectors.toList());
	}
	
	private List<CollectionProtocolEventDetail> eventsDeleted(List<CollectionProtocolEventDetail> existingEvents,
			List<String> newEventLabels) {
		return nullableListToStream(existingEvents)
				.filter(existingEvent -> !newEventLabels.contains(existingEvent.getEventLabel()))
				.collect(Collectors.toList());
	}
	
	private CollectionProtocolEventDetail createEvent(CollectionProtocolEventDetail input) throws OpenSpecimenException {
		ResponseEvent<CollectionProtocolEventDetail> resp = cpSvc.addEvent(request(input));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}

	private void updateEvent(CollectionProtocolEventDetail newEvent) {
		// This Scenario is not yet handled
	}
	
	private Object deleteEvent(CollectionProtocolEventDetail event) throws OpenSpecimenException {
		ResponseEvent<CollectionProtocolEventDetail> resp = cpSvc.deleteEvent(request(event.getId()));
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
	
	private List<String> getSrNames(List<SpecimenRequirementDetail> specmnReq) {
		return nullableListToStream(specmnReq)
				.map(SpecimenRequirementDetail::getName)
				.collect(Collectors.toList());
	}
	
	private List<SpecimenRequirementDetail> addedSrs(List<String> existingSrNames, 
			List<SpecimenRequirementDetail> newSpecmnReqs) {
		return nullableListToStream(newSpecmnReqs)
				.filter(newSr -> !existingSrNames.contains(newSr.getName()))
				.collect(Collectors.toList());
	}
	
	private List<SpecimenRequirementDetail> updatedSrs(List<String> existingSrNames,
			List<SpecimenRequirementDetail> newSpecmnReqs) {
		return nullableListToStream(newSpecmnReqs)
				.filter(newSr -> existingSrNames.contains(newSr.getName()))
				.collect(Collectors.toList());
	}

	private List<SpecimenRequirementDetail> deletedSrs(List<SpecimenRequirementDetail> existingSpecmnReqs,
			List<String> newSrNames) {
		return nullableListToStream(existingSpecmnReqs)
				.filter(existingSr -> !newSrNames.contains(existingSr.getName()))
				.collect(Collectors.toList());
	}
	
	private SpecimenRequirementDetail createSr(SpecimenRequirementDetail input) throws OpenSpecimenException {
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.addSpecimenRequirement(request(input));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}
	
	private void updateSr(List<SpecimenRequirementDetail> existingSpecmnReqs, 
			SpecimenRequirementDetail sr) throws OpenSpecimenException {
		SpecimenRequirementDetail existingSr = existingSpecmnReqs.stream()
				.filter(osSr -> StringUtils.equals(osSr.getName(), sr.getName()))
				.findFirst().get();
		
		updateSr(existingSr, sr);
	}
	
	private SpecimenRequirementDetail updateSr(SpecimenRequirementDetail exisitingSr, 
			SpecimenRequirementDetail newSr) throws OpenSpecimenException {
		newSr.setId(exisitingSr.getId());
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.updateSpecimenRequirement(request(newSr));
		resp.throwErrorIfUnsuccessful();
		
		return resp.getPayload();
	}
	
	private SpecimenRequirementDetail deleteSr(SpecimenRequirementDetail sr) {
		if (eventAlreadyDeleted(sr.getEventId())) {
			return null;
		}
		
		ResponseEvent<SpecimenRequirementDetail> resp = cpSvc.deleteSpecimenRequirement(request(sr.getId()));
		resp.throwErrorIfUnsuccessful();
		return resp.getPayload();
	}
	
	private boolean eventAlreadyDeleted(Long eventId) {
		return daoFactory.getCollectionProtocolDao().getCpe(eventId) == null ? true : false;
	}
	
	private List<SpecimenRequirementDetail> getSpecmnReqFromEvents(List<CollectionProtocolEvent> events) {
		return nullableListToStream(events)
				.flatMap(event -> nullableListToStream(event.getTopLevelAnticipatedSpecimens()))
				.map(SpecimenRequirementDetail::from)
				.collect(Collectors.toList());
	}
}
