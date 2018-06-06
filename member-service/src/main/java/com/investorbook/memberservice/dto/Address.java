package com.investorbook.memberservice.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "member address")
public class Address {
	@ApiModelProperty(notes = "Address line1")
	private String line1;

	@ApiModelProperty(notes = "Address line2")
	private String line2;

	@ApiModelProperty(notes = "Address suburb")
	private String suburb;

	@ApiModelProperty(notes = "Address state")
	private String state;

	@ApiModelProperty(notes = "Address zip code")
	private String zipCode;

	@ApiModelProperty(notes = "country")
	private String country;

	public String getLine1() {
		return line1;
	}

	public void setLine1(String line1) {
		this.line1 = line1;
	}

	public String getLine2() {
		return line2;
	}

	public void setLine2(String line2) {
		this.line2 = line2;
	}

	public String getSuburb() {
		return suburb;
	}

	public void setSuburb(String suburb) {
		this.suburb = suburb;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

}
