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
	
	private SqlRowSet rowSet;
	
	private final static String SQL_QUERY = "SELECT * FROM CARS_DETAILS";
	
	public CarsReader() {
		this.scds = new SingleConnectionDataSource(ConfigParams.getUrl(), ConfigParams.getUsername(), ConfigParams.getPassword(), true);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(scds);
		this.rowSet = jdbcTemplate.queryForRowSet(SQL_QUERY);
	}
	
	public CarsDetail next() {
		ObjectMapper objMapper = new ObjectMapper();
		objMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		return objMapper.convertValue(getAttrValueMap(), CarsDetail.class);
	}
	
	private Map<String, Object> getAttrValueMap() {
		String[] attrs = rowSet.getMetaData().getColumnNames();
		Map<String, Object> attrValueMap = new HashMap<>();
		Arrays.asList(attrs).forEach(attr -> attrValueMap.put(attr, rowSet.getObject(attr)));
		
		return attrValueMap;
	}

	public boolean hasNext() {
		return rowSet.next();
	}
	
	public void close() {
		scds.destroy();
	}
}
