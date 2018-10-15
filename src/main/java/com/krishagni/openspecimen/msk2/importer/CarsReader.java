package com.krishagni.openspecimen.msk2.importer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CarsReader {
	private SingleConnectionDataSource scds;
	
	private JdbcTemplate jdbcTemplate;
	
	private SqlRowSet rowSet;
	
	private SqlRowSet cpRowSet;
	
	private final static String SQL_GET_DISTINCT_IRBNUMBER = "SELECT DISTINCT(IRBNUMBER)\n" + 
			"FROM CARS_DETAILS\n" + 
			"WHERE TIMEPOINT_CR_DATE > (getdate() -1)\n" + 
			"  OR TIMEPOINT_UPDATE > (getdate() -1)\n" + 
			"  OR PROCEDURE_CR_DATE > (getdate() -1)\n" + 
			"  OR PROCEDURE_UPDATE > (getdate() -1);";
	
	public CarsReader() {
		this.scds = new SingleConnectionDataSource(ConfigParams.getUrl(), ConfigParams.getUsername(), ConfigParams.getPassword(), true);
		this.jdbcTemplate = new JdbcTemplate(scds);
		this.rowSet = jdbcTemplate.queryForRowSet(SQL_GET_DISTINCT_IRBNUMBER);
	}
	
	public boolean hasDistinctCp() {
		return rowSet.next() == true ? loadNextCpSet() : false;
	}
	
	public boolean loadNextCpSet() {
		this.cpRowSet = jdbcTemplate.queryForRowSet(getCpRowSetQuery(rowSet.getObject(1).toString()));
		return true;
	}
	
	private String getCpRowSetQuery(String IRBNUMBER) {
		return "SELECT * "
				+ "FROM CARS_DETAILS "
				+ "WHERE IRBNUMBER= '" +  IRBNUMBER + "' "
				+ "AND (TIMEPOINT_CR_DATE > (getdate() -1) "
				+ "OR TIMEPOINT_UPDATE > (getdate() -1) "
				+ "OR PROCEDURE_CR_DATE > (getdate() -1) "
				+ "OR PROCEDURE_UPDATE > (getdate() -1));";
	}
	
	public boolean hasCp() {
		return cpRowSet.next();
	}
	
	public CarsDetail nextCp() {
		ObjectMapper objMapper = new ObjectMapper();
		objMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		return objMapper.convertValue(getAttrValueMap(), CarsDetail.class);
	}

	private Map<String, Object> getAttrValueMap() {
		String[] attrs = cpRowSet.getMetaData().getColumnNames();
		Map<String, Object> attrValueMap = new HashMap<>();
		Arrays.asList(attrs).forEach(attr -> attrValueMap.put(attr, cpRowSet.getObject(attr)));
		
		return attrValueMap;
	}
	
	public void close() {
		scds.destroy();
	}
}
