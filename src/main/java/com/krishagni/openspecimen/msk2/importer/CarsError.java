package com.krishagni.openspecimen.msk2.importer;

import com.krishagni.catissueplus.core.common.errors.ErrorCode;

public enum CarsError implements ErrorCode {
	DB_URL_NOT_SPECIFIED,
	
	DB_USER_NOT_SPECIFIED,
	
	DB_PASSWD_NOT_SPECIFIED;
	
	@Override
	public String code() {
		return "CARS_" + name();
	}

}
