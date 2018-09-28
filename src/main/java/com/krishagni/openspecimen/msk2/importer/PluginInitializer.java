package com.krishagni.openspecimen.msk2.importer;

import org.springframework.beans.factory.InitializingBean;

import com.krishagni.catissueplus.core.importer.services.ObjectImporterFactory;
import com.krishagni.catissueplus.core.importer.services.ObjectSchemaFactory;

public class PluginInitializer implements InitializingBean {
	
	private ObjectSchemaFactory objectSchemaFactory;
	
	private ObjectImporterFactory objectImporterFactory;
	
	public void setObjectImporterFactory(ObjectImporterFactory objectImporterFactory) {
		this.objectImporterFactory = objectImporterFactory;
	}

	public void setObjectSchemaFactory(ObjectSchemaFactory objectSchemaFactory) {
		this.objectSchemaFactory = objectSchemaFactory;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		objectSchemaFactory.registerSchema("com/krishagni/openspecimen/msk2/schema/msk-cars.xml");
		objectImporterFactory.registerImporter("msk_cars", new CarsImporter());
	}
}