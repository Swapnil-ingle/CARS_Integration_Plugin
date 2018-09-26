package com.krishagni.openspecimen.msk2.importer;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolDetail;
import com.krishagni.catissueplus.core.biospecimen.events.CollectionProtocolRegistrationDetail;
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
public class MskCARSImporter implements ObjectImporter<MskCARSDetail, MskCARSDetail> {
	
	@Autowired
	private CollectionProtocolService cpSvc;
	
	@Override
	public ResponseEvent<MskCARSDetail> importObject(RequestEvent<ImportObjectDetail<MskCARSDetail>> req) {
		try {
			ImportObjectDetail<MskCARSDetail> detail = req.getPayload();
			importRecord(detail);
			return ResponseEvent.response(detail.getObject());
		} catch (OpenSpecimenException ose) {
			return ResponseEvent.error(ose);
		} catch (Exception e) {
			return ResponseEvent.serverError(e);
		}
	}
	
	@PlusTransactional
	private ResponseEvent<Object> importRecord(ImportObjectDetail<MskCARSDetail> detail) throws Exception {
		importCp(detail.getObject());
		
		return null;
	}
				
	private ResponseEvent<CollectionProtocolRegistrationDetail> importCp(MskCARSDetail object) throws Exception {
		CollectionProtocolDetail cpDetail = new CollectionProtocolDetail();
		cpDetail.setTitle(object.getCpID());
		cpDetail.setShortTitle(object.getCpID());
		setCpSites(object, cpDetail);
		setCpPI(object, cpDetail);
		
		ResponseEvent<CollectionProtocolDetail> resp = cpSvc.createCollectionProtocol(request(cpDetail));
		resp.throwErrorIfUnsuccessful();
		
		return null;
	}

	private void setCpPI(MskCARSDetail object, CollectionProtocolDetail cpDetail) {
		UserSummary pi = new UserSummary();
		pi.setEmailAddress(object.getCpPI());
		cpDetail.setPrincipalInvestigator(pi);
	}

	private void setCpSites(MskCARSDetail object, CollectionProtocolDetail cpDetail) {
		List<CollectionProtocolSiteDetail> sites = new ArrayList<CollectionProtocolSiteDetail>();
		CollectionProtocolSiteDetail cpSite = new CollectionProtocolSiteDetail();
	
		cpSite.setSiteName(object.getCpSite());
		sites.add(cpSite);
		
		cpDetail.setCpSites(sites);
	}

	private <T> RequestEvent<T> request(T payload) {
		return new RequestEvent<T>(payload);
	}
}
