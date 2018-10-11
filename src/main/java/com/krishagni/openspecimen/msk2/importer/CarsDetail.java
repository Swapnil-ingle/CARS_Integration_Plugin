package com.krishagni.openspecimen.msk2.importer;

import java.util.Date;

public class CarsDetail {
	
	private String irbNumber;
	
	private String facility;
	
	private String pi;
	
	private String cycleName;
	
	private String timepointName;
	
	private String procedureName;
	
	private String specimenType;
	
	private String collectionContainer;
	
	private Date timepoint_Cr_Date;
	
	private Date timepoint_Update;
	
	private Date procedure_Cr_Date;
	
	private Date procedure_Update;

	public String getIrbNumber() {
		return irbNumber;
	}

	public void setIrbNumber(String irbNumber) {
		this.irbNumber = irbNumber;
	}

	public String getFacility() {
		return facility;
	}

	public void setFacility(String facility) {
		this.facility = facility;
	}

	public String getPi() {
		return pi;
	}

	public void setPi(String pi) {
		this.pi = pi;
	}

	public String getProcedureName() {
		return procedureName;
	}

	public void setProcedureName(String procedureName) {
		this.procedureName = procedureName;
	}

	public String getCycleName() {
		return cycleName;
	}

	public void setCycleName(String cycleName) {
		this.cycleName = cycleName;
	}

	public String getTimepointName() {
		return timepointName;
	}

	public void setTimepointName(String timepointName) {
		this.timepointName = timepointName;
	}

	public String getSpecimenType() {
		return specimenType;
	}

	public void setSpecimenType(String specimenType) {
		this.specimenType = specimenType;
	}

	public String getCollectionContainer() {
		return collectionContainer;
	}

	public void setCollectionContainer(String collectionContainer) {
		this.collectionContainer = collectionContainer;
	}

	public Date getTimepoint_Cr_Date() {
		return timepoint_Cr_Date;
	}

	public void setTimepoint_Cr_Date(Date timepoint_Cr_Date) {
		this.timepoint_Cr_Date = timepoint_Cr_Date;
	}

	public Date getTimepoint_Update() {
		return timepoint_Update;
	}

	public void setTimepoint_Update(Date timepoint_Update) {
		this.timepoint_Update = timepoint_Update;
	}

	public Date getProcedure_Cr_Date() {
		return procedure_Cr_Date;
	}

	public void setProcedure_Cr_Date(Date procedure_Cr_Date) {
		this.procedure_Cr_Date = procedure_Cr_Date;
	}

	public Date getProcedure_Update() {
		return procedure_Update;
	}

	public void setProcedure_Update(Date procedure_Update) {
		this.procedure_Update = procedure_Update;
	}
}
