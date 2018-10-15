package com.krishagni.openspecimen.msk2.importer;

import org.apache.commons.lang3.StringUtils;

import com.krishagni.catissueplus.core.common.errors.ErrorCode;
import com.krishagni.catissueplus.core.common.errors.OpenSpecimenException;
import com.krishagni.catissueplus.core.common.util.ConfigUtil;

public class ConfigParams {
	private static String MODULE = "cars";
	
	private static String URL = "cars_db_url";
	
	private static String USERNAME = "cars_db_username";
	
	private static String PASSWORD = "cars_db_password";
	
	public static String getUrl() {
		return getValue(URL, CarsError.DB_URL_NOT_SPECIFIED);
	}
	
	public static String getUsername() {
		return getValue(USERNAME, CarsError.DB_USER_NOT_SPECIFIED);
	}
	
	public static String getPassword() {
		return getValue(PASSWORD, CarsError.DB_PASSWD_NOT_SPECIFIED);
	}
	
	private static String getValue(String propName, ErrorCode errorCode) {
 		String value = ConfigUtil.getInstance().getStrSetting(MODULE, propName, null);
 		return ensureNotBlank(value, errorCode);
 	}
 
 	private static String ensureNotBlank(String value, ErrorCode code) {
 		if (StringUtils.isBlank(value)) {
 			throw OpenSpecimenException.userError(code);
 		}
 
 		return value;
 	}
}
