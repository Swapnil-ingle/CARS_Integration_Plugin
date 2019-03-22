package com.krishagni.openspecimen.msk2.importer;

import org.springframework.beans.factory.InitializingBean;

public class PluginInitializer implements InitializingBean {
	
	@Override
	public void afterPropertiesSet() throws Exception {
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	}
}