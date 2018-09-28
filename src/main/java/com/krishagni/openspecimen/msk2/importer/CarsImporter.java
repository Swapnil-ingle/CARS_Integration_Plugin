package com.krishagni.openspecimen.msk2.importer;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolSiteDetail;
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
	
	@Autowired
	private CollectionProtocolService cpSvc;
	
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
		importCp(detail.getObject());
		
		return null;
	}
				
	private CollectionProtocolDetail importCp(CarsDetail input) throws Exception {
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

	private <T> RequestEvent<T> request(T payload) {
		return new RequestEvent<T>(payload);
	}
}